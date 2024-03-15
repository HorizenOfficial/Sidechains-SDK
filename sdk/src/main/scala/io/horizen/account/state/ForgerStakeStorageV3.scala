package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.utils.BigIntegerUInt256
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS
import io.horizen.evm.Address
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.utils.BytesUtils
import sparkz.core.serialization.{BytesSerializable, SparkzSerializer}
import sparkz.crypto.hash.Blake2b256
import sparkz.util.serialization.{Reader, Writer}

import java.math.BigInteger


object ForgerStakeStorageV3 {

  val forgerList: StateDbArray = new StateDbArray(FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS, "Forgers".getBytes("UTF-8"))

  def getForgerKey(blockSignProposition: PublicKey25519Proposition, vrfPublicKey: VrfPublicKey): Array[Byte] =
    Blake2b256.hash(Bytes.concat(blockSignProposition.pubKeyBytes(), vrfPublicKey.pubKeyBytes()))

  def getDelegatorKey(delegatorPublicKey: Address): Array[Byte] =
    Blake2b256.hash(new AddressProposition(delegatorPublicKey).pubKeyBytes())

//  def getStakeKey(blockSignProposition: PublicKey25519Proposition, vrfPublicKey: VrfPublicKey, delegatorPublicKey: Address): Array[Byte] = {
//    val forgerKey = getForgerKey(blockSignProposition, vrfPublicKey)
//    val delegatorKey = getDelegatorKey(delegatorPublicKey)
//    Blake2b256.hash(Bytes.concat(forgerKey, delegatorKey))
//  }

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

    if (existsForger(view, forgerKey))
      throw new ExecutionRevertedException(s"Forger already registered.")

    forgerList.append(view, forgerKey)

    val forgerStakeData = ForgerInfoV3(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), rewardShare, new AddressProposition(rewardAddress))

    // store the forger stake data
    view.updateAccountStorageBytes(FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS, forgerKey,
      ForgerInfoV3Serializer.toBytes(forgerStakeData))

    val forgerHistory = StakeHistory(forgerKey)
    forgerHistory.addCheckpoint(view, epochNumber, stakedAmount)

    val stakeKey = getStakeKey(forgerKey, delegatorPublicKey)
    val stakeHistory = StakeHistory(stakeKey)
    stakeHistory.addCheckpoint(view, epochNumber, stakedAmount)

    addNewDelegator(view, forgerKey, delegatorPublicKey)

  }

  def addNewDelegator(view: BaseAccountStateView, forgerKey: Array[Byte], delegator: Address): Unit = {
    val delegatorAddressProposition = new AddressProposition(delegator)
    val listOfDelegators = DelegatorList(forgerKey)
    listOfDelegators.addDelegator(view, delegatorAddressProposition)

    val listOfForgers = ForgerList(delegatorAddressProposition)
    listOfForgers.addForger(view, forgerKey)

  }

  private[horizen] def existsForger(view: BaseAccountStateView, forgerKey: Array[Byte]): Boolean = {
    val forgerData = view.getAccountStorage(FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS, forgerKey)
    // getting a not existing key from state DB using RAW strategy
    // gives an array of 32 bytes filled with 0, while using CHUNK strategy
    // gives an empty array instead
    !forgerData.sameElements(NULL_HEX_STRING_32)
  }

  def getPagedListOfForgers(view: BaseAccountStateView, startPos: Int, pageSize: Int): (Int, Seq[ForgerInfoV3]) = {

    val listSize = forgerList.getSize(view)
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
      ForgerInfoV3Serializer.parseBytes(view.getAccountStorageBytes(FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS, currentKey))
    })

    if (endPos == listSize) {
      // tell the caller we are done with the array
      endPos = -1
    }

    (endPos, listOfElems)
  }

  def addStake(view: BaseAccountStateView,
                blockSignProposition: PublicKey25519Proposition,
                vrfPublicKey: VrfPublicKey,
                epochNumber: Int,
                delegatorPublicKey: Address,
                stakedAmount: BigInteger): Unit = {

//    val forgerKey = getForgerKey(blockSignProposition, vrfPublicKey)
//    if (!existsForger(view, forgerKey))
//      throw new ExecutionRevertedException(s"Forger doesn't exist.")
//
//    val forgerHistory = StakeHistory(forgerKey)
////   forgerHistory.updateOrAddCheckpoint(view, epochNumber, stakedAmount)
//    forgerHistory.updateOrAddCheckpoint(view, epochNumber, latestStake => latestStake.add(stakedAmount))
//
//    val stakeKey = getStakeKey(forgerKey, delegatorPublicKey)
//    val stakeHistory = StakeHistory(stakeKey)
//    if (stakeHistory.getSize(view) > 0)
////      stakeHistory.updateOrAddCheckpoint(view, epochNumber, stakedAmount)
//      stakeHistory.updateOrAddCheckpoint(view, epochNumber, latestStake => latestStake.add(stakedAmount))
//    else {
//      stakeHistory.addCheckpoint(view, epochNumber, stakedAmount)
//      val ownerAddressProposition = new AddressProposition(delegatorPublicKey)
//      val listOfDelegators = DelegatorList(forgerKey)
//      listOfDelegators.addDelegator(view, ownerAddressProposition)
//      val listOfForgers = ForgerList(ownerAddressProposition)
//      listOfForgers.addForger(view, forgerKey)
//    }

    val forgerKey = getForgerKey(blockSignProposition, vrfPublicKey)
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


  case class StakeHistory(uid: Array[Byte])
    extends StateDbArray(FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS, Blake2b256.hash(Bytes.concat(uid, "History".getBytes("UTF-8")))) {

    def addCheckpoint(view: BaseAccountStateView, epoch: Int, stakeAmount: BigInteger): Unit = {
      val checkpoint = StakeCheckpoint(epoch, stakeAmount)
      append(view, checkpointToPaddedBytes(checkpoint))
    }

//    def updateOrAddCheckpoint(view: BaseAccountStateView, epoch: Int, stakeAmount: BigInteger): Unit = {
//      val size = getSize(view)
//      assert(size > 0)
//      val lastCheckpoint = getCheckpoint(view, size - 1)
//      val newAmount = stakeAmount.add(lastCheckpoint.stakedAmount)
//      if (lastCheckpoint.fromEpochNumber == epoch) {
//        val checkpoint = StakeCheckpoint(epoch, newAmount)
//        updateValue(view, size - 1, checkpointToPaddedBytes(checkpoint))
//      }
//      else
//        addCheckpoint(view, epoch, newAmount)
//    }

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

    private[horizen] def checkpointToPaddedBytes(checkpoint: StakeCheckpoint): Array[Byte] = {
      BytesUtils.padRightWithZeroBytes(StakeCheckpointSerializer.toBytes(checkpoint), 32)
    }
  }

  case class DelegatorList(fuid: Array[Byte])
    extends StateDbArray(FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS, Blake2b256.hash(Bytes.concat(fuid, "Delegators".getBytes("UTF-8"))))  {

    def addDelegator(view: BaseAccountStateView, delegator: AddressProposition): Unit = {
      append(view, BytesUtils.padRightWithZeroBytes(AddressPropositionSerializer.getSerializer.toBytes(delegator), 32))
    }

    def getDelegatorAt(view: BaseAccountStateView, index: Int): AddressProposition = {
      AddressPropositionSerializer.getSerializer.parseBytes(getValue(view, index))
    }
  }

  case class ForgerList(delegator: AddressProposition)
    extends StateDbArray(FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS, Blake2b256.hash(Bytes.concat(delegator.pubKeyBytes(), "Forgers".getBytes("UTF-8"))))  {

    def addForger(view: BaseAccountStateView, fuid: Array[Byte]): Unit = {
      append(view, fuid)
    }

  }

}

case class ForgerInfoV3(
                         val forgerPublicKeys: ForgerPublicKeys,
                         val rewardShare: Int,
                         val rewardAddress: AddressProposition)
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
                         val fromEpochNumber: Int,
                         val stakedAmount: BigInteger)
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
