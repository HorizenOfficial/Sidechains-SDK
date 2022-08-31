package com.horizen.account.state

import com.fasterxml.jackson.annotation.JsonView
import com.google.common.primitives.Bytes
import com.horizen.account.abi.ABIUtil.{METHOD_CODE_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import com.horizen.account.abi.{ABIDecoder, ABIEncodable, ABIListEncoder}
import com.horizen.account.events.{DelegateForgerStake, WithdrawForgerStake}
import com.horizen.account.proof.SignatureSecp256k1
import com.horizen.account.proposition.{AddressProposition, AddressPropositionSerializer}
import com.horizen.account.state.ForgerStakeMsgProcessor._
import com.horizen.account.utils.ZenWeiConverter.isValidZenAmount
import com.horizen.params.NetworkParams
import com.horizen.proposition.{PublicKey25519Proposition, PublicKey25519PropositionSerializer, VrfPublicKey, VrfPublicKeySerializer}
import com.horizen.serialization.Views
import com.horizen.utils.BytesUtils
import org.web3j.abi.TypeReference
import org.web3j.abi.datatypes.generated.{Bytes1, Bytes32, Uint256}
import org.web3j.abi.datatypes.{Address, StaticStruct, Type}
import org.web3j.utils.Numeric
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.{Blake2b256, Keccak256}
import scorex.util.serialization.{Reader, Writer}

import java.math.BigInteger
import java.util
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success}

trait ForgerStakesProvider {
  private[horizen] def getListOfForgers(view: BaseAccountStateView): Seq[AccountForgingStakeInfo]

  private[horizen] def addScCreationForgerStake(msg: Message, view: BaseAccountStateView): Array[Byte]

  private[horizen] def findStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeData]
}

case class ForgerStakeMsgProcessor(params: NetworkParams) extends FakeSmartContractMsgProcessor with ForgerStakesProvider {

  override val contractAddress: Array[Byte] = ForgerStakeSmartContractAddress
  override val contractCodeHash: Array[Byte] = Keccak256.hash("ForgerStakeSmartContractCodeHash")

  val networkParams: NetworkParams = params

  def getStakeId(msg: Message): Array[Byte] = {
    Keccak256.hash(Bytes.concat(
      msg.getFrom.address(), msg.getNonce.toByteArray, msg.getValue.toByteArray, msg.getData))
  }

  override def init(view: BaseAccountStateView): Unit = {
    super.init(view)
    // set the initial value for the linked list last element (null hash)

    // check we do not have this key set to any value yet
    val initialTip = view.getAccountStorage(contractAddress, LinkedListTipKey)

    // getting a not existing key from state DB using RAW strategy as the api is doing
    // gives 32 bytes filled with 0 (CHUNK strategy gives an empty array instead)
    if (!initialTip.sameElements(NULL_HEX_STRING_32))
      throw new MessageProcessorInitializationException("initial tip already set")

    view.updateAccountStorage(contractAddress, LinkedListTipKey, LinkedListNullValue)
  }

  def existsStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Boolean = {
    // do the RAW-strategy read even if the record is actually multi-line in stateDb. It will save some gas.
    val data = view.getAccountStorage(contractAddress, stakeId)
    // getting a not existing key from state DB using RAW strategy
    // gives an array of 32 bytes filled with 0, while using CHUNK strategy
    // gives an empty array instead
    !data.sameElements(NULL_HEX_STRING_32)
  }

  def findStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeData] = {
    val data = view.getAccountStorageBytes(contractAddress, stakeId)
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

  def findLinkedListNode(view: BaseAccountStateView, nodeId: Array[Byte]): Option[LinkedListNode] = {
    val data = view.getAccountStorageBytes(contractAddress, nodeId)
    if (data.length == 0) {
      // getting a not existing key from state DB using RAW strategy
      // gives an array of 32 bytes filled with 0, while using CHUNK strategy, as the api is doing here
      // gives an empty array instead
      None
    } else {
      LinkedListNodeSerializer.parseBytesTry(data) match {
        case Success(obj) => Some(obj)
        case Failure(exception) =>
          throw new ExecutionRevertedException("Error while parsing forger info.", exception)
      }
    }
  }

  def addNewNodeToList(view: BaseAccountStateView, stakeId: Array[Byte]): Unit = {
    val oldTip = view.getAccountStorage(contractAddress, LinkedListTipKey)

    val newTip = Blake2b256.hash(stakeId)

    // modify previous node (if any) to point at this one
    if (!linkedListNodeRefIsNull(oldTip)) {
      val previousNode = findLinkedListNode(view, oldTip).get

      val modPreviousNode = LinkedListNode(
        previousNode.dataKey,
        previousNode.previousNodeKey,
        newTip
      )

      // store the modified previous node
      view.updateAccountStorageBytes(contractAddress, oldTip,
        LinkedListNodeSerializer.toBytes(modPreviousNode))
    }

    // update list tip, now it is this newly added one
    view.updateAccountStorage(contractAddress, LinkedListTipKey, newTip)

    // store the new node
    view.updateAccountStorageBytes(contractAddress, newTip,
      LinkedListNodeSerializer.toBytes(
        LinkedListNode(stakeId, oldTip, LinkedListNullValue)))
  }

  def addForgerStake(view: BaseAccountStateView, stakeId: Array[Byte],
                     blockSignProposition: PublicKey25519Proposition,
                     vrfPublicKey: VrfPublicKey,
                     ownerPublicKey: AddressProposition,
                     stakedAmount: BigInteger): Unit = {

    // add a new node to the linked list pointing to this forger stake data
    addNewNodeToList(view, stakeId)

    val forgerStakeData = ForgerStakeData(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), ownerPublicKey, stakedAmount)

    // store the forger stake data
    view.updateAccountStorageBytes(contractAddress, stakeId,
      ForgerStakeDataSerializer.toBytes(forgerStakeData))
  }

  def removeForgerStake(view: BaseAccountStateView, stakeId: Array[Byte]): Unit = {
    val nodeToRemoveId = Blake2b256.hash(stakeId)

    // we assume that the caller have checked that the forger stake really exists in the stateDb.
    // in this case we must necessarily have a linked list node
    val nodeToRemove = findLinkedListNode(view, nodeToRemoveId).get

    // modify previous node if any
    if (!linkedListNodeRefIsNull(nodeToRemove.previousNodeKey)) {
      val prevNodeId = nodeToRemove.previousNodeKey
      val previousNode = findLinkedListNode(view, prevNodeId).get

      val modPreviousNode = LinkedListNode(
        previousNode.dataKey,
        previousNode.previousNodeKey,
        nodeToRemove.nextNodeKey)

      // store the modified previous node
      view.updateAccountStorageBytes(contractAddress, prevNodeId,
        LinkedListNodeSerializer.toBytes(modPreviousNode))
    }

    // modify next node if any
    if (!linkedListNodeRefIsNull(nodeToRemove.nextNodeKey)) {
      val nextNodeId = nodeToRemove.nextNodeKey
      val nextNode = findLinkedListNode(view, nextNodeId).get

      val modNextNode = LinkedListNode(
        nextNode.dataKey,
        nodeToRemove.previousNodeKey,
        nextNode.nextNodeKey)

      // store the modified next node
      view.updateAccountStorageBytes(contractAddress, nextNodeId,
        LinkedListNodeSerializer.toBytes(modNextNode))
    } else {
      // if there is no next node, we update the linked list tip to point to the previous node, promoted to be the new tip
      view.updateAccountStorage(contractAddress, LinkedListTipKey, nodeToRemove.previousNodeKey)
    }

    // remove the stake
    view.removeAccountStorageBytes(contractAddress, stakeId)

    // remove the node from the linked list
    view.removeAccountStorageBytes(contractAddress, nodeToRemoveId)
  }

  def getListItem(view: BaseAccountStateView, tip: Array[Byte]): (AccountForgingStakeInfo, Array[Byte]) = {
    if (!linkedListNodeRefIsNull(tip)) {
      val node = findLinkedListNode(view, tip).get
      val stakeData = findStakeData(view, node.dataKey).get
      val listItem = AccountForgingStakeInfo(
        node.dataKey,
        ForgerStakeData(
          ForgerPublicKeys(
            stakeData.forgerPublicKeys.blockSignPublicKey, stakeData.forgerPublicKeys.vrfPublicKey),
          stakeData.ownerPublicKey, stakeData.stakedAmount)
      )
      val prevNodeKey = node.previousNodeKey
      (listItem, prevNodeKey)
    } else {
      throw new ExecutionRevertedException("Tip has the null value, no list here")
    }
  }

  def linkedListNodeRefIsNull(ref: Array[Byte]): Boolean =
    BytesUtils.toHexString(ref).equals(BytesUtils.toHexString(LinkedListNullValue))

  override def addScCreationForgerStake(msg: Message, view: BaseAccountStateView): Array[Byte] =
    doAddNewStakeCmd(msg, view, isGenesisScCreation = true)

  def doAddNewStakeCmd(msg: Message, view: BaseAccountStateView, isGenesisScCreation: Boolean = false): Array[Byte] = {

    // first of all check msg.value, it must be a legal wei amount convertible in satoshi without any remainder
    if (!isValidZenAmount(msg.getValue)) {
      throw new ExecutionRevertedException(s"Value is not a legal wei amount: ${msg.getValue.toString()}")
    }

    // check also that sender account exists (unless we are staking in the sc creation phase)
    if (!isGenesisScCreation && !view.accountExists(msg.getFrom.address())) {
      throw new ExecutionRevertedException(s"Sender account does not exist: ${msg.getFrom.toString}")
    }

    val inputParams = getArgumentsFromData(msg.getData)

    val cmdInput = AddNewStakeCmdInputDecoder.decode(inputParams)
    val blockSignPublicKey: PublicKey25519Proposition = cmdInput.forgerPublicKeys.blockSignPublicKey
    val vrfPublicKey: VrfPublicKey = cmdInput.forgerPublicKeys.vrfPublicKey
    val ownerAddress: AddressProposition = cmdInput.ownerAddress

    // TODO decide whether we need to check also genesis case (also UTXO model)
    if (!isGenesisScCreation && networkParams.restrictForgers) {
      // check that the delegation arguments satisfy the restricted list of forgers.
      if (!networkParams.allowedForgersList.contains((blockSignPublicKey, vrfPublicKey))) {
        throw new ExecutionRevertedException("Forger is not in the allowed list")
      }
    }

    // compute stakeId
    val newStakeId = getStakeId(msg)

    // check we do not already have this stake obj in the db
    if (existsStakeData(view, newStakeId)) {
      throw new ExecutionRevertedException(s"Stake ${BytesUtils.toHexString(newStakeId)} already in stateDb")
    }

    // add the obj to stateDb
    val stakedAmount = msg.getValue
    addForgerStake(view, newStakeId, blockSignPublicKey, vrfPublicKey, ownerAddress, stakedAmount)
    log.debug(s"Added stake to stateDb: newStakeId=${BytesUtils.toHexString(newStakeId)}, blockSignPublicKey=$blockSignPublicKey, vrfPublicKey=$vrfPublicKey, ownerAddress=$ownerAddress, stakedAmount=$stakedAmount")

    val addNewStakeEvt = DelegateForgerStake(msg.getFrom, ownerAddress, newStakeId, stakedAmount)
    val evmLog = getEvmLog(addNewStakeEvt)
    view.addLog(evmLog)


    if (isGenesisScCreation) {
      // increase the balance of the "forger stake smart contract” account
      view.addBalance(contractAddress, stakedAmount)
    } else {
      // decrease the balance of `from` account by `tx.value`
      view.subBalance(msg.getFrom.address(), stakedAmount)
      // increase the balance of the "forger stake smart contract” account
      view.addBalance(contractAddress, stakedAmount)
      // TODO add log ForgerStakeDelegation(StakeId, ...) to the StateView ???
      //view.addLog(new EvmLog concrete instance) // EvmLog will be used internally
    }
    // result in case of success execution might be useful for RPC commands
    newStakeId
  }

  private def checkGetListOfForgersCmd(msg: Message): Unit = {
    // check we have no other bytes after the op code in the msg data
    if (getArgumentsFromData(msg.getData).length > 0) {
      val msgStr = s"invalid msg data length: ${msg.getData.length}, expected $METHOD_CODE_LENGTH"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }
  }

  override def getListOfForgers(view: BaseAccountStateView): Seq[AccountForgingStakeInfo] = {
    var stakeList = Seq[AccountForgingStakeInfo]()
    var nodeReference = view.getAccountStorage(contractAddress, LinkedListTipKey)

    while (!linkedListNodeRefIsNull(nodeReference)) {
      val (item: AccountForgingStakeInfo, prevNodeReference: Array[Byte]) = getListItem(view, nodeReference)
      stakeList = item +: stakeList
      nodeReference = prevNodeReference
    }
    stakeList
  }

  def doUncheckedGetListOfForgersCmd(view: BaseAccountStateView): Array[Byte] = {
    val stakeList = getListOfForgers(view)
    AccountForgingStakeInfoListEncoder.encode(stakeList.asJava)
  }

  def doGetListOfForgersCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    checkGetListOfForgersCmd(msg)
    doUncheckedGetListOfForgersCmd(view)
  }

  def doRemoveStakeCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    val inputParams = getArgumentsFromData(msg.getData)
    val cmdInput = RemoveStakeCmdInputDecoder.decode(inputParams)
    val stakeId: Array[Byte] = cmdInput.stakeId
    val signature: SignatureSecp256k1 = cmdInput.signature

    // get the forger stake data to remove
    val stakeData = findStakeData(view, stakeId)
      .getOrElse(throw new ExecutionRevertedException("No such stake id in state-db"))

    // check signature
    val msgToSign = getMessageToSign(stakeId, msg.getFrom.address(), msg.getNonce.toByteArray)
    if (!signature.isValid(stakeData.ownerPublicKey, msgToSign)) {
      throw new ExecutionRevertedException("Invalid signature")
    }

    // remove the forger stake data
    removeForgerStake(view, stakeId)

    val removeStakeEvt = WithdrawForgerStake(stakeData.ownerPublicKey, stakeId)
    val evmLog = getEvmLog(removeStakeEvt)
    view.addLog(evmLog)

    // decrease the balance of the "stake smart contract” account
    view.subBalance(contractAddress, stakeData.stakedAmount)

    // increase the balance of owner (not the sender) by withdrawn amount.
    view.addBalance(stakeData.ownerPublicKey.address(), stakeData.stakedAmount)

    // Maybe result is not useful in case of success execution (used probably for RPC commands only)
    stakeId
  }

  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool): Array[Byte] = {
    view.enableGasTracking(gas)
    getFunctionSignature(msg.getData) match {
      case GetListOfForgersCmd => doGetListOfForgersCmd(msg, view)
      case AddNewStakeCmd => doAddNewStakeCmd(msg, view)
      case RemoveStakeCmd => doRemoveStakeCmd(msg, view)
      case opCodeHex => throw new ExecutionRevertedException(s"op code $opCodeHex not supported")
    }
  }
}

object ForgerStakeMsgProcessor {

  val LinkedListTipKey: Array[Byte] = Blake2b256.hash("Tip")
  val LinkedListNullValue: Array[Byte] = Blake2b256.hash("Null")

  val GetListOfForgersCmd: String = getABIMethodId("getAllForgersStakes()")
  val AddNewStakeCmd: String = getABIMethodId("delegate(bytes32,bytes32,bytes1,address)")
  val RemoveStakeCmd: String = getABIMethodId("withdraw(bytes32,bytes1,bytes32,bytes32)")

  // ensure we have strings consistent with size of opcode
  require(
    GetListOfForgersCmd.length == 2 * METHOD_CODE_LENGTH &&
      AddNewStakeCmd.length == 2 * METHOD_CODE_LENGTH &&
      RemoveStakeCmd.length == 2 * METHOD_CODE_LENGTH
  )

  val ForgerStakeSmartContractAddress: Array[Byte] = BytesUtils.fromHexString("0000000000000000000022222222222222222222")

  def getMessageToSign(stakeId: Array[Byte], from: Array[Byte], nonce: Array[Byte]): Array[Byte] = {
    Bytes.concat(from, nonce, stakeId)
  }
}

@JsonView(Array(classOf[Views.Default]))
// used as element of the list to return when getting all forger stakes via msg processor
case class AccountForgingStakeInfo(
                                    stakeId: Array[Byte],
                                    forgerStakeData: ForgerStakeData)
  extends BytesSerializable with ABIEncodable[StaticStruct] {

  override type M = AccountForgingStakeInfo

  override def serializer: ScorexSerializer[AccountForgingStakeInfo] = AccountForgingStakeInfoSerializer

  override def toString: String = "%s(stakeId: %s, forgerStakeData: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), forgerStakeData)

  private[horizen] def asABIType(): StaticStruct = {

    val forgerPublicKeysParams = forgerStakeData.forgerPublicKeys.asABIType().getValue.asInstanceOf[util.Collection[_ <: Type[_]]]
    val listOfParams = new util.ArrayList[Type[_]]()

    listOfParams.add(new Bytes32(stakeId))
    listOfParams.add(new Uint256(forgerStakeData.stakedAmount))
    listOfParams.add(new Address(Numeric.toHexString(forgerStakeData.ownerPublicKey.address())))

    listOfParams.addAll(forgerPublicKeysParams)

    new StaticStruct(listOfParams)
  }

  override def equals(that: Any): Boolean =
    that match {
      case that: AccountForgingStakeInfo =>
        that.canEqual(this) &&
          this.forgerStakeData == that.forgerStakeData &&
          util.Arrays.equals(this.stakeId, that.stakeId)
      case _ => false
    }


  override def hashCode: Int = {
    val prime = 31
    var result = 1
    result = prime * result + (if (stakeId == null) 0 else util.Arrays.hashCode(stakeId))
    result = prime * result + (if (forgerStakeData == null) 0 else forgerStakeData.hashCode)
    result
  }

}

object AccountForgingStakeInfoListEncoder extends ABIListEncoder[AccountForgingStakeInfo, StaticStruct]{
  override def getAbiClass: Class[StaticStruct] = classOf[StaticStruct]
}

object AccountForgingStakeInfoSerializer extends ScorexSerializer[AccountForgingStakeInfo] {

  override def serialize(s: AccountForgingStakeInfo, w: Writer): Unit = {
    w.putBytes(s.stakeId)
    ForgerStakeDataSerializer.serialize(s.forgerStakeData, w)
  }

  override def parse(r: Reader): AccountForgingStakeInfo = {
    val stakeId = r.getBytes(32)
    val forgerStakeData = ForgerStakeDataSerializer.parse(r)

    AccountForgingStakeInfo(stakeId, forgerStakeData)
  }
}

@JsonView(Array(classOf[Views.Default]))
case class ForgerPublicKeys(
                             blockSignPublicKey: PublicKey25519Proposition,
                             vrfPublicKey: VrfPublicKey)
  extends BytesSerializable with ABIEncodable[StaticStruct] {
  override type M = ForgerPublicKeys

  private[horizen] def vrfPublicKeyToAbi(vrfPublicKey: Array[Byte]): (Bytes32, Bytes1) = {
    val vrfPublicKeyFirst32Bytes = new Bytes32(util.Arrays.copyOfRange(vrfPublicKey, 0, 32))
    val vrfPublicKeyLastByte = new Bytes1(Array[Byte](vrfPublicKey(32)))
    (vrfPublicKeyFirst32Bytes, vrfPublicKeyLastByte)
  }

  override def asABIType(): StaticStruct = {

    val vrfPublicKeyBytes = vrfPublicKeyToAbi(vrfPublicKey.pubKeyBytes())

    new StaticStruct(
      new Bytes32(blockSignPublicKey.bytes()),
      vrfPublicKeyBytes._1,
      vrfPublicKeyBytes._2
    )
  }

  override def serializer: ScorexSerializer[ForgerPublicKeys] = ForgerPublicKeysSerializer

}

object ForgerPublicKeysSerializer extends ScorexSerializer[ForgerPublicKeys] {

  override def serialize(s: ForgerPublicKeys, w: Writer): Unit = {
    PublicKey25519PropositionSerializer.getSerializer.serialize(s.blockSignPublicKey, w)
    VrfPublicKeySerializer.getSerializer.serialize(s.vrfPublicKey, w)
  }

  override def parse(r: Reader): ForgerPublicKeys = {
    val blockSignProposition = PublicKey25519PropositionSerializer.getSerializer.parse(r)
    val vrfPublicKey = VrfPublicKeySerializer.getSerializer.parse(r)
    ForgerPublicKeys(blockSignProposition, vrfPublicKey)
  }
}


case class AddNewStakeCmdInput(
                                forgerPublicKeys: ForgerPublicKeys,
                                ownerAddress: AddressProposition) extends ABIEncodable[StaticStruct] {


  override def asABIType(): StaticStruct = {
    val forgerPublicKeysAbi = forgerPublicKeys.asABIType()
    val listOfParams: util.List[Type[_]] = new util.ArrayList(forgerPublicKeysAbi.getValue.asInstanceOf[util.List[Type[_]]])
    //val listOfParams = new util.ArrayList(forgerPublicKeysAbi.getValue)
    listOfParams.add(new Address(Numeric.toHexString(ownerAddress.address())))
    new StaticStruct(listOfParams)

  }

  override def toString: String = "%s(forgerPubKeys: %s, ownerAddress: %s)"
    .format(this.getClass.toString, forgerPublicKeys, ownerAddress)

}

object AddNewStakeCmdInputDecoder extends ABIDecoder[AddNewStakeCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes1]() {},
      new TypeReference[Address]() {}))

   override def createType(listOfParams: util.List[Type[_]]): AddNewStakeCmdInput = {
    val forgerPublicKey = new PublicKey25519Proposition(listOfParams.get(0).asInstanceOf[Bytes32].getValue)
    val vrfKey = decodeVrfKey(listOfParams.get(1).asInstanceOf[Bytes32], listOfParams.get(2).asInstanceOf[Bytes1])
    val forgerPublicKeys = ForgerPublicKeys(forgerPublicKey, vrfKey)
    val ownerPublicKey = new AddressProposition(org.web3j.utils.Numeric.hexStringToByteArray(listOfParams.get(3).asInstanceOf[Address].getValue))

    AddNewStakeCmdInput(forgerPublicKeys, ownerPublicKey)

  }

  private[horizen] def decodeVrfKey(vrfFirst32Bytes: Bytes32, vrfLastByte: Bytes1): VrfPublicKey = {
    val vrfinBytes = vrfFirst32Bytes.getValue ++ vrfLastByte.getValue
    new VrfPublicKey(vrfinBytes)
  }
}


case class RemoveStakeCmdInput(
                                stakeId: Array[Byte],
                                signature: SignatureSecp256k1)
  extends ABIEncodable[StaticStruct] {

  override def asABIType(): StaticStruct = {
    val listOfParams: util.List[Type[_]] = util.Arrays.asList(new Bytes32(stakeId), new Bytes1(signature.getV), new Bytes32(signature.getR), new Bytes32(signature.getS))
    new StaticStruct(listOfParams)

  }

  override def toString: String = "%s(stakeId: %s, signature: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(stakeId), signature)
}

object RemoveStakeCmdInputDecoder extends ABIDecoder[RemoveStakeCmdInput] {

  override val getListOfABIParamTypes: util.List[TypeReference[Type[_]]] =
    org.web3j.abi.Utils.convert(util.Arrays.asList(
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes1]() {},
      new TypeReference[Bytes32]() {},
      new TypeReference[Bytes32]() {}))

  override def createType(listOfParams: util.List[Type[_]]): RemoveStakeCmdInput = {
    val stakeId = listOfParams.get(0).asInstanceOf[Bytes32].getValue
    val signature = decodeSignature(listOfParams.get(1).asInstanceOf[Bytes1], listOfParams.get(2).asInstanceOf[Bytes32], listOfParams.get(3).asInstanceOf[Bytes32])

    RemoveStakeCmdInput(stakeId, signature)
  }

  private[horizen] def decodeSignature(v: Bytes1, r: Bytes32, s: Bytes32): SignatureSecp256k1 = {
    new SignatureSecp256k1(v.getValue, r.getValue, s.getValue)
  }

}

// the forger stake data record, stored in stateDb as: key=stakeId / value=data
@JsonView(Array(classOf[Views.Default]))
case class ForgerStakeData(
                            forgerPublicKeys: ForgerPublicKeys,
                            ownerPublicKey: AddressProposition,
                            stakedAmount: BigInteger)
  extends BytesSerializable {

  require(stakedAmount.signum() != -1, "stakeAmount expected to be non negative.")

  override type M = ForgerStakeData

  override def serializer: ScorexSerializer[ForgerStakeData] = ForgerStakeDataSerializer

  override def toString: String = "%s(forgerPubKeys: %s, ownerAddress: %s, stakedAmount: %s)"
    .format(this.getClass.toString, forgerPublicKeys, ownerPublicKey, stakedAmount)
}

object ForgerStakeDataSerializer extends ScorexSerializer[ForgerStakeData] {
  override def serialize(s: ForgerStakeData, w: Writer): Unit = {
    ForgerPublicKeysSerializer.serialize(s.forgerPublicKeys, w)
    AddressPropositionSerializer.getSerializer.serialize(s.ownerPublicKey, w)
    w.putInt(s.stakedAmount.toByteArray.length)
    w.putBytes(s.stakedAmount.toByteArray)
  }

  override def parse(r: Reader): ForgerStakeData = {
    val forgerPublicKeys = ForgerPublicKeysSerializer.parse(r)
    val ownerPublicKey = AddressPropositionSerializer.getSerializer.parse(r)
    val stakeAmountLength = r.getInt()
    val stakeAmount = new BigInteger(r.getBytes(stakeAmountLength))

    ForgerStakeData(forgerPublicKeys, ownerPublicKey, stakeAmount)
  }
}

// A (sort of) linked list node containing:
//     stakeId of a forger stake data db record
//     two keys to contiguous nodes, previous and next
// Each node is stored in stateDb as key/value pair:
//     key=Hash(node.dataKey) / value = node
// Note:
// 1) we use Blake256b hash since stateDb internally uses Keccak hash of stakeId as key for forger stake data records
// and it would clash
// 2) TIP value is stored in the state db as well, initialized as NULL value

/*
TIP                            NULL
  |                               ^
  |                                \
  |                                 \
  +-----> NODE_n [stakeId_n, prev, next]  <------------+
                    |         |                         \
                    |         |                          \
                    |         V                           \
                    |       NODE_n-1 [stakeId_n-1, prev, next]  <------------+
                    |                   |           |                         \
                    |                   |           |                          \
                    V                   |           V                           \
                 STAKE_n                |         NODE_n-2 [stakeId_n-2, prev, next]
                                        |                     |           |
                                        |                    ...         ...
                                        V
                                     STAKE_n-1                      .
                                                                     .
                                                                      .

                                                                                    ...
                                                                                      \
                                                          NODE_1 [stakeId_n-1, prev, next]  <---------+
                                                                    |           |                      \
                                                                    |           |                       \
                                                                    |           V                        \
                                                                    |         NODE_0 [stakeId_0, prev, next]
                                                                    |                   |         |
                                                                    |                   |         |
                                                                    V                   |         V
                                                                 STAKE_1                |       NULL
                                                                                        |
                                                                                        |
                                                                                        V
                                                                                     STAKE_0

 */

case class LinkedListNode(dataKey: Array[Byte], previousNodeKey: Array[Byte], nextNodeKey: Array[Byte])
  extends BytesSerializable {

  require(dataKey.length == 32, "data key size should be 32")
  require(previousNodeKey.length == 32, "next node key size should be 32")
  require(nextNodeKey.length == 32, "next node key size should be 32")

  override type M = LinkedListNode

  override def serializer: ScorexSerializer[LinkedListNode] = LinkedListNodeSerializer

  override def toString: String = "%s(dataKey: %s, previousNodeKey: %s, nextNodeKey: %s)"
    .format(this.getClass.toString, BytesUtils.toHexString(dataKey),
      BytesUtils.toHexString(previousNodeKey), BytesUtils.toHexString(nextNodeKey))
}

object LinkedListNodeSerializer extends ScorexSerializer[LinkedListNode] {
  override def serialize(s: LinkedListNode, w: Writer): Unit = {
    w.putBytes(s.dataKey)
    w.putBytes(s.previousNodeKey)
    w.putBytes(s.nextNodeKey)
  }

  override def parse(r: Reader): LinkedListNode = {
    val dataKey = r.getBytes(32)
    val previousNodeKey = r.getBytes(32)
    val nextNodeKey = r.getBytes(32)
    LinkedListNode(dataKey, previousNodeKey, nextNodeKey)
  }
}
