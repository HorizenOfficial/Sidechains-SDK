package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.utils.{BytesUtils, ListSerializer}

import java.math.BigInteger
import com.horizen.account.utils.ZenWeiConverter.isValidZenAmount
import com.horizen.account.proof.{SignatureSecp256k1, SignatureSecp256k1Serializer}
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.params.NetworkParams
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Keccak256
import scorex.util.serialization.{Reader, Writer}

import java.util
import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}
import scala.util.{Failure, Success}


case class ForgerStakeMsgProcessor(params: NetworkParams) extends AbstractFakeSmartContractMsgProcessor {

  override val fakeSmartContractAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000022222222222222222222"))
  override def fakeSmartContractCodeHash: Array[Byte] =
    Keccak256.hash("ForgerStakeSmartContractCodeHash")

  val stakeIdsListKey : Array[Byte] = BytesUtils.fromHexString("1122334411223344112233441122334411223344112233441122334411223344")

  val GetListOfForgersCmd: String = "00"
  val AddNewStakeCmd: String =      "01"
  val RemoveStakeCmd: String =      "02"

  // TODO set proper values
  val GetListOfForgersGasPaidValue : BigInteger = java.math.BigInteger.ONE
  val AddNewStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE
  val RemoveStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE

  val networkParams : NetworkParams = params

  def getStakeId(msg: Message): Array[Byte] = {
    Keccak256.hash(Bytes.concat(
      msg.getFrom.address(), msg.getNonce.toByteArray, msg.getValue.toByteArray,
      msg.getData))
  }

  def getMessageToSign(stakeId: Array[Byte], from: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    Bytes.concat(from, nonce, stakeId)
  }

  private val forgingInfoSerializer: ListSerializer[AccountForgingStakeInfo] =
    new ListSerializer[AccountForgingStakeInfo](AccountForgingStakeInfoSerializer)

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    try {

      if (!canProcess(msg, view)) {
        val errMsg = s"Cannot process message $msg"
        log.error(errMsg)
        return new InvalidMessage(new IllegalArgumentException(errMsg))
      }

      val cmdString = BytesUtils.toHexString(getOpCodeFromData(msg.getData))
      cmdString match {
        case `GetListOfForgersCmd` =>
          // check we have no other bytes after the op code in the msg data
          if (getArgumentsFromData(msg.getData).length > 0) {
            val errMsg = s"invalid msg data length: ${msg.getData.length}, expected $OP_CODE_LENGTH"
            log.error(errMsg)
            return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(errMsg))
          }

          // get current list from db and unserialize it, just to be sure we have the right data
          val serializedStakeIdList : Array[Byte] = view.getAccountStorageBytes(fakeSmartContractAddress.address(), stakeIdsListKey).get
          val stakeInfoList = serializedStakeIdList.length match {
            case 0 =>
              new util.ArrayList[AccountForgingStakeInfo]()

            case _ =>  forgingInfoSerializer.parseBytesTry(serializedStakeIdList) match {
              case Success(obj) => obj
              case Failure(exception) =>
                val errMsg = "Error while parsing list of forging info."
                log.error(errMsg, exception)
                return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(errMsg))
            }
          }

          val listOfForgers : Array[Byte] = forgingInfoSerializer.toBytes(stakeInfoList)

          new ExecutionSucceeded(GetListOfForgersGasPaidValue, listOfForgers)

        case `AddNewStakeCmd` =>
          // first of all check msg.value, it must be a legal wei amount convertible in satoshi without any remainder
          if (!isValidZenAmount(msg.getValue)) {
            val errMsg =s"Value is not a legal wei amount: ${msg.getValue.toString()}"
            log.error(errMsg)
            return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(errMsg))
          }

          // check also that sender account exists
          if (!view.accountExists(msg.getFrom.address())) {
            val errMsg =s"Sender account does not exist: ${msg.getFrom.toString}"
            log.error(errMsg)
            return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(errMsg))
          }

          val cmdInput = AddNewStakeCmdInputSerializer.parseBytesTry(getArgumentsFromData(msg.getData)) match {
            case Success(obj) => obj
            case Failure(exception) =>
              val errMsg = "Error while parsing cmd input"
              log.error(errMsg, exception)
              return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(errMsg))
          }

          val blockSignProposition : PublicKey25519Proposition = cmdInput.blockSignProposition
          val vrfPublicKey :VrfPublicKey                       = cmdInput.vrfPublicKey
          val ownerPublicKey: AddressProposition               = cmdInput.ownerPublicKey

          // check that the delegation arguments satisfy the restricted list of forgers.
          if (!networkParams.allowedForgersList.contains((blockSignProposition, vrfPublicKey))) {
            log.error("Forger is not in the allowed list")
            return new ExecutionFailed(AddNewStakeGasPaidValue, new Exception("Forger is not in the allowed list"))
          }

          // get current stakes list from db
          val serializedStakeIdList : Array[Byte] = view.getAccountStorageBytes(fakeSmartContractAddress.address(), stakeIdsListKey).get

          val stakeInfoList = serializedStakeIdList.length match {
            case 0 =>
              new util.ArrayList[AccountForgingStakeInfo]()

            case _ =>  forgingInfoSerializer.parseBytesTry(serializedStakeIdList) match {
              case Success(obj) => obj
              case Failure(exception) =>
                val errMsg = "Error while parsing list of forging info."
                log.error(errMsg, exception)
                return new InvalidMessage(new Exception(exception))
            }
          }

          // compute stakeId
          val stakeId = getStakeId(msg)

          // do we already have this id?
          if (stakeInfoList.asScala.exists(
            x => { BytesUtils.toHexString(x.stakeId) == BytesUtils.toHexString(stakeId)}))
          {
            val errMsg = s"Stake ${BytesUtils.toHexString(stakeId)} already in stateDb"
            log.error(errMsg)
            return new ExecutionFailed(AddNewStakeGasPaidValue, new Exception(errMsg))
          }

          // add new obj to memory list
          stakeInfoList.add(
            AccountForgingStakeInfo(stakeId, blockSignProposition, vrfPublicKey, ownerPublicKey, msg.getValue))

          // decrease the balance of `from` account by `tx.value`
          view.subBalance(msg.getFrom.address(), msg.getValue) match {
            case Success(_) =>

              // serialize the list
              val newList : Array[Byte] = forgingInfoSerializer.toBytes(stakeInfoList)

              // update the db
              view.updateAccountStorageBytes(fakeSmartContractAddress.address(), stakeIdsListKey, newList).get

              // increase the balance of the "stake smart contract” account
              view.addBalance(fakeSmartContractAddress.address(), msg.getValue).get

              // TODO add log ForgerStakeDelegation(StakeId, ...) to the StateView ???
              //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally

              // Maybe result is not useful in case of success execution (used probably for RPC cmds only)
              val result = stakeId
              new ExecutionSucceeded(AddNewStakeGasPaidValue, result)

            case Failure(e) =>
              val balance = view.getBalance(msg.getFrom.address())
              log.error(s"Could not subtract ${msg.getValue} from account: current balance = $balance")
              new ExecutionFailed(AddNewStakeGasPaidValue, new Exception(e))
          }


        case `RemoveStakeCmd` =>

          val cmdInput = RemoveStakeCmdInputSerializer.parseBytesTry(getArgumentsFromData(msg.getData)) match {
            case Success(obj) => obj
            case Failure(exception) =>
              log.error("Error while parsing cmd input.", exception)
              return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(exception))
          }

          val stakeId : Array[Byte]          = cmdInput.stakeId
          val signature : SignatureSecp256k1 = cmdInput.signature

          // get current list from db
          val serializedStakeIdList : Array[Byte] = view.getAccountStorageBytes(fakeSmartContractAddress.address(), stakeIdsListKey).get

          val stakeInfoList = serializedStakeIdList.length match {
            case 0 =>
              return new ExecutionFailed(RemoveStakeGasPaidValue, new Exception("No stakes in state-db"))

            case _ =>  forgingInfoSerializer.parseBytesTry(serializedStakeIdList) match {
              case Success(obj) => obj
              case Failure(exception) =>
                log.error("Error while parsing list of forging info.", exception)
                return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(exception))
            }
          }

          // remove the entry if any
          var removedElement : AccountForgingStakeInfo = null
          val newList = stakeInfoList.asScala.filterNot(
            x => {
              if (BytesUtils.toHexString(x.stakeId) == BytesUtils.toHexString(stakeId))
              {
                removedElement = x
                true
              } else {
                false
              }
            })

          if (removedElement == null) {
            return new ExecutionFailed(RemoveStakeGasPaidValue, new Exception("No such stake id in state-db"))
          }

          // check signature
          val msgToSign = getMessageToSign(stakeId, msg.getFrom.address(), msg.getNonce.toByteArray)
          if (!signature.isValid(removedElement.ownerPublicKey, msgToSign)) {
            return new ExecutionFailed(RemoveStakeGasPaidValue, new Exception("Invalid signature"))
          }

          // serialize the list
          val newListSerialized : Array[Byte] = forgingInfoSerializer.toBytes(newList.toList.asJava)

          // update the db
          view.updateAccountStorageBytes(fakeSmartContractAddress.address(), stakeIdsListKey, newListSerialized).get

          // TODO add log ForgerStakeWithdrawal(StakeId, ...) to the StateView ???
          //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally

          // decrease the balance of the "stake smart contract” account
          view.subBalance(fakeSmartContractAddress.address(), removedElement.stakedAmount).get

          // increase the balance of owner (not the sender) by withdrawn amount.
          view.addBalance(removedElement.ownerPublicKey.address(), removedElement.stakedAmount)

          // Maybe result is not useful in case of success execution (used probably for RPC cmds only)
          val result = stakeId
          new ExecutionSucceeded(RemoveStakeGasPaidValue, result)

        case _ =>
          val errMsg = s"op code $cmdString not supported"
          log.error(errMsg)
          new ExecutionFailed(RemoveStakeGasPaidValue, new IllegalArgumentException(errMsg))
      }
    }
    catch {
      case e : Exception =>
        val errMsg = s"Exception while processing message: $msg"
        log.error(errMsg)
        new ExecutionFailed(RemoveStakeGasPaidValue, new IllegalArgumentException(e))
    }
  }
}

//@JsonView(Array(classOf[Views.Default]))
case class AccountForgingStakeInfo(
                                    stakeId: Array[Byte],
                                    blockSignProposition: PublicKey25519Proposition,
                                    vrfPublicKey: VrfPublicKey,
                                    ownerPublicKey: AddressProposition,
                                    stakedAmount: BigInteger)
  extends BytesSerializable  {
  require(stakedAmount.signum() != -1, "stakeAmount expected to be non negative.")

  override type M = AccountForgingStakeInfo

  override def serializer: ScorexSerializer[AccountForgingStakeInfo] = AccountForgingStakeInfoSerializer

  override def toString: String = "%s(stakeId: %s, blockSignPublicKey: %s, vrfPublicKey: %s, ownerPublicKey: %s, stakeAmount: %d)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), blockSignProposition, vrfPublicKey, ownerPublicKey, stakedAmount)

}


object AccountForgingStakeInfoSerializer extends ScorexSerializer[AccountForgingStakeInfo]{
  override def serialize(s: AccountForgingStakeInfo, w: Writer): Unit = {
    w.putBytes(s.stakeId)
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
  }

  override def parse(r: Reader): AccountForgingStakeInfo = {
    val stakeId = r.getBytes(32)
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigInteger(r.getBytes(stakeAmountLength))

    AccountForgingStakeInfo(stakeId, blockSignPublicKey, vrfPublicKey, ownerPublicKey, stakeAmount)

  }
}


case class AllowedForgerInfo(
                blockSignProposition: PublicKey25519Proposition,
                vrfPublicKey: VrfPublicKey
              ) extends BytesSerializable {
  override type M = AllowedForgerInfo

  override def serializer: ScorexSerializer[AllowedForgerInfo] = AllowedForgerInfoSerializer
}

object AllowedForgerInfoSerializer extends ScorexSerializer[AllowedForgerInfo] {

  override def serialize(s: AllowedForgerInfo, w: Writer): Unit = {
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
  }

  override def parse(r: Reader): AllowedForgerInfo = {
    val blockSignProposition = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    AllowedForgerInfo(blockSignProposition, vrfPublicKey)
  }
}

case class AddNewStakeCmdInput(
                        blockSignProposition: PublicKey25519Proposition,
                        vrfPublicKey: VrfPublicKey,
                        ownerPublicKey: AddressProposition)
  extends BytesSerializable  {

  override type M = AddNewStakeCmdInput

  override def serializer: ScorexSerializer[AddNewStakeCmdInput] = AddNewStakeCmdInputSerializer

  override def toString: String = "%s(blockSignPublicKey: %s, vrfPublicKey: %s, ownerPublicKey: %s)"
    .format(this.getClass.toString, blockSignProposition, vrfPublicKey, ownerPublicKey)
}

object AddNewStakeCmdInputSerializer extends ScorexSerializer[AddNewStakeCmdInput]{

  override def serialize(s: AddNewStakeCmdInput, w: Writer): Unit = {
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
  }

  override def parse(r: Reader): AddNewStakeCmdInput = {
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)

    AddNewStakeCmdInput(blockSignPublicKey, vrfPublicKey, ownerPublicKey)
  }
}


//@JsonView(Array(classOf[Views.Default]))
case class RemoveStakeCmdInput(
                             stakeId: Array[Byte],
                             signature: SignatureSecp256k1)
  extends BytesSerializable  {

  override type M = RemoveStakeCmdInput

  override def serializer: ScorexSerializer[RemoveStakeCmdInput] = RemoveStakeCmdInputSerializer

  override def toString: String = "%s(stakeId: %s, signature: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), signature)

}


object RemoveStakeCmdInputSerializer extends ScorexSerializer[RemoveStakeCmdInput]{
  override def serialize(s: RemoveStakeCmdInput, w: Writer): Unit = {
    w.putBytes(s.stakeId)
    SignatureSecp256k1Serializer.getSerializer.serialize(s.signature, w)
  }

  override def parse(r: Reader): RemoveStakeCmdInput = {
    val stakeId = r.getBytes(32)
    val signature = SignatureSecp256k1Serializer.getSerializer.parse(r)

    RemoveStakeCmdInput(stakeId, signature)

  }
}

