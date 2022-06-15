package com.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import com.google.common.primitives.Bytes
import com.horizen.utils.{BytesUtils, ListSerializer}

import java.math.BigInteger
import com.google.common.primitives.Ints
import com.horizen.account.api.http.ZenWeiConverter.isValidZenAmount
import com.horizen.account.proof.{SignatureSecp256k1, SignatureSecp256k1Serializer}
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, RemoveStakeCmd}
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import com.horizen.serialization.Views
import org.web3j.crypto.{Sign, TransactionEncoder}
import org.web3j.rlp.{RlpEncoder, RlpList, RlpType}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Keccak256
import scorex.util.serialization.{Reader, Writer}

import java.util
import java.util.Arrays.asList
import java.util.List
import scala.collection.JavaConverters.{asScalaBufferConverter, seqAsJavaListConverter}
import scala.util.{Failure, Success}


object ForgerStakeMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val myAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000022222222222222222222"))

  val stakeIdsListKey = BytesUtils.fromHexString("1122334411223344112233441122334411223344112233441122334411223344")

  val GetListOfForgersCmd: String = "00"
  val AddNewStakeCmd: String =      "01"
  val RemoveStakeCmd: String =      "02"

  // TODO set proper values
  val GetListOfForgersGasPaidValue : BigInteger = java.math.BigInteger.ONE
  val AddNewStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE
  val RemoveStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE

  def getStakeId(view: AccountStateView, msg: Message): Array[Byte] = {
    val currentConsensusEpochNumber: Int = view.getConsensusEpochNumber.getOrElse(0)
    Keccak256.hash(Bytes.concat(
      msg.getFrom.address(), msg.getNonce.toByteArray, msg.getValue.toByteArray,
      msg.getData, Ints.toByteArray(currentConsensusEpochNumber)))
  }

  def getMessageToSign(stakeId: Array[Byte], from: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    Bytes.concat(from, nonce, stakeId)
  }

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    try {
      val cmdString = BytesUtils.toHexString(getFunctionFromData(msg.getData))
      cmdString match {
        case `GetListOfForgersCmd` => // TODO
          // we do not have any argument here
          require(msg.getData.length == OP_CODE_LENGTH, s"Wrong data length ${msg.getData.length}")
          val listOfForgers = Array[Byte]() //view.getForgerList
          new ExecutionSucceeded(GetListOfForgersGasPaidValue, listOfForgers)

        case `AddNewStakeCmd` =>
          // first of all check msg.value, it must be a legal wei amount convertible in satoshi without any remainder
          if (!isValidZenAmount(msg.getValue)) {
            val errMsg =s"Value is not a legal wei amount: ${msg.getValue.toString()}"
            log.error(errMsg)
            new InvalidMessage(new IllegalArgumentException(errMsg))
          }

          val cmdInput = AddNewStakeInputSerializer.parseBytesTry(msg.getData) match {
            case Success(obj) => obj
            case Failure(exception) =>
              log.error("Error while parsing cmd input.", exception)
              return new InvalidMessage(new Exception(exception))
          }

          val blockSignProposition : PublicKey25519Proposition = cmdInput.blockSignProposition
          val vrfPublicKey :VrfPublicKey                       = cmdInput.vrfPublicKey
          val ownerPublicKey: AddressProposition               = cmdInput.ownerPublicKey


          // compute stakeId
          val stakeId = getStakeId(view, msg)

          // check that the delegation arguments satisfy the restricted list of forgers.
          // TODO - in the network params, We can add this param to arguments
          /*
          val forgersList: Seq[(PublicKey25519Proposition, VrfPublicKey)] = ???

          if (!forgersList.contains((blockSignProposition, vrfPublicKey))) {
            // TODO error handling
          }
          */
          // Add this stakeId to the list of all stakes
          val forgingInfoSerializer = new ListSerializer[AccountForgingStakeInfo](AccountForgingStakeInfoSerializer)

          // get current list from db
          val serializedStakeIdList : Array[Byte] = view.getAccountStorageBytes(myAddress.address(), stakeIdsListKey).get

          val stakeInfoList = serializedStakeIdList.length match {
            case 0 =>
              new util.ArrayList[AccountForgingStakeInfo]()

            case _ =>  forgingInfoSerializer.parseBytesTry(serializedStakeIdList) match {
              case Success(obj) => obj
              case Failure(exception) =>
                log.error("Error while parsing list of forging info.", exception)
                return new InvalidMessage(new Exception(exception))
            }
          }

          // do we already have this key?
          if (stakeInfoList.asScala.exists(
            x => { BytesUtils.toHexString(x.stakeId) == BytesUtils.toHexString(stakeId)}))
          {
            return new InvalidMessage(new Exception("Stake id already in storage"))
          }

          // add new obj
          stakeInfoList.add(
            AccountForgingStakeInfo(stakeId, blockSignProposition, vrfPublicKey, ownerPublicKey, msg.getValue.longValue()))

          // decrease the balance of `from` account by `tx.value`
          view.subBalance(msg.getFrom.address(), msg.getValue) match {
            case Success(_) =>

              // serialize the list
              val newList : Array[Byte] = forgingInfoSerializer.toBytes(stakeInfoList)

              // update the db
              view.updateAccountStorageBytes(myAddress.address(), stakeIdsListKey, newList).get

              // increase the balance of the "stake smart contract” account
              view.addBalance(myAddress.address(), msg.getValue).get

              // TODO add log ForgerStakeDelegation(StakeId, ...) to the StateView ???
              //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally

              // Maybe result is not useful in case of success execution (used probably for RPC cmds only)
              val result = stakeId
              return new ExecutionSucceeded(AddNewStakeGasPaidValue, result)

            case Failure(e) =>
              val balance = view.getBalance(msg.getFrom.address())
              log.error(s"Could not subtract ${msg.getValue} from account: current balance = ${balance}")
              return new ExecutionFailed(AddNewStakeGasPaidValue, new Exception(e))
          }




        case `RemoveStakeCmd` =>

          val cmdInput = RemoveStakeInputSerializer.parseBytesTry(msg.getData) match {
            case Success(obj) => obj
            case Failure(exception) =>
              log.error("Error while parsing cmd input.", exception)
              return new InvalidMessage(new Exception(exception))
          }

          val stakeId : Array[Byte]          = cmdInput.stakeId
          val signature : SignatureSecp256k1 = cmdInput.signature

          // get current list from db
          val serializedStakeIdList : Array[Byte] = view.getAccountStorageBytes(myAddress.address(), stakeIdsListKey).get
          val forgingInfoSerializer = new ListSerializer[AccountForgingStakeInfo](AccountForgingStakeInfoSerializer)

          val stakeInfoList = serializedStakeIdList.length match {
            case 0 =>
              return new ExecutionFailed(RemoveStakeGasPaidValue, new Exception("No stakes in state-db"))

            case _ =>  forgingInfoSerializer.parseBytesTry(serializedStakeIdList) match {
              case Success(obj) => obj
              case Failure(exception) =>
                log.error("Error while parsing list of forging info.", exception)
                return new InvalidMessage(new Exception(exception))
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
                    /**/

          // serialize the list
          val qqq = newList.toList.asJava
          val newListSerialized : Array[Byte] = forgingInfoSerializer.toBytes(qqq)

          // update the db
          view.updateAccountStorageBytes(myAddress.address(), stakeIdsListKey, newListSerialized).get

          // TODO add log ForgerStakeWithdrawal(StakeId, ...) to the StateView ???
          //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally

          // decrease the balance of the "stake smart contract” account
          view.subBalance(myAddress.address(), BigInteger.valueOf(removedElement.stakedAmount)).get

          // increase the balance of owner by withdrawn amount, and decrease by gas paid.
          view.addBalance(removedElement.ownerPublicKey.address(), BigInteger.valueOf(removedElement.stakedAmount))

          // Maybe result is not useful in case of success execution (used probably for RPC cmds only)
          val result = stakeId
          return new ExecutionSucceeded(RemoveStakeGasPaidValue, result)
      }

      new InvalidMessage(null)

    }
    catch {
      case e : Exception =>
        log.error(s"Exception while processing message: $msg",e)
        new InvalidMessage(e)
    }
  }
}

//@JsonView(Array(classOf[Views.Default]))
case class AccountForgingStakeInfo(
                                    stakeId: Array[Byte],
                                    blockSignProposition: PublicKey25519Proposition,
                                    vrfPublicKey: VrfPublicKey,
                                    ownerPublicKey: AddressProposition,
                                    stakedAmount: Long)
  extends BytesSerializable  {
  require(stakedAmount >= 0, "stakeAmount expected to be non negative.")

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
    w.putLong(s.stakedAmount)
  }

  override def parse(r: Reader): AccountForgingStakeInfo = {
    val stakeId = r.getBytes(32)
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmount = r.getLong()

    AccountForgingStakeInfo(stakeId, blockSignPublicKey, vrfPublicKey, ownerPublicKey, stakeAmount)

  }
}

case class AddNewStakeInput(
                        blockSignProposition: PublicKey25519Proposition,
                        vrfPublicKey: VrfPublicKey,
                        ownerPublicKey: AddressProposition)
  extends BytesSerializable  {

  override type M = AddNewStakeInput

  override def serializer: ScorexSerializer[AddNewStakeInput] = AddNewStakeInputSerializer

  override def toString: String = "%s(blockSignPublicKey: %s, vrfPublicKey: %s, ownerPublicKey: %s)"
    .format(this.getClass.toString, blockSignProposition, vrfPublicKey, ownerPublicKey)
}


object AddNewStakeInputSerializer extends ScorexSerializer[AddNewStakeInput]{
  override def serialize(s: AddNewStakeInput, w: Writer): Unit = {
    w.putBytes(BytesUtils.fromHexString(AddNewStakeCmd))
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
  }

  override def parse(r: Reader): AddNewStakeInput = {
    val opCode = r.getBytes(1)
    require(BytesUtils.toHexString(opCode) == AddNewStakeCmd)
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)

    AddNewStakeInput(blockSignPublicKey, vrfPublicKey, ownerPublicKey)
  }
}

//@JsonView(Array(classOf[Views.Default]))
case class RemoveStakeInput(
                             stakeId: Array[Byte],
                             signature: SignatureSecp256k1)
  extends BytesSerializable  {

  override type M = RemoveStakeInput

  override def serializer: ScorexSerializer[RemoveStakeInput] = RemoveStakeInputSerializer

  override def toString: String = "%s(stakeId: %s, signature: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), signature)

}


object RemoveStakeInputSerializer extends ScorexSerializer[RemoveStakeInput]{
  override def serialize(s: RemoveStakeInput, w: Writer): Unit = {
    w.putBytes(BytesUtils.fromHexString(RemoveStakeCmd))
    w.putBytes(s.stakeId)
    SignatureSecp256k1Serializer.getSerializer.serialize(s.signature, w)
  }

  override def parse(r: Reader): RemoveStakeInput = {
    val opCode = r.getBytes(1)
    require(BytesUtils.toHexString(opCode) == RemoveStakeCmd)
    val stakeId = r.getBytes(32)
    val signature = SignatureSecp256k1Serializer.getSerializer.parse(r)

    RemoveStakeInput(stakeId, signature)

  }
}

