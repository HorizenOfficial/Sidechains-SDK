package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import io.horizen.account.state.ForgerMap.{forgerList, getSize}
import io.horizen.account.state.ForgerStakeStorageV3.ACCOUNT
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.utils.BigIntegerUInt256
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.utils.BytesUtils
import io.horizen.utxo.utils.BlockFeeInfo
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.crypto.hash.Blake2b256
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger
import scala.collection.mutable.ListBuffer


object ForgerStakeStorageV3 {

  val ACCOUNT = FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS

  def getForgerKey(blockSignProposition: PublicKey25519Proposition, vrfPublicKey: VrfPublicKey): Array[Byte] =
    Blake2b256.hash(Bytes.concat(blockSignProposition.pubKeyBytes(), vrfPublicKey.pubKeyBytes()))

  def getDelegatorKey(delegatorPublicKey: Address): Array[Byte] =
    Blake2b256.hash(new AddressProposition(delegatorPublicKey).pubKeyBytes())

  def getStakeKey(forgerKey: Array[Byte], delegatorPublicKey: Address): Array[Byte] = {
    val delegatorKey = getDelegatorKey(delegatorPublicKey)
    Blake2b256.hash(Bytes.concat(forgerKey, delegatorKey))
  }

  def addForger(view: BaseAccountStateView,
                blockSignProposition: PublicKey25519Proposition,
                vrfPublicKey: VrfPublicKey,
                rewardShare: Int,
                rewardAddress: Address,
                epochNumber: Int,
                delegatorPublicKey: Address,
                stakedAmount: BigInteger): Unit = {

    val forgerKey = getForgerKey(blockSignProposition, vrfPublicKey)

    if (ForgerMap.existsForger(view, forgerKey))
      throw new ExecutionRevertedException(s"Forger already registered.")

    ForgerMap.addForger(view, forgerKey, blockSignProposition, vrfPublicKey, rewardShare, rewardAddress)

    val forgerHistory = StakeHistory(forgerKey)
    forgerHistory.addCheckpoint(view, epochNumber, stakedAmount)

    val stakeKey = getStakeKey(forgerKey, delegatorPublicKey)
    val stakeHistory = StakeHistory(stakeKey)
    stakeHistory.addCheckpoint(view, epochNumber, stakedAmount)

    addNewDelegator(view, forgerKey, delegatorPublicKey)

  }

  def updateForger(view: AccountStateView, blockSignProposition: PublicKey25519Proposition, vrfPublicKey: VrfPublicKey, rewardShare: Int, rewardAddress: Address): Unit = {
    val forgerKey = getForgerKey(blockSignProposition, vrfPublicKey)
    val forger = ForgerMap.getForger(view, forgerKey).getOrElse(throw new ExecutionRevertedException("Forger doesn't exist."))
    if ((forger.rewardShare > 0) || (forger.rewardAddress.address() != Address.ZERO))
      throw new ExecutionRevertedException("Forger has already set reward share and reward address.")
    ForgerMap.updateForger(view, forgerKey, blockSignProposition, vrfPublicKey, rewardShare, rewardAddress)
  }


  def addNewDelegator(view: BaseAccountStateView, forgerKey: Array[Byte], delegator: Address): Unit = {
    val delegatorAddressProposition = new AddressProposition(delegator)
    val listOfDelegators = DelegatorList(forgerKey)
    listOfDelegators.addDelegator(view, delegatorAddressProposition)

    val listOfForgers = ForgerList(delegatorAddressProposition)
    listOfForgers.addForger(view, forgerKey)

  }


  def getPagedListOfForgers(view: BaseAccountStateView, startPos: Int, pageSize: Int): (Int, Seq[ForgerInfoV3]) =
    ForgerMap.getPagedListOfForgers(view, startPos, pageSize)

  def getForger(view: BaseAccountStateView, signKey: PublicKey25519Proposition, vrfKey: VrfPublicKey): Option[ForgerInfoV3] = {
    val forgerKey = getForgerKey(signKey, vrfKey)
    ForgerMap.getForger(view, forgerKey)
  }

  def addStake(view: BaseAccountStateView,
               signKey: PublicKey25519Proposition,
               vrfPublicKey: VrfPublicKey,
               epochNumber: Int,
               delegatorPublicKey: Address,
               stakedAmount: BigInteger): Unit = {

    val forgerKey = getForgerKey(signKey, vrfPublicKey)
    val forgerHistory = StakeHistory(forgerKey)
    val forgerHistorySize =  forgerHistory.getSize(view)
    if (forgerHistorySize == 0)
      throw new ExecutionRevertedException(s"Forger doesn't exist.")

    forgerHistory.updateOrAddCheckpoint(view, forgerHistorySize, epochNumber, latestStake => latestStake.add(stakedAmount))

    val stakeKey = getStakeKey(forgerKey, delegatorPublicKey)
    val stakeHistory = StakeHistory(stakeKey)
    val stakeHistorySize = stakeHistory.getSize(view)
    stakeHistory.updateOrAddCheckpoint(view, stakeHistorySize, epochNumber, latestStake => latestStake.add(stakedAmount))
    if (stakeHistorySize == 0)
      addNewDelegator(view, forgerKey, delegatorPublicKey)
  }

  def removeStake(view: BaseAccountStateView,
                  blockSignProposition: PublicKey25519Proposition,
                  vrfPublicKey: VrfPublicKey,
                  epochNumber: Int,
                  delegatorPublicKey: Address,
                  stakedAmount: BigInteger): Unit = {


    val forgerKey = getForgerKey(blockSignProposition, vrfPublicKey)
    val forgerHistory = StakeHistory(forgerKey)
    val forgerHistorySize = forgerHistory.getSize(view)
    if (forgerHistorySize == 0)
      throw new ExecutionRevertedException("Forger doesn't exist.")

    val stakeKey = getStakeKey(forgerKey, delegatorPublicKey)
    val stakeHistory = StakeHistory(stakeKey)
    val stakeHistorySize = stakeHistory.getSize(view)
    if (stakeHistorySize == 0)
      throw new ExecutionRevertedException(s"Delegator ${BytesUtils.toHexString(delegatorPublicKey.toBytes)} doesn't have stake with the forger.")


    def subtractStake(latestStake: BigInteger): BigInteger = {
      val newAmount = latestStake.subtract(stakedAmount)
      if (newAmount.signum() == -1)
        throw new ExecutionRevertedException(s"Not enough stake. Claimed $stakedAmount, available $latestStake")
      newAmount
    }

    stakeHistory.updateOrAddCheckpoint(view, stakeHistorySize, epochNumber, subtractStake)
    forgerHistory.updateOrAddCheckpoint(view, forgerHistorySize, epochNumber, subtractStake)
  }

  def getAllForgerStakes(view: BaseAccountStateView): Seq[(PublicKey25519Proposition, VrfPublicKey, Address, BigInteger)] = {
    val listOfForgerKeys = ForgerMap.getKeys(view)
    listOfForgerKeys.flatMap { forgerKey =>
      val forger = ForgerMap(view, forgerKey)
      val delegatorList = DelegatorList(forgerKey)
      val delegatorSize = delegatorList.getSize(view)
      val listOfStakes: ListBuffer[(PublicKey25519Proposition, VrfPublicKey, Address, BigInteger)] = ListBuffer()
      for (idx <- 0 until delegatorSize) {
        val delegator = delegatorList.getDelegatorAt(view, idx).address()
        val stakeKey = getStakeKey(forgerKey, delegator)
        val stakeHistory = StakeHistory(stakeKey)
        val amount = stakeHistory.getLatestAmount(view)
        if (amount.signum() == 1)
          listOfStakes.append((forger.forgerPublicKeys.blockSignPublicKey, forger.forgerPublicKeys.vrfPublicKey, delegator, amount))
      }
      listOfStakes
    }


  }


  case class StakeHistory(uid: Array[Byte])
    extends StateDbArray(ACCOUNT, Blake2b256.hash(Bytes.concat(uid, "History".getBytes("UTF-8")))) {

    def addCheckpoint(view: BaseAccountStateView, epoch: Int, stakeAmount: BigInteger): Unit = {
      val checkpoint = StakeCheckpoint(epoch, stakeAmount)
      append(view, checkpointToPaddedBytes(checkpoint))
    }

    def updateOrAddCheckpoint(view: BaseAccountStateView, historySize: Int, epoch: Int, op: BigInteger => BigInteger): Unit = {
      if (historySize > 0) {
        val lastElemIndex = historySize - 1
        val lastCheckpoint = getCheckpoint(view, lastElemIndex)
        val newAmount = op(lastCheckpoint.stakedAmount)
        if (lastCheckpoint.fromEpochNumber == epoch) {
          val checkpoint = StakeCheckpoint(epoch, newAmount)
          updateValue(view, lastElemIndex, checkpointToPaddedBytes(checkpoint))
        }
        else
          addCheckpoint(view, epoch, newAmount)
      }
      else if (historySize == 0) {
        val newAmount = op(BigInteger.ZERO)
        addCheckpoint(view, epoch, newAmount)
      }
      else
        throw new IllegalArgumentException(s"Size cannot be negative: $historySize")
    }


    def getCheckpoint(view: BaseAccountStateView, index: Int): StakeCheckpoint = {
      val paddedValue = getValue(view, index)
      StakeCheckpointSerializer.parseBytes(paddedValue)
    }

    def getLatestAmount(view: BaseAccountStateView): BigInteger = {
      val size = getSize(view)
      if (size == 0)
        BigInteger.ZERO
      else {
        val paddedValue = getValue(view, size - 1)
        StakeCheckpointSerializer.parseBytes(paddedValue).stakedAmount
      }
    }


    private[horizen] def checkpointToPaddedBytes(checkpoint: StakeCheckpoint): Array[Byte] = {
      BytesUtils.padRightWithZeroBytes(StakeCheckpointSerializer.toBytes(checkpoint), 32)
    }
  }

  case class DelegatorList(fuid: Array[Byte])
    extends StateDbArray(ACCOUNT, Blake2b256.hash(Bytes.concat(fuid, "Delegators".getBytes("UTF-8"))))  {

    def addDelegator(view: BaseAccountStateView, delegator: AddressProposition): Unit = {
      append(view, BytesUtils.padRightWithZeroBytes(AddressPropositionSerializer.getSerializer.toBytes(delegator), 32))
    }

    def getDelegatorAt(view: BaseAccountStateView, index: Int): AddressProposition = {
      AddressPropositionSerializer.getSerializer.parseBytes(getValue(view, index))
    }

  }

  case class ForgerList(delegator: AddressProposition)
    extends StateDbArray(ACCOUNT, Blake2b256.hash(Bytes.concat(delegator.pubKeyBytes(), "Forgers".getBytes("UTF-8"))))  {

    def addForger(view: BaseAccountStateView, fuid: Array[Byte]): Unit = {
      append(view, fuid)
    }

  }

}

case class ForgerInfoV3(
                         forgerPublicKeys: ForgerPublicKeys,
                         rewardShare: Int,
                         rewardAddress: AddressProposition)
  extends BytesSerializable {

  override type M = ForgerInfoV3

  override def serializer: SparkzSerializer[ForgerInfoV3] = ForgerInfoV3Serializer

  override def toString: String = "%s(forgerPubKeys: %s, rewardShare: %s, rewardAddress: %s)"
    .format(this.getClass.toString, forgerPublicKeys, rewardShare, rewardAddress)
}

object ForgerInfoV3Serializer extends SparkzSerializer[ForgerInfoV3] {

  override def serialize(s: ForgerInfoV3, w: Writer): Unit = {
    ForgerPublicKeysSerializer.serialize(s.forgerPublicKeys, w)
    w.putInt(s.rewardShare)
    AddressPropositionSerializer.getSerializer.serialize(s.rewardAddress, w)
  }

  override def parse(r: Reader): ForgerInfoV3 = {
    val forgerPublicKeys = ForgerPublicKeysSerializer.parse(r)
    val rewardShare = r.getInt()
    val rewardAddress = AddressPropositionSerializer.getSerializer.parse(r)
    ForgerInfoV3(forgerPublicKeys, rewardShare, rewardAddress)
  }
}

case class StakeCheckpoint(
                         fromEpochNumber: Int,
                         stakedAmount: BigInteger)
  extends BytesSerializable {

  require(fromEpochNumber >= 0, s"Epoch cannot be a negative number. Passed value: $fromEpochNumber")
  require(stakedAmount.signum() != -1, s"Stake cannot be a negative number. Passed value: $stakedAmount")

  override type M = StakeCheckpoint

  override def serializer: SparkzSerializer[StakeCheckpoint] = StakeCheckpointSerializer

  override def toString: String = "%s(fromEpochNumber: %s, stakedAmount: %s)"
    .format(this.getClass.toString, fromEpochNumber, stakedAmount)

}

object StakeCheckpointSerializer extends SparkzSerializer[StakeCheckpoint] {

  override def serialize(s: StakeCheckpoint, w: Writer): Unit = {
    w.putInt(s.fromEpochNumber)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
  }

  override def parse(r: Reader): StakeCheckpoint = {
    val fromEpochNumber = r.getInt()
    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigIntegerUInt256(r.getBytes(stakeAmountLength)).getBigInt
    StakeCheckpoint(fromEpochNumber, stakeAmount)
  }
}

object ForgerMap {
  val forgerList: StateDbArray = new StateDbArray(ACCOUNT, "Forgers".getBytes("UTF-8"))

  def existsForger(view: BaseAccountStateView, forgerKey: Array[Byte]): Boolean = {
    val forgerData = view.getAccountStorage(ACCOUNT, forgerKey)
    // getting a not existing key from state DB using RAW strategy
    // gives an array of 32 bytes filled with 0, while using CHUNK strategy
    // gives an empty array instead
    !forgerData.sameElements(NULL_HEX_STRING_32)
  }

  def addForger(view: BaseAccountStateView,
                forgerKey: Array[Byte],
                blockSignProposition: PublicKey25519Proposition,
                vrfPublicKey: VrfPublicKey,
                rewardShare: Int,
                rewardAddress: Address,
               ): Unit = {
    forgerList.append(view, forgerKey)

    val forgerStakeData = ForgerInfoV3(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), rewardShare, new AddressProposition(rewardAddress))

    // store the forger stake data
    view.updateAccountStorageBytes(ACCOUNT, forgerKey,
      ForgerInfoV3Serializer.toBytes(forgerStakeData))
  }

  def updateForger(view: BaseAccountStateView,
                forgerKey: Array[Byte],
                blockSignProposition: PublicKey25519Proposition,
                vrfPublicKey: VrfPublicKey,
                rewardShare: Int,
                rewardAddress: Address,
               ): Unit = {

    val forgerStakeData = ForgerInfoV3(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), rewardShare, new AddressProposition(rewardAddress))

    // store the forger stake data
    view.updateAccountStorageBytes(ACCOUNT, forgerKey,
      ForgerInfoV3Serializer.toBytes(forgerStakeData))
  }


  def getSize(view: BaseAccountStateView): Int = forgerList.getSize(view)

  def getKeys(view: BaseAccountStateView): Seq[Array[Byte]] = {
    val listSize = getSize(view)
    (0 until listSize).map(index => {
     forgerList.getValue(view, index)
    })
  }

  def getForger(view: BaseAccountStateView, forgerKey: Array[Byte]): Option[ForgerInfoV3] = {
    val forgerData = view.getAccountStorage(ACCOUNT, forgerKey)
    if (!forgerData.sameElements(NULL_HEX_STRING_32))
      Some(ForgerInfoV3Serializer.parseBytes(view.getAccountStorageBytes(ACCOUNT, forgerKey)))
    else
      None
  }

  def apply(view: BaseAccountStateView, forgerKey: Array[Byte]): ForgerInfoV3 = {
    val forgerData = view.getAccountStorage(ACCOUNT, forgerKey)
    ForgerInfoV3Serializer.parseBytes(view.getAccountStorageBytes(ACCOUNT, forgerKey))
  }


  def getPagedListOfForgers(view: BaseAccountStateView, startPos: Int, pageSize: Int): (Int, Seq[ForgerInfoV3]) = {

    val listSize = getSize(view)
    if (startPos < 0)
      throw new IllegalArgumentException(s"Invalid startPos input: $startPos can not be negative")
    if (pageSize <= 0)
      throw new IllegalArgumentException(s"Invalid page size $pageSize, must be positive")

    if (startPos == 0 && listSize == 0)
      return (-1, Seq.empty[ForgerInfoV3])

    if (startPos > listSize-1)
      throw new IllegalArgumentException(s"Invalid start position: $startPos, array size: $listSize")

    var endPos = startPos + pageSize
    if (endPos > listSize)
      endPos = listSize

    val listOfElems = (startPos until endPos).map(index => {
      val currentKey = forgerList.getValue(view, index)
      apply(view, currentKey)
    })

    if (endPos == listSize) {
      // tell the caller we are done with the array
      endPos = -1
    }

    (endPos, listOfElems)
  }


}
