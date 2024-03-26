package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.ForgerStakeLinkedList.{addNewNode, getStakeListItem, getStakeListSize, linkedListNodeRefIsNull, removeNode}
import io.horizen.account.state.ForgerStakeStorage.saveStorageVersion
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
  def getPagedListOfForgersStakes(view: BaseAccountStateView, startPos: Int, pageSize: Int): (Int, Seq[AccountForgingStakeInfo])

  def addForgerStake(view: BaseAccountStateView, stakeId: Array[Byte],
                     blockSignProposition: PublicKey25519Proposition,
                     vrfPublicKey: VrfPublicKey,
                     ownerPublicKey: Address,
                     stakedAmount: BigInteger): Unit

  def findForgerStakeStorageElem(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeStorageElem]

  def removeForgerStake(view: BaseAccountStateView, stakeId: Array[Byte], stake: ForgerStakeStorageElem): Unit

  def isForgerStakeAvailable(view: BaseAccountStateView): Boolean

  def setupStorage(view: BaseAccountStateView): Unit
}

/*
In Forger Stake Storage model version 1, stake objects are saved in the statedb in a sort of map, where stake_id is the
key and the value is the serialization of the object ForgerStakeData. Since it is not possible to iterate over a map in
the statedb, in order to be able iterate over all the stakes and to retrieve them, the stakes are inserted in a linked
list data structure. Each node of the linked list contains 3 fields: the key for the stake (stake_id) and the keys to the
2 contiguous nodes, previous and next. The key to the last inserted node in the linked list (Tip) is saved in the
state db. The node pointed to by the tip will have next field equal to null and previous field equal to the old tip.
The key for retrieving each node is calculated from the stake_id of the corresponding stake.
When a new stake is created, a new node is inserted in the linked list and the tip will be updated to point to it.
Its "previous" field will point to the old tip and the old tip "next" field will point to the new tip.
When a stake is deleted, the key to the corresponding node is calculated from the stake_id. The previous node will be
updated in order to point to node after the deleted one and vice-versa.
For retrieving the list of all the stakes, the list of stake_ids is retrieved iterating over the linked-list, starting
from the node pointed to by the tip and going backward.
 */
object ForgerStakeStorageV1 extends ForgerStakeStorage {
  val LinkedListTipKey: Array[Byte] = Blake2b256.hash("Tip")
  val LinkedListNullValue: Array[Byte] = Blake2b256.hash("Null")

  override def setupStorage(view: BaseAccountStateView): Unit = {
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

  override def isForgerStakeAvailable(view: BaseAccountStateView): Boolean = {
    getStakeListSize(view) <= 5000
  }

  override def getPagedListOfForgersStakes(view: BaseAccountStateView, startPos: Int, pageSize: Int): (Int, Seq[AccountForgingStakeInfo]) =
    throw new IllegalArgumentException(s"Method not supported before fork point 1_3")

}

/*
In Forger Stake Storage model version 2, stake objects are saved in the statedb in a sort of map, where stake_id is the
key and the value is the serialization of the object ForgerStakeStorageElemV2. For keeping track of all the stakes, an
array-like data structure is created containing all the stake_ids. The array is realized using a set of key-value
storage elements, where the keys are calculated from the hash of the concatenation of a fixed string and the index of
the element in the array. The value contains the stake_id of the corresponding stake. An additional array element is
used for keeping the number of element in the array. When a new stake is created, a new element is added at the end of
the array and the array length is increased by 1. The array index where the new element is inserted is then saved as a
field of ForgerStakeStorageElemV2 object in the stake map. When a stake is deleted, the ForgerStakeStorageElemV2 object
is retrieved using the stake_id as a key and deleted. In the array, instead of deleting the corresponding element and
shifting one position the remaining ones, the last element in the array is moved to the position that was occupied by the
deleted stake. For retrieving the list of all the stakes, the list of all stake_ids is retrieved from the array and then
the stake_ids are used as keys for retrieving the Forger Stake objects.
For speeding up the search of the stakes belonging to a specific owner, an array with the stake_ids for each owner is also
saved.
The array structure is described in https://medium.com/robhitchens/solidity-crud-part-1-824ffa69509a.

 */
object ForgerStakeStorageV2 extends ForgerStakeStorage {

  val forgerStakeArray: StateDbArray = new StateDbArray(FORGER_STAKE_SMART_CONTRACT_ADDRESS, "ForgerStakeList".getBytes("UTF-8"))

  override def setupStorage(view: BaseAccountStateView): Unit = {
    saveStorageVersion(view, ForgerStakeStorageVersion.VERSION_2)
  }


  override def getPagedListOfForgersStakes(view: BaseAccountStateView, startPos: Int, pageSize: Int): (Int, Seq[AccountForgingStakeInfo]) = {

    val stakeListSize = forgerStakeArray.getSize(view)
    if (startPos < 0)
      throw new IllegalArgumentException(s"Invalid startPos input: $startPos can not be negative")
    if (startPos > stakeListSize-1)
      throw new IllegalArgumentException(s"Invalid position where to start reading forger stakes: $startPos, stakes array size: $stakeListSize")
    if (pageSize <= 0)
      throw new IllegalArgumentException(s"Invalid page size $pageSize, must be positive")

    var endPos = startPos + pageSize
    if (endPos > stakeListSize)
      endPos = stakeListSize

    val stakeList = (startPos until endPos).map(index => {
      val currentStakeId = forgerStakeArray.getValue(view, index)
      val stakeData = ForgerStakeDataSerializer.parseBytes(view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, currentStakeId))
      AccountForgingStakeInfo(
        currentStakeId,
        stakeData
      )
    })

    if (endPos == stakeListSize) {
      // tell the caller we are done with the array
      endPos = -1
    }

    (endPos, stakeList)
  }


  override def addForgerStake(view: BaseAccountStateView,
                              stakeId: Array[Byte],
                              blockSignProposition: PublicKey25519Proposition,
                              vrfPublicKey: VrfPublicKey,
                              ownerPublicKey: Address,
                              stakedAmount: BigInteger): Unit = {


    val forgerListIndex: Int = forgerStakeArray.append(view, stakeId)

    val ownerAddressProposition = new AddressProposition(ownerPublicKey)
    val ownerInfo = OwnerStakeInfo(ownerAddressProposition)
    val ownerListIndex: Int = ownerInfo.append(view, stakeId)

    ownerInfo.addOwnerStake(view, stakedAmount)

    val forgerStakeData = ForgerStakeStorageElemV2(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), ownerAddressProposition, stakedAmount, forgerListIndex, ownerListIndex)

    // store the forger stake data
    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId,
      ForgerStakeStorageElemV2Serializer.toBytes(forgerStakeData))
  }

  def getOwnerStake(view: BaseAccountStateView, owner: AddressProposition): BigInteger = {
    val ownerInfo = OwnerStakeInfo(owner)
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
    if (!stakeIdToMove.sameElements(stakeId))
      updateStake(view, stakeIdToMove, stake => {stake.stakeListIndex = stakeListIndex; stake})

    val ownerStakeInfo = OwnerStakeInfo(stakeToRemove.ownerPublicKey)
    val ownerStakeListIndex = stakeToRemove.ownerListIndex
    val stakeIdToMoveFromOwnerList = ownerStakeInfo.removeAndRearrange(view, ownerStakeListIndex)
    if (!stakeIdToMoveFromOwnerList.sameElements(stakeId))
      updateStake(view, stakeIdToMoveFromOwnerList, stake => {stake.ownerListIndex = ownerStakeListIndex; stake})

    ownerStakeInfo.subOwnerStake(view, stake.stakedAmount)

    // remove the stake
    view.removeAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId)
  }

  private def updateStake(view: BaseAccountStateView, stakeId: Array[Byte], fun: ForgerStakeStorageElemV2 => ForgerStakeStorageElemV2): Unit = {
    val stake = findForgerStakeStorageElem(view, stakeId).get.asInstanceOf[ForgerStakeStorageElemV2]
    val updatedStake = fun(stake)
    view.updateAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, stakeId,
      ForgerStakeStorageElemV2Serializer.toBytes(updatedStake))
  }

  override def getListOfForgersStakes(view: BaseAccountStateView): Seq[AccountForgingStakeInfo] = {
    getListOfForgersStakesFromList(view, forgerStakeArray)
  }

  def getPagedListOfForgersStakesOfUser(view: BaseAccountStateView, owner: AddressProposition, startPos: Int, size: Int): (Int, Seq[AccountForgingStakeInfo]) = {
    val ownerStakeIdArray = OwnerStakeInfo(owner)
    getPagedListOfForgersStakesFromList(view, ownerStakeIdArray, startPos, size)
  }


  def getPagedListOfForgersStakesFromList(view: BaseAccountStateView, stakeArray: StateDbArray, startPos: Int, pageSize: Int): (Int, Seq[AccountForgingStakeInfo]) = {

    val stakeListSize = stakeArray.getSize(view)
    if (startPos < 0)
      throw new IllegalArgumentException(s"Invalid startPos input: $startPos, can not be negative")
    if (pageSize <= 0) {
      throw new IllegalArgumentException(s"Invalid page size $pageSize, must be positive")
    }

    if (startPos == 0 && stakeListSize == 0)
      return (-1, Seq.empty[AccountForgingStakeInfo])

    if (startPos > stakeListSize-1)
      throw new IllegalArgumentException(s"Invalid position where to start reading forger stakes: $startPos, stakes array size: $stakeListSize")

    var endPos = startPos + pageSize
    if (endPos > stakeListSize)
      endPos = stakeListSize

    val stakeList = (startPos until endPos).map(index => {
      val currentStakeId = stakeArray.getValue(view, index)
      val stakeData = ForgerStakeDataSerializer.parseBytes(view.getAccountStorageBytes(FORGER_STAKE_SMART_CONTRACT_ADDRESS, currentStakeId))
      AccountForgingStakeInfo(
        currentStakeId,
        stakeData
      )
    })

    if (endPos == stakeListSize) {
      // tell the caller we are done with the array
      endPos = -1
    }

    (endPos, stakeList)
  }

  private def getListOfForgersStakesFromList(view: BaseAccountStateView, stakeArray: StateDbArray): Seq[AccountForgingStakeInfo] = {
    val numOfForgerStakes = stakeArray.getSize(view)
    val stakeList = (0 until numOfForgerStakes).map(index => {
      val currentStakeId = stakeArray.getValue(view, index)
      val stakeData = findStakeData(view, currentStakeId).get
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

  override def isForgerStakeAvailable(view: BaseAccountStateView): Boolean = {
    forgerStakeArray.getSize(view)  <= 5000
  }
}


object ForgerStakeStorage {

  val ForgerStakeVersionKey: Array[Byte] = Blake2b256.hash("ForgerStakeVersion")
  val DisabledKey: Array[Byte] = Blake2b256.hash("Disabled")

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

  def isDisabled(view: BaseAccountStateView): Boolean = {
    val disabled = view.getAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, DisabledKey)
    new BigInteger(1, disabled) == BigInteger.ONE
  }

  def setDisabled(view: BaseAccountStateView): Unit  = {
    val disabled = BigIntegerUtil.toUint256Bytes(BigInteger.ONE)
    view.updateAccountStorage(FORGER_STAKE_SMART_CONTRACT_ADDRESS, DisabledKey, disabled)
  }



}


object ForgerStakeStorageVersion extends Enumeration {
  type ForgerStakeStorageVersion = Value
  val VERSION_1, VERSION_2 = Value
}

case class OwnerStakeInfo(owner: AddressProposition)
  extends StateDbArray(FORGER_STAKE_SMART_CONTRACT_ADDRESS, owner.pubKeyBytes()) {

  val OwnerTotalStakeKey: Array[Byte] = calculateKey(Bytes.concat("Stake".getBytes("UTF-8"), keySeed))

  def addOwnerStake(view: BaseAccountStateView, stakeAmount: BigInteger): Unit = {
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


