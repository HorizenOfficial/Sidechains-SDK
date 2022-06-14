package com.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import com.google.common.primitives.Bytes
import com.horizen.utils.{BytesUtils, ListSerializer}

import java.math.BigInteger
import com.google.common.primitives.Ints
import com.horizen.account.api.http.ZenWeiConverter.isValidZenAmount
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import com.horizen.serialization.Views
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Keccak256
import scorex.util.serialization.{Reader, Writer}

import java.util
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.util.{Failure, Success}


object ForgerStakeMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val myAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000022222222222222222222"))

  val stakeIdsListKey = BytesUtils.fromHexString("1122334411223344112233441122334411223344112233441122334411223344")

  val GetListOfForgersCmd: String = "00"
  val AddNewStakeCmd: String =      "01"
  val RemoveStakeCmd: String =      "02"

  val FORGING_PUBKEY_LEN = 32
  val VRF_PUBKEY_LEN     = 32
  val OWNER_ADDRESS      = 20

  // TODO set proper values
  val GetListOfForgersGasPaidValue : BigInteger = java.math.BigInteger.ONE
  val AddNewStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE
  val RemoveStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE


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
          if (!isValidZenAmount(msg.getValue))
          {
            val errMsg =s"Value is not a legal wei amount: ${msg.getValue.toString()}"
            log.error(errMsg)
            new InvalidMessage(new IllegalArgumentException(errMsg))
          }

          // arguments is a concatenation of:
          //   forger public key aka blockSignProposition (32 bytes), vrf public key bytes (32 bytes) and owner (eth address, 20 bytes)
          require(msg.getData.length == OP_CODE_LENGTH + FORGING_PUBKEY_LEN + VRF_PUBKEY_LEN + OWNER_ADDRESS,
            s"Wrong data length ${msg.getData.length}")

          val blockSignProposition : PublicKey25519Proposition = new PublicKey25519Proposition(msg.getData.slice(
            OP_CODE_LENGTH,
            OP_CODE_LENGTH + FORGING_PUBKEY_LEN))

          val vrfPublicKey :VrfPublicKey = new VrfPublicKey(msg.getData.slice(
            OP_CODE_LENGTH + FORGING_PUBKEY_LEN,
            OP_CODE_LENGTH + FORGING_PUBKEY_LEN + VRF_PUBKEY_LEN))

          val ownerPublicKey: AddressProposition = new AddressProposition(msg.getData.slice(
            OP_CODE_LENGTH + FORGING_PUBKEY_LEN + VRF_PUBKEY_LEN,
            OP_CODE_LENGTH + FORGING_PUBKEY_LEN + VRF_PUBKEY_LEN + OWNER_ADDRESS)) // This is not necessarily the same as msg.from

          // compute stakeId
          val currentConsensusEpochNumber: Int = view.getConsensusEpochNumber.getOrElse(0)
          val stakeId = Keccak256.hash(Bytes.concat(
            msg.getFrom.address(), msg.getNonce.toByteArray, msg.getValue.toByteArray,
            msg.getData, Ints.toByteArray(currentConsensusEpochNumber)))

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
            AccountForgingStakeInfo(
              stakeId, blockSignProposition, vrfPublicKey, ownerPublicKey,
              msg.getValue.longValue(), currentConsensusEpochNumber))

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




        case `RemoveStakeCmd` => // TODO
          new ExecutionSucceeded(java.math.BigInteger.ONE, null)
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

@JsonView(Array(classOf[Views.Default]))
case class AccountForgingStakeInfo(
                                    stakeId: Array[Byte],
                                    blockSignProposition: PublicKey25519Proposition,
                                    vrfPublicKey: VrfPublicKey,
                                    ownerPublicKey: AddressProposition,
                                    stakedAmount: Long,
                                    consensusEpochNumber: Int
                                  )
  extends BytesSerializable  {
  require(stakedAmount >= 0, "stakeAmount expected to be non negative.")

  override type M = AccountForgingStakeInfo

  override def serializer: ScorexSerializer[AccountForgingStakeInfo] = AccountForgingStakeInfoSerializer

  override def toString: String = "%s(stakeId: %s, blockSignPublicKey: %s, vrfPublicKey: %s, ownerPublicKey: %s, stakeAmount: %d, epoch: %d)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), blockSignProposition, vrfPublicKey, ownerPublicKey, stakedAmount, consensusEpochNumber)

}


object AccountForgingStakeInfoSerializer extends ScorexSerializer[AccountForgingStakeInfo]{
  override def serialize(s: AccountForgingStakeInfo, w: Writer): Unit = {
    w.putBytes(s.stakeId)
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    w.putLong(s.stakedAmount)
    w.putInt(s.consensusEpochNumber)
  }

  override def parse(r: Reader): AccountForgingStakeInfo = {
    val stakeId = r.getBytes(32)
    val blockSignPublicKey = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmount = r.getLong()
    val consensusEpochNumber = r.getInt

    AccountForgingStakeInfo(
      stakeId, blockSignPublicKey, vrfPublicKey, ownerPublicKey, stakeAmount, consensusEpochNumber)

  }
}
