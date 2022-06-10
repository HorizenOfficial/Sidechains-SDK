package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.utils.BytesUtils

import java.math.BigInteger
import com.google.common.primitives.Ints
import com.horizen.account.proposition.AddressProposition
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import scorex.crypto.hash.Keccak256


object ForgerStakeMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val myAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000022222222222222222222"))

  val GetListOfForgersCmd: String = "0x00"
  val AddNewStakeCmd: String =      "0x01"
  val RemoveStakeCmd: String =      "0x02"

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
          // TODO use util funcs

          
          // arguments is a concatenation of:
          //   forger public key as blockSignProposition (32 bytes), vrf public key bytes (32 bytes) and owner (eth address, 20 bytes)
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
          val currentConsensusEpochNumber: Int = view.getConsensusEpochNumber.get
          val stakeId = Keccak256.hash(Bytes.concat(
            msg.getFrom.address(), msg.getNonce.toByteArray, msg.getValue.toByteArray,
            msg.getData, Ints.toByteArray(currentConsensusEpochNumber)))

          // check that the delegation arguments satisfy the restricted list of forgers.
          // TODO - in the network params, We can add this param to arguments
          val forgersList: Seq[(PublicKey25519Proposition, VrfPublicKey)] = ???
          if (!forgersList.contains((blockSignProposition, vrfPublicKey))) {
            // TODO error handling
          }

          // store a new record into a "stake contract" storage
          val key = stakeId
          val value = Bytes.concat(
            msg.getData, msg.getValue.toByteArray, Ints.toByteArray(currentConsensusEpochNumber))

          view.updateAccountStorage(myAddress.address(), key, value)

          // TODO add log ForgerStakeDelegation(StakeId, ...) to the StateView ???
          //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally

          // decrease the balance of `from` account by `tx.value` and gas paid
          val ret : ExecutionResult = chargeSender(
            msg.getFrom.address(), AddNewStakeGasPaidValue.longValue(), msg.getValue.longValue(), view)

          ret match {
            case _: ExecutionSucceeded =>
              // increase the balance of the "stake smart contract” account
              view.addBalance(myAddress.address(), msg.getValue.longValue())

              // Maybe result is not useful in case of success execution (used probably for RPC cmds only)
              val result = stakeId
              return new ExecutionSucceeded(AddNewStakeGasPaidValue, result)

            case _ => return ret
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


