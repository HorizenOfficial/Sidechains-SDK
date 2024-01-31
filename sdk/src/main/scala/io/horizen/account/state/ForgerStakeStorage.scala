package io.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.ForgerStakeLinkedList.{addNewNode, getStakeListItem, linkedListNodeRefIsNull, removeNode}
import io.horizen.account.state.ForgerStakeStorageVersion.ForgerStakeStorageVersion
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state.WithdrawalMsgProcessor.calculateKey
import io.horizen.account.utils.BigIntegerUtil
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import sparkz.crypto.hash.Blake2b256

import java.math.BigInteger
import scala.util.{Failure, Success}

trait ForgerStakeStorage {

  def existsStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Boolean = {
    // do the RAW-strategy read even if the record is actually multi-line in stateDb. It will save some gas.
    val data = view.getAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId)
    // getting a not existing key from state DB using RAW strategy
    // gives an array of 32 bytes filled with 0, while using CHUNK strategy
    // gives an empty array instead
    !data.sameElements(NULL_HEX_STRING_32)
  }

  def findStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeData] = {
    val data = view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      ForgerStakeDataSerializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger data.", exception)
      }
    }
  }

  def getListOfForgersStakes(view: BaseAccountStateView): Seq[AccountForgingStakeInfo]

  def addForgerStake(view: BaseAccountStateView, stakeId: Array[Byte],
                     blockSignProposition: PublicKey25519Proposition,
                     vrfPublicKey: VrfPublicKey,
                     ownerPublicKey: Address,
                     stakedAmount: BigInteger): Unit

  def findForgerStakeStorageElem(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeStorageElem]

  def removeForgerStake(view: BaseAccountStateView, stakeId: Array[Byte], stake: ForgerStakeStorageElem): Unit


}

object ForgerStakeStorageV1 extends ForgerStakeStorage {
  val LinkedListTipKey: Array[Byte] = Blake2b256.hash("Tip")
  val LinkedListNullValue: Array[Byte] = Blake2b256.hash("Null")

  def setupStorage(view: BaseAccountStateView): Unit = {
    // set the initial value for the linked list last element (null hash)

    // check we do not have this key set to any value yet
    val initialTip = view.getAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, LinkedListTipKey)

    // getting a not existing key from state DB using RAW strategy as the api is doing
    // gives 32 bytes filled with 0 (CHUNK strategy gives an empty array instead)
    if (!initialTip.sameElements(NULL_HEX_STRING_32))
      throw new MessageProcessorInitializationException("initial tip already set")

    view.updateAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, LinkedListTipKey, LinkedListNullValue)

  }

  override def getListOfForgersStakes(view: BaseAccountStateView): Seq[AccountForgingStakeInfo] = {
    var stakeList = Seq[AccountForgingStakeInfo]()
    var nodeReference = view.getAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, LinkedListTipKey)

    while (!linkedListNodeRefIsNull(nodeReference)) {
      val (item: AccountForgingStakeInfo, prevNodeReference: Array[Byte]) = getStakeListItem(view, nodeReference)
      stakeList = item +: stakeList
      nodeReference = prevNodeReference
    }
    stakeList
  }

  override def addForgerStake(view: BaseAccountStateView, stakeId: Array[Byte],
                     blockSignProposition: PublicKey25519Proposition,
                     vrfPublicKey: VrfPublicKey,
                     ownerPublicKey: Address,
                     stakedAmount: BigInteger): Unit = {

    // add a new node to the linked list pointing to this forger stake data
    addNewNode(view, stakeId, FORGER_STAKE_SMART_CONTRACT_ADDRESS)

    val forgerStakeData = ForgerStakeData(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), new AddressProposition(ownerPublicKey), stakedAmount)

    // store the forger stake data
    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId,
      ForgerStakeDataSerializer.toBytes(forgerStakeData))
  }

  override def findForgerStakeStorageElem(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeStorageElem] = {
    findStakeData(view, stakeId)
  }

  override def removeForgerStake(view: BaseAccountStateView, stakeId: Array[Byte], stake: ForgerStakeStorageElem): Unit = {
    // remove the data from the linked list
    removeNode(view, stakeId, FORGER_STAKE_SMART_CONTRACT_ADDRESS)

    // remove the stake
    view.removeAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId)
  }

}

object ForgerStakeStorageV2 extends ForgerStakeStorage {

  val forgerStakeArray: StateDbArray = new StateDbArray(FORGER_STAKE_SMART_CONTRACT_ADDRESS, "ForgerStakeList".getBytes("UTF-8"))


  override def getListOfForgersStakes(view: BaseAccountStateView): Seq[AccountForgingStakeInfo] = {
    val numOfForgerStakes = forgerStakeArray.getSize(view)
    val stakeList = (0 until numOfForgerStakes).map(index => {
      val currentStakeId = forgerStakeArray.getValue(view, index)
      val stakeData = ForgerStakeDataSerializer.parseBytes(view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, currentStakeId))
      AccountForgingStakeInfo(
        currentStakeId,
        stakeData
      )
    })
    stakeList
  }

  override def addForgerStake(view: BaseAccountStateView, stakeId: Array[Byte],
                       blockSignProposition: PublicKey25519Proposition,
                       vrfPublicKey: VrfPublicKey,
                       ownerPublicKey: Address,
                       stakedAmount: BigInteger): Unit = {

    val forgerListIndex: Int = forgerStakeArray.append(view, stakeId)

    val ownerAddressProposition = new AddressProposition(ownerPublicKey)
    val ownerInfo = new OwnerStakeInfo(ownerAddressProposition)
    val ownerListIndex: Int = ownerInfo.append(view, stakeId)

    ownerInfo.addToTotalStake(view, stakedAmount)

    val forgerStakeData = ForgerStakeStorageElemV2(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), ownerAddressProposition, stakedAmount, forgerListIndex, ownerListIndex)

    // store the forger stake data
    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId,
      ForgerStakeStorageElemV2Serializer.toBytes(forgerStakeData))
  }

  def getOwnerStake(view: BaseAccountStateView, owner: AddressProposition): BigInteger = {
    val ownerInfo = new OwnerStakeInfo(owner)
    ownerInfo.getOwnerStake(view)
  }

  override def findForgerStakeStorageElem(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeStorageElem] = {
    val data = view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      ForgerStakeStorageElemV2Serializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger data.", exception)
      }
    }
  }

  override def removeForgerStake(view: BaseAccountStateView, stakeId: Array[Byte], stake: ForgerStakeStorageElem): Unit = {
    val stakeToRemove = stake.asInstanceOf[ForgerStakeStorageElemV2]

    val stakeListIndex = stakeToRemove.stakeListIndex
    val stakeIdToMove = forgerStakeArray.removeAndRearrange(view, stakeListIndex)

    val stakeToMoveData = view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeIdToMove)
    val stakeToMove = ForgerStakeStorageElemV2Serializer.parseBytesTry(stakeToMoveData).get
    stakeToMove.stakeListIndex = stakeListIndex

    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeIdToMove,
      ForgerStakeStorageElemV2Serializer.toBytes(stakeToMove))

    val ownerStakeInfo = new OwnerStakeInfo(stakeToRemove.ownerPublicKey)
    val ownerStakeListIndex = stakeToRemove.ownerListIndex
    val stakeIdToMoveFromOwnerList = ownerStakeInfo.removeAndRearrange(view, ownerStakeListIndex)

    val stakeToMoveDataOwner = view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeIdToMoveFromOwnerList)
    val stakeToMoveOwner = ForgerStakeStorageElemV2Serializer.parseBytesTry(stakeToMoveDataOwner).get
    stakeToMoveOwner.ownerListIndex = stakeListIndex//TODO this is a bug that I will remove only after creating a test for it

    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeIdToMoveFromOwnerList,
      ForgerStakeStorageElemV2Serializer.toBytes(stakeToMoveOwner))

    ownerStakeInfo.subOwnerStake(view, stake.stakedAmount)

    // remove the stake
    view.removeAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId)
  }

  def getListOfForgersStakesOfUser(view: BaseAccountStateView, owner: AddressProposition): Seq[AccountForgingStakeInfo] = {
    val ownerStakeIdArray = new OwnerStakeInfo(owner)
    val numOfForgerStakes = ownerStakeIdArray.getSize(view)
    val stakeList = (0 until numOfForgerStakes).map(index => {
      val currentStakeId = ownerStakeIdArray.getValue(view, index)
      val stakeData = ForgerStakeStorageV1.findStakeData(view, currentStakeId).get
      AccountForgingStakeInfo(
        currentStakeId,
        ForgerStakeData(
          ForgerPublicKeys(
            stakeData.forgerPublicKeys.blockSignPublicKey, stakeData.forgerPublicKeys.vrfPublicKey),
          stakeData.ownerPublicKey, stakeData.stakedAmount)
      )
    })
    stakeList
  }

}


object ForgerStakeStorage {

  val ForgerStakeVersionKey: Array[Byte] = Blake2b256.hash("ForgerStakeVersion")

  def apply(storageVersion: ForgerStakeStorageVersion): ForgerStakeStorage = {
    storageVersion match {
      case ForgerStakeStorageVersion.VERSION_1 => ForgerStakeStorageV1
      case ForgerStakeStorageVersion.VERSION_2 => ForgerStakeStorageV2
      case _ => throw new ExecutionRevertedException(s"Unknown Forger Stake storage version")
    }
  }


  def getStorageVersionFromDb(view: BaseAccountStateView): ForgerStakeStorageVersion.ForgerStakeStorageVersion = {
    val version = view.getAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, ForgerStakeVersionKey)
    ForgerStakeStorageVersion(new BigInteger(1, version).intValueExact()) match {
      case ForgerStakeStorageVersion.VERSION_2 => ForgerStakeStorageVersion.VERSION_2
      case _ => ForgerStakeStorageVersion.VERSION_1
    }
  }

  def saveStorageVersion(view: BaseAccountStateView, version: ForgerStakeStorageVersion.ForgerStakeStorageVersion): Array[Byte]  = {
    val ver = BigIntegerUtil.toUint256Bytes(BigInteger.valueOf(version.id))
    view.updateAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, ForgerStakeVersionKey, ver)
    ver
  }
}


object ForgerStakeStorageVersion extends Enumeration {
  type ForgerStakeStorageVersion = Value
  val VERSION_1, VERSION_2 = Value
}

class OwnerStakeInfo(owner: AddressProposition)
  extends StateDbArray(FORGER_STAKE_SMART_CONTRACT_ADDRESS, owner.pubKeyBytes()) {

  val OwnerTotalStakeKey: Array[Byte] = calculateKey(Bytes.concat("Stake".getBytes("UTF-8"), keySeed))

  def addToTotalStake(view: BaseAccountStateView, stakeAmount: BigInteger): Unit = {
    val currentAmount = new BigInteger(1, view.getAccountStorage(account, OwnerTotalStakeKey))
    view.updateAccountStorage(account, OwnerTotalStakeKey, BigIntegerUtil.toUint256Bytes(currentAmount.add(stakeAmount)))
  }

  def subOwnerStake(view: BaseAccountStateView, stakeAmount: BigInteger): Unit = {
    val currentAmount = new BigInteger(1, view.getAccountStorage(account, OwnerTotalStakeKey))
    view.updateAccountStorage(account, OwnerTotalStakeKey, BigIntegerUtil.toUint256Bytes(currentAmount.subtract(stakeAmount)))
  }

  def getOwnerStake(view: BaseAccountStateView): BigInteger = {
    val currentAmount = new BigInteger(1, view.getAccountStorage(account, OwnerTotalStakeKey))
    currentAmount
  }

}


class StateDbArray(val account: Address, val keySeed: Array[Byte]) {
  protected val baseArrayKey: Array[Byte] = Blake2b256.hash(keySeed)

  def getSize(view: BaseAccountStateView): Int = {
    val size = new BigInteger(1, view.getAccountStorage(account, baseArrayKey)).intValueExact()
    size
  }

  def append(view: BaseAccountStateView, value: Array[Byte]): Int = {
    val numOfElem: Int = getSize(view)
    val key = getElemKey(numOfElem)
    view.updateAccountStorage(account, key, value)
    updateSize(view, numOfElem + 1)
    numOfElem
  }

  private def updateSize(view: BaseAccountStateView, newSize: Int): Unit = {
    val paddedSize = BigIntegerUtil.toUint256Bytes(BigInteger.valueOf(newSize))
    view.updateAccountStorage(account, baseArrayKey, paddedSize)
  }

  private def removeLast(view: BaseAccountStateView): Array[Byte] = {
    val size: Int = getSize(view)
    val lastElemIndex: Int = size - 1
    val key = getElemKey(lastElemIndex)
    val value = view.getAccountStorage(account, key)
    updateSize(view, size - 1)
    view.removeAccountStorage(account, key)
    value
  }

  def removeAndRearrange(view: BaseAccountStateView, index: Int): Array[Byte] = {
    // Remove last elem from the array and put its value to the position left empty, so there aren't gaps in the array
    val lastElemValue = removeLast(view)
    updateValue(view, index, lastElemValue)
    lastElemValue
  }

  def updateValue(view: BaseAccountStateView, index: Int, newValue: Array[Byte]): Unit = {
    val key = getElemKey(index)
    view.updateAccountStorage(account, key, newValue)
  }

  def getValue(view: BaseAccountStateView, index: Int): Array[Byte] = {
    val key = getElemKey(index)
    val value = view.getAccountStorage(account, key)
    value
  }

  private def getElemKey(index: Int): Array[Byte] = {
    calculateKey(Bytes.concat(baseArrayKey, Ints.toByteArray(index)))
  }

}