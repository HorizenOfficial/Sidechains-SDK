package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.utils.{BytesUtils, ListSerializer}

import java.math.BigInteger
import com.horizen.account.utils.ZenWeiConverter.isValidZenAmount
import com.horizen.account.proof.{SignatureSecp256k1, SignatureSecp256k1Serializer}
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}

import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Keccak256
import scorex.util.serialization.{Reader, Writer}

import java.util
import java.util.List

import scala.util.{Failure, Success}


object ForgerStakeMsgProcessor extends AbstractFakeSmartContractMsgProcessor {

  override val fakeSmartContractAddress: AddressProposition = new AddressProposition(BytesUtils.fromHexString("0000000000000000000022222222222222222222"))

  val stakeIdsListKey = BytesUtils.fromHexString("1122334411223344112233441122334411223344112233441122334411223344")

  val LinkedListHeadKey : Array[Byte] = Keccak256.hash("Head")
  val LinkedListNullValue : Array[Byte] = Keccak256.hash("Null")

  val GetListOfForgersCmd: String = "00"
  val AddNewStakeCmd: String =      "01"
  val RemoveStakeCmd: String =      "02"

  // ensure we have strings consistent with size of opcode
  require(
    GetListOfForgersCmd.size == 2*OP_CODE_LENGTH &&
    AddNewStakeCmd.size == 2*OP_CODE_LENGTH &&
    RemoveStakeCmd.size == 2*OP_CODE_LENGTH
  )

  // TODO set proper values
  val GetListOfForgersGasPaidValue : BigInteger = java.math.BigInteger.ONE
  val AddNewStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE
  val RemoveStakeGasPaidValue : BigInteger      = java.math.BigInteger.ONE

  def getStakeId(msg: Message): Array[Byte] = {
    Keccak256.hash(Bytes.concat(
      msg.getFrom.address(), msg.getNonce.toByteArray, msg.getValue.toByteArray, msg.getData))
  }

  private val forgingInfoSerializer: ListSerializer[AccountForgingStakeInfo] =
    new ListSerializer[AccountForgingStakeInfo](AccountForgingStakeInfoSerializer)

  override def init(view: AccountStateView): Unit = {
    super.init(view)
    // set the initial value for the linked list head
    view.updateAccountStorage(fakeSmartContractAddress.address(), LinkedListHeadKey, LinkedListNullValue)
  }

  def getMessageToSign(stakeId: Array[Byte], from: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    Bytes.concat(from, nonce, stakeId)
  }


  def findStakeId(view: AccountStateView, stakeId: Array[Byte]): Option[LinkedListItem] = {
      view.getAccountStorageBytes(fakeSmartContractAddress.address(), stakeId) match {
        case Success(data) =>
          if(data.length == 0) {
            None
          } else {
            LinkedListItemSerializer.parseBytesTry(data) match {
              case Success(obj) => Some(obj)
              case Failure(exception) =>
                log.error("Error while parsing forging info.", exception)
                throw exception
            }
          }

        case Failure(e) =>
          log.info("No such stake id: " + e.getMessage)
          None
      }
  }

  def addStakeIdToList(view: AccountStateView, stakeId: Array[Byte]) : Unit = ???

  def addStake(view: AccountStateView, stakeId: Array[Byte],
               blockSignProposition: PublicKey25519Proposition,
               vrfPublicKey: VrfPublicKey,
               ownerPublicKey: AddressProposition,
               stakedAmount: BigInteger): Unit =
  {
    val head = view.getAccountStorage(fakeSmartContractAddress.address(), LinkedListHeadKey).get
    
    val item = LinkedListItem(
      blockSignProposition, vrfPublicKey, ownerPublicKey, stakedAmount,
      head, LinkedListNullValue)

    // update list head, now it is this newly added one
    view.updateAccountStorage(fakeSmartContractAddress.address(), LinkedListHeadKey, stakeId)

    // modify previous node (if any) to point at this one
    if (!linkedListItemRefIsNull(head))
    {
      val prevStakeId = head
      val previousItem = findStakeId(view, prevStakeId).get

      val modPreviousItem = LinkedListItem(
        previousItem.blockSignProposition,
        previousItem.vrfPublicKey,
        previousItem.ownerPublicKey,
        previousItem.stakedAmount,
        previousItem.previous,
        stakeId
      )

      // store the modified previous item
      view.updateAccountStorageBytes(fakeSmartContractAddress.address(), prevStakeId,
        LinkedListItemSerializer.toBytes(modPreviousItem))
    }

    // store the item
    view.updateAccountStorageBytes(fakeSmartContractAddress.address(), stakeId,
      LinkedListItemSerializer.toBytes(item))
  }

  def removeStake(view: AccountStateView, itemToRemoveStakeId: Array[Byte], itemToRemove: LinkedListItem) : Unit=
  {
    // modify previous node if any
    if (!linkedListItemRefIsNull(itemToRemove.previous))
    {
      val prevStakeId = itemToRemove.previous

      val previousItem = findStakeId(view, prevStakeId).get

      val modPreviousItem = LinkedListItem(
       previousItem.blockSignProposition,
       previousItem.vrfPublicKey,
       previousItem.ownerPublicKey,
       previousItem.stakedAmount,
       previousItem.previous,
       itemToRemove.next
      )

      // store the modified previous item
      view.updateAccountStorageBytes(fakeSmartContractAddress.address(), prevStakeId,
        LinkedListItemSerializer.toBytes(modPreviousItem))
    }

    // modify next node if any
    if (!linkedListItemRefIsNull(itemToRemove.next))
    {
      val nextStakeId = itemToRemove.next

      val nextItem = findStakeId(view, nextStakeId).get

      val modPreviousItem = LinkedListItem(
        nextItem.blockSignProposition,
        nextItem.vrfPublicKey,
        nextItem.ownerPublicKey,
        nextItem.stakedAmount,
        itemToRemove.previous,
        nextItem.next
      )

      // store the modified next item
      view.updateAccountStorageBytes(fakeSmartContractAddress.address(), nextStakeId,
        LinkedListItemSerializer.toBytes(modPreviousItem))
    } else {
      // if there is no next node, we update the linked list head to point to the previous node, promoted to be the new head
      view.updateAccountStorage(fakeSmartContractAddress.address(), LinkedListHeadKey, itemToRemove.previous)
    }

    // remove the stake
    view.updateAccountStorageBytes(fakeSmartContractAddress.address(), itemToRemoveStakeId, new Array[Byte](0))
  }

  def getListItem(view: AccountStateView, head: Array[Byte]) : (AccountForgingStakeInfo, Array[Byte]) = {
    if (!linkedListItemRefIsNull(head))
    {
      val stakeId = head
      val stakeItem = findStakeId(view, stakeId).get
      val listItem = AccountForgingStakeInfo(
        stakeId,
        stakeItem.blockSignProposition, stakeItem.vrfPublicKey, stakeItem.ownerPublicKey, stakeItem.stakedAmount
      )
      val prevItemRef = stakeItem.previous
      (listItem, prevItemRef)
    } else {
      throw new IllegalArgumentException("Head is the null value, no list here")
    }
  }

  def linkedListItemRefIsNull(ref: Array[Byte]) : Boolean =
    BytesUtils.toHexString(ref).equals(BytesUtils.toHexString(LinkedListNullValue))

  override def process(msg: Message, view: AccountStateView): ExecutionResult = {
    try {
      val cmdString = BytesUtils.toHexString(getOpCodeFromData(msg.getData))
      cmdString match {
        case `GetListOfForgersCmd` =>
          // check we have no other bytes after the op code in the msg data
          if (getArgumentsFromData(msg.getData).length > 0) {
            val errorMsg = s"invalid msg data length: ${msg.getData.length}, expected ${OP_CODE_LENGTH}"
            log.error(errorMsg)
            return new InvalidMessage(new Exception(errorMsg))
          }

          val stakeList = new util.ArrayList[AccountForgingStakeInfo]()
          var itemReference = view.getAccountStorage(fakeSmartContractAddress.address(), LinkedListHeadKey).get

          while (!linkedListItemRefIsNull(itemReference))
          {
            val (item : AccountForgingStakeInfo, prevItemReference: Array[Byte]) = getListItem(view, itemReference)
            stakeList.add(item)
            itemReference = prevItemReference
          }

          val listOfForgers : Array[Byte] = forgingInfoSerializer.toBytes(stakeList)

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
            val errMsg =s"Sender account does not exist: ${msg.getFrom.toString()}"
            log.error(errMsg)
            return new ExecutionFailed(AddNewStakeGasPaidValue, new IllegalArgumentException(errMsg))
          }

          val cmdInput = AddNewStakeCmdInputSerializer.parseBytesTry(getArgumentsFromData(msg.getData)) match {
            case Success(obj) => obj
            case Failure(exception) =>
              log.error("Error while parsing cmd input.", exception)
              return new InvalidMessage(new Exception(exception))
          }

          val blockSignProposition : PublicKey25519Proposition = cmdInput.blockSignProposition
          val vrfPublicKey :VrfPublicKey                       = cmdInput.vrfPublicKey
          val ownerPublicKey: AddressProposition               = cmdInput.ownerPublicKey
          val allowedForgerList: List[AllowedForgerInfo]       = cmdInput.allowedForgerList

          // check that the delegation arguments satisfy the restricted list of forgers.
          if (!allowedForgerList.contains(AllowedForgerInfo(blockSignProposition, vrfPublicKey))) {
            log.error("Forger is not in the allowed list")
            return new ExecutionFailed(AddNewStakeGasPaidValue, new Exception("Forger is not in the allowed list"))
          }

          // compute stakeId
          val newStakeId = getStakeId(msg)

          // check we do not already have this stake obj in the db
          if (findStakeId(view, newStakeId).isDefined) {
            val errorMsg = s"Stake ${BytesUtils.toHexString(newStakeId)} already in stateDb"
            log.error(errorMsg)
            return new ExecutionFailed(AddNewStakeGasPaidValue, new Exception(errorMsg))
          }

          // add the obj to stateDb
          addStake(view, newStakeId, blockSignProposition, vrfPublicKey, ownerPublicKey, msg.getValue)

          // decrease the balance of `from` account by `tx.value`
          view.subBalance(msg.getFrom.address(), msg.getValue) match {
            case Success(_) =>
              // increase the balance of the "stake smart contract” account
              view.addBalance(fakeSmartContractAddress.address(), msg.getValue).get

              // TODO add log ForgerStakeDelegation(StakeId, ...) to the StateView ???
              //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally

              // Maybe result is not useful in case of success execution (used probably for RPC cmds only)
              val result = newStakeId
              return new ExecutionSucceeded(AddNewStakeGasPaidValue, result)

            case Failure(e) =>
              val balance = view.getBalance(msg.getFrom.address())
              log.error(s"Could not subtract ${msg.getValue} from account: current balance = ${balance}")
              return new ExecutionFailed(AddNewStakeGasPaidValue, new Exception(e))
          }


        case `RemoveStakeCmd` =>

          val cmdInput = RemoveStakeCmdInputSerializer.parseBytesTry(getArgumentsFromData(msg.getData)) match {
            case Success(obj) => obj
            case Failure(exception) =>
              log.error("Error while parsing cmd input.", exception)
              return new InvalidMessage(new Exception(exception))
          }

          val stakeId : Array[Byte]          = cmdInput.stakeId
          val signature : SignatureSecp256k1 = cmdInput.signature

          // get the item to remove
          val stakeItem = findStakeId(view, stakeId) match {
            case Some(obj) => obj
            case None =>
              val errorMsg = "No such stake id in state-db"
              log.error(errorMsg)
              return new ExecutionFailed(RemoveStakeGasPaidValue, new Exception(errorMsg))
          }

          // check signature
          val msgToSign = getMessageToSign(stakeId, msg.getFrom.address(), msg.getNonce.toByteArray)
          if (!signature.isValid(stakeItem.ownerPublicKey, msgToSign)) {
            val errorMsg = "Invalid signature"
            log.error(errorMsg)
            return new ExecutionFailed(RemoveStakeGasPaidValue, new Exception(errorMsg))
          }

          // remove the item
          removeStake(view, stakeId, stakeItem)

          // TODO add log ForgerStakeWithdrawal(StakeId, ...) to the StateView ???
          //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally

          // decrease the balance of the "stake smart contract” account
          view.subBalance(fakeSmartContractAddress.address(), stakeItem.stakedAmount).get

          // increase the balance of owner (not the sender) by withdrawn amount.
          view.addBalance(stakeItem.ownerPublicKey.address(), stakeItem.stakedAmount)

          // Maybe result is not useful in case of success execution (used probably for RPC cmds only)
          val result = stakeId
          return new ExecutionSucceeded(RemoveStakeGasPaidValue, result)

        case _ =>
          val errorMsg = s"op code ${cmdString} not supported"
            log.error(errorMsg)
          new InvalidMessage(new IllegalArgumentException(errorMsg))
      }
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
                                    stakedAmount: BigInteger)
  extends BytesSerializable  {
  require(stakedAmount.signum() != -1, "stakeAmount expected to be non negative.")

  override type M = AccountForgingStakeInfo

  override def serializer: ScorexSerializer[AccountForgingStakeInfo] = AccountForgingStakeInfoSerializer

  override def toString: String = "%s(stakeId: %s, blockSignProposition: %s, vrfPublicKey: %s, ownerPublicKey: %s, stakeAmount: %d)"
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
    val blockSignProposition = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigInteger(r.getBytes(stakeAmountLength))

    AccountForgingStakeInfo(stakeId, blockSignProposition, vrfPublicKey, ownerPublicKey, stakeAmount)
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
                        ownerPublicKey: AddressProposition,
                        allowedForgerList: util.List[AllowedForgerInfo])
  extends BytesSerializable  {

  override type M = AddNewStakeCmdInput

  override def serializer: ScorexSerializer[AddNewStakeCmdInput] = AddNewStakeCmdInputSerializer

  override def toString: String = "%s(blockSignProposition: %s, vrfPublicKey: %s, ownerPublicKey: %s, allowedForgerList: %s)"
    .format(this.getClass.toString, blockSignProposition, vrfPublicKey, ownerPublicKey, allowedForgerList)
}

object AddNewStakeCmdInputSerializer extends ScorexSerializer[AddNewStakeCmdInput]{

  private val allowedForgerListSerializer : ListSerializer[AllowedForgerInfo] = new ListSerializer[AllowedForgerInfo](AllowedForgerInfoSerializer)

  override def serialize(s: AddNewStakeCmdInput, w: Writer): Unit = {
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    allowedForgerListSerializer.serialize(s.allowedForgerList, w)
  }

  override def parse(r: Reader): AddNewStakeCmdInput = {
    val blockSignProposition = PublicKey25519PropositionSerializer.getSerializer.parse(r)

    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val allowedForgerList = allowedForgerListSerializer.parse(r)

    AddNewStakeCmdInput(blockSignProposition, vrfPublicKey, ownerPublicKey, allowedForgerList)
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

case class LinkedListItem(
                    blockSignProposition: PublicKey25519Proposition,
                    vrfPublicKey: VrfPublicKey,
                    ownerPublicKey: AddressProposition,
                    stakedAmount: BigInteger,
                    previous: Array[Byte],
                    next: Array[Byte])
  extends BytesSerializable  {
  require(previous.length == 32, "next ptr size should be 32")
  require(next.length == 32, "next ptr size should be 32")

  override type M = LinkedListItem

  override def serializer: ScorexSerializer[LinkedListItem] = LinkedListItemSerializer

  override def toString: String = "%s(previous: %s, next: %s)"
    .format(this.getClass.toString,
      BytesUtils.toHexString(previous), BytesUtils.toHexString(next))
}


object LinkedListItemSerializer extends ScorexSerializer[LinkedListItem]{
  override def serialize(s: LinkedListItem, w: Writer): Unit = {
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignProposition, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
    
    w.putBytes(s.previous)
    w.putBytes(s.next)
  }

  override def parse(r: Reader): LinkedListItem = {
    val blockSignProposition = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigInteger(r.getBytes(stakeAmountLength))

    val previous = r.getBytes(32)
    val next = r.getBytes(32)

    LinkedListItem(
      blockSignProposition, vrfPublicKey, ownerPublicKey, stakeAmount,
      previous, next)
  }
}
