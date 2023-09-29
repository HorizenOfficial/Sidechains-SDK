package io.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.ForgerStakeLinkedList._
import io.horizen.account.state.ForgerStakeMsgProcessor._
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state.events.{DelegateForgerStake, OpenForgerList, WithdrawForgerStake}
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS
import io.horizen.account.utils.ZenWeiConverter.isValidZenAmount
import io.horizen.params.NetworkParams
import io.horizen.proof.Signature25519
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.utils.BytesUtils
import io.horizen.evm.Address
import sparkz.crypto.hash.{Blake2b256, Keccak256}

import java.math.BigInteger
import scala.collection.JavaConverters.seqAsJavaListConverter
import scala.util.{Failure, Success, Try}

trait ForgerStakesProvider {
  private[horizen] def getListOfForgersStakes(view: BaseAccountStateView): Seq[AccountForgingStakeInfo]

  private[horizen] def addScCreationForgerStake(msg: Message, view: BaseAccountStateView): Array[Byte]

  private[horizen] def findStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeData]

  private[horizen] def isForgerListOpen(view: BaseAccountStateView): Boolean

  private[horizen] def getAllowedForgerListIndexes(view: BaseAccountStateView): Seq[Int]
}

case class ForgerStakeMsgProcessor(params: NetworkParams) extends NativeSmartContractMsgProcessor with ForgerStakesProvider {

  override val contractAddress: Address = FORGER_STAKE_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("ForgerStakeSmartContractCode")

  val networkParams: NetworkParams = params

  def getStakeId(msg: Message): Array[Byte] = {
    Keccak256.hash(Bytes.concat(
      msg.getFrom.toBytes, msg.getNonce.toByteArray, msg.getValue.toByteArray, msg.getData))
  }

  override def init(view: BaseAccountStateView, consensusEpochNumber: Int): Unit = {
    super.init(view, consensusEpochNumber)
    // set the initial value for the linked list last element (null hash)

    // check we do not have this key set to any value yet
    val initialTip = view.getAccountStorage(contractAddress, LinkedListTipKey)

    // getting a not existing key from state DB using RAW strategy as the api is doing
    // gives 32 bytes filled with 0 (CHUNK strategy gives an empty array instead)
    if (!initialTip.sameElements(NULL_HEX_STRING_32))
      throw new MessageProcessorInitializationException("initial tip already set")

    view.updateAccountStorage(contractAddress, LinkedListTipKey, LinkedListNullValue)

    // forger list
    /* Do not initialize it here since bootstrapping tool can not do the same as of now. That would result in
       a different state root in the genesis block creation  */
    val restrictForgerList = view.getAccountStorage(contractAddress, RestrictedForgerFlagsList)
    if (!restrictForgerList.sameElements(NULL_HEX_STRING_32))
      throw new MessageProcessorInitializationException("restrictForgerList already set")
  }

  def existsStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Boolean = {
    // do the RAW-strategy read even if the record is actually multi-line in stateDb. It will save some gas.
    val data = view.getAccountStorage(contractAddress, stakeId)
    // getting a not existing key from state DB using RAW strategy
    // gives an array of 32 bytes filled with 0, while using CHUNK strategy
    // gives an empty array instead
    !data.sameElements(NULL_HEX_STRING_32)
  }

  def addForgerStake(view: BaseAccountStateView, stakeId: Array[Byte],
                     blockSignProposition: PublicKey25519Proposition,
                     vrfPublicKey: VrfPublicKey,
                     ownerPublicKey: Address,
                     stakedAmount: BigInteger): Unit = {

    // add a new node to the linked list pointing to this forger stake data
    addNewNode(view, stakeId, contractAddress)

    val forgerStakeData = ForgerStakeData(
      ForgerPublicKeys(blockSignProposition, vrfPublicKey), new AddressProposition(ownerPublicKey), stakedAmount)

    // store the forger stake data
    view.updateAccountStorageBytes(contractAddress, stakeId,
      ForgerStakeDataSerializer.toBytes(forgerStakeData))
  }

  private def removeForgerStake(view: BaseAccountStateView, stakeId: Array[Byte]): Unit = {
    // remove the data from the linked list
    removeNode(view, stakeId, contractAddress)

    // remove the stake
    view.removeAccountStorageBytes(contractAddress, stakeId)
  }


  override def addScCreationForgerStake(msg: Message, view: BaseAccountStateView): Array[Byte] =
    doAddNewStakeCmd(msg, view, isGenesisScCreation = true)

  def doAddNewStakeCmd(msg: Message, view: BaseAccountStateView, isGenesisScCreation: Boolean = false): Array[Byte] = {

    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
    if (msg.getNonce == null) {
      throw new ExecutionRevertedException("Call must include a nonce")
    }

    // check that msg.value is greater than zero
    if (msg.getValue.signum() <= 0) {
      throw new ExecutionRevertedException("Value must not be zero")
    }

    // check that msg.value is a legal wei amount convertible to satoshis without any remainder
    if (!isValidZenAmount(msg.getValue)) {
      throw new ExecutionRevertedException(s"Value is not a legal wei amount: ${msg.getValue.toString()}")
    }

    // check that sender account exists (unless we are staking in the sc creation phase)
    if (!view.accountExists(msg.getFrom) && !isGenesisScCreation) {
      throw new ExecutionRevertedException(s"Sender account does not exist: ${msg.getFrom}")
    }

    val inputParams = getArgumentsFromData(msg.getData)

    val cmdInput = AddNewStakeCmdInputDecoder.decode(inputParams)
    val blockSignPublicKey: PublicKey25519Proposition = cmdInput.forgerPublicKeys.blockSignPublicKey
    val vrfPublicKey: VrfPublicKey = cmdInput.forgerPublicKeys.vrfPublicKey
    val ownerAddress = cmdInput.ownerAddress

    if (!view.isEoaAccount(cmdInput.ownerAddress)) {
      throw new ExecutionRevertedException(s"Owner account is not an EOA")
    }

    if (!isGenesisScCreation) {
      // check that the delegation arguments satisfy the restricted list of forgers if we have any.
      if (!isForgerListOpen(view)) {
        if (!networkParams.allowedForgersList.contains((blockSignPublicKey, vrfPublicKey))) {
          throw new ExecutionRevertedException("Forger is not in the allowed list")
        }
      }
    }

    // compute stakeId
    val newStakeId = getStakeId(msg)

    // check we do not already have this stake obj in the db
    if (existsStakeData(view, newStakeId)) {
      throw new ExecutionRevertedException(s"Stake ${BytesUtils.toHexString(newStakeId)} already exists")
    }

    // add the obj to stateDb
    val stakedAmount = msg.getValue
    addForgerStake(view, newStakeId, blockSignPublicKey, vrfPublicKey, ownerAddress, stakedAmount)
    log.debug(s"Added stake to stateDb: newStakeId=${BytesUtils.toHexString(newStakeId)}, blockSignPublicKey=$blockSignPublicKey, vrfPublicKey=$vrfPublicKey, ownerAddress=$ownerAddress, stakedAmount=$stakedAmount")

    val addNewStakeEvt = DelegateForgerStake(msg.getFrom, ownerAddress, newStakeId, stakedAmount)
    val evmLog = getEthereumConsensusDataLog(addNewStakeEvt)
    view.addLog(evmLog)


    if (isGenesisScCreation) {
      // increase the balance of the "forger stake smart contract” account
      view.addBalance(contractAddress, stakedAmount)
    } else {
      // decrease the balance of `from` account by `tx.value`
      view.subBalance(msg.getFrom, stakedAmount)
      // increase the balance of the "forger stake smart contract” account
      view.addBalance(contractAddress, stakedAmount)
    }
    // result in case of success execution might be useful for RPC commands
    newStakeId
  }

  private def checkGetListOfForgersCmd(msg: Message): Unit = {
    // check we have no other bytes after the op code in the msg data
    if (getArgumentsFromData(msg.getData).length > 0) {
      val msgStr = s"invalid msg data length: ${msg.getData.length}, expected $METHOD_ID_LENGTH"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }
  }

  override def getListOfForgersStakes(view: BaseAccountStateView): Seq[AccountForgingStakeInfo] = {
    var stakeList = Seq[AccountForgingStakeInfo]()
    var nodeReference = view.getAccountStorage(contractAddress, LinkedListTipKey)

    while (!linkedListNodeRefIsNull(nodeReference)) {
      val (item: AccountForgingStakeInfo, prevNodeReference: Array[Byte]) = getStakeListItem(view, nodeReference)
      stakeList = item +: stakeList
      nodeReference = prevNodeReference
    }
    stakeList
  }

  def doUncheckedGetListOfForgersStakesCmd(view: BaseAccountStateView): Array[Byte] = {
    val stakeList = getListOfForgersStakes(view)
    AccountForgingStakeInfoListEncoder.encode(stakeList.asJava)
  }

  def doGetListOfForgersCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    checkGetListOfForgersCmd(msg)
    doUncheckedGetListOfForgersStakesCmd(view)
  }

  def doRemoveStakeCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
    if (msg.getNonce == null) {
      throw new ExecutionRevertedException("Call must include a nonce")
    }

    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    val inputParams = getArgumentsFromData(msg.getData)
    val cmdInput = RemoveStakeCmdInputDecoder.decode(inputParams)
    val stakeId: Array[Byte] = cmdInput.stakeId
    val signature: SignatureSecp256k1 = cmdInput.signature

    // get the forger stake data to remove
    val stakeData = findStakeData(view, stakeId)
      .getOrElse(throw new ExecutionRevertedException("No such stake id in state-db"))

    // check signature
    val msgToSign = getRemoveStakeCmdMessageToSign(stakeId, msg.getFrom, msg.getNonce.toByteArray)
    val isValid : Boolean = Try {
      signature.isValid(stakeData.ownerPublicKey, msgToSign)
    } match {
      case Success(result) => result
      case Failure(ex) =>
        // can throw IllegalArgumentexception if the signature data are really wrong
        throw new ExecutionRevertedException("Could not verify ill-formed signature: " + ex.getMessage)
    }
    if (!isValid) {
      throw new ExecutionRevertedException("Invalid signature")
    }

    // remove the forger stake data
    removeForgerStake(view, stakeId)

    val removeStakeEvt = WithdrawForgerStake(stakeData.ownerPublicKey.address(), stakeId)
    val evmLog = getEthereumConsensusDataLog(removeStakeEvt)
    view.addLog(evmLog)

    // decrease the balance of the "stake smart contract” account
    view.subBalance(contractAddress, stakeData.stakedAmount)

    // increase the balance of owner (not the sender) by withdrawn amount.
    view.addBalance(stakeData.ownerPublicKey.address(), stakeData.stakedAmount)

    // Maybe result is not useful in case of success execution (used probably for RPC commands only)
    stakeId
  }

  private def getAllowedForgersIndexList(view: BaseAccountStateView): Array[Byte] = {

    if (networkParams.allowedForgersList.isEmpty){
      throw new IllegalStateException("Illegal call when list of forger is empty")
    }

    // get the forger list. Lazy init
    val restrictForgerList = view.getAccountStorage(contractAddress, RestrictedForgerFlagsList)
    if (restrictForgerList.sameElements(NULL_HEX_STRING_32)) {
      // it is the first time we access this item, do the init now
      new Array[Byte](networkParams.allowedForgersList.size)
    } else {
      view.getAccountStorageBytes(contractAddress, RestrictedForgerFlagsList)
    }
  }

  def doOpenStakeForgerListCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {

    if (!networkParams.restrictForgers) {
      throw new ExecutionRevertedException("Illegal call when list of forger is not restricted")
    }

    if (networkParams.allowedForgersList.isEmpty){
      throw new ExecutionRevertedException("Illegal call when list of forger is empty")
    }

    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    val inputParams = getArgumentsFromData(msg.getData)
    val cmdInput = OpenStakeForgerListCmdInputDecoder.decode(inputParams)
    val forgerIndex: Int = cmdInput.forgerIndex
    val signature: Signature25519 = cmdInput.signature

    // check consistency of input.
    if (networkParams.allowedForgersList.size < forgerIndex+1) {
      throw new ExecutionRevertedException(
        s"Invalid forgerIndex=$forgerIndex: allowedForgersList size=${networkParams.allowedForgersList.size}")
    }

    // check signature
    val blockSignerProposition = networkParams.allowedForgersList(forgerIndex)._1

    val msgToSign = getOpenStakeForgerListCmdMessageToSign(forgerIndex, msg.getFrom, msg.getNonce.toByteArray)
    if (!signature.isValid(blockSignerProposition, msgToSign)) {
      throw new ExecutionRevertedException(s"Invalid signature, could not validate against blockSignerProposition=$blockSignerProposition")
    }

    // get the forger list. Lazy init
    val restrictForgerList = getAllowedForgersIndexList(view)

    // check that the forger list is not already open
    if (isForgerListOpenUnchecked(restrictForgerList)) {
      throw new ExecutionRevertedException("Forger list already open")
    }

    // check that the index has not been already processed
    if (restrictForgerList(forgerIndex) == 1) {
      throw new ExecutionRevertedException("Forger index already processed")
    }

    // modify the list
    restrictForgerList(forgerIndex) = 1
    view.updateAccountStorageBytes(contractAddress, RestrictedForgerFlagsList, restrictForgerList)

    val addOpenStakeForgerListEvt = OpenForgerList(forgerIndex, msg.getFrom, blockSignerProposition)
    val evmLog = getEthereumConsensusDataLog(addOpenStakeForgerListEvt)
    view.addLog(evmLog)

    restrictForgerList
  }

  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    val gasView = view.getGasTrackedView(gas)
    getFunctionSignature(msg.getData) match {
      case GetListOfForgersCmd => doGetListOfForgersCmd(msg, gasView)
      case AddNewStakeCmd => doAddNewStakeCmd(msg, gasView)
      case RemoveStakeCmd => doRemoveStakeCmd(msg, gasView)
      case OpenStakeForgerListCmd => doOpenStakeForgerListCmd(msg, gasView)
      case opCodeHex => throw new ExecutionRevertedException(s"op code not supported: $opCodeHex")
    }
  }

  override private[horizen] def findStakeData(view: BaseAccountStateView, stakeId: Array[Byte]): Option[ForgerStakeData] =
    ForgerStakeLinkedList.findStakeData(view, stakeId)

  override private[horizen] def isForgerListOpen(view: BaseAccountStateView) : Boolean = {
    if (params.restrictForgers) {
      val restrictForgerList = getAllowedForgersIndexList(view)
      isForgerListOpenUnchecked(restrictForgerList)
    } else {
      true
    }
  }

  // length is not checked, useful when the list has already been fetched and the gas paid
  private def isForgerListOpenUnchecked(list: Array[Byte]) : Boolean = {
    list.sum > list.length/2
  }

  override private[horizen] def getAllowedForgerListIndexes(view: BaseAccountStateView): Seq[Int] =
    if (params.restrictForgers) {
      val restrictForgerList = getAllowedForgersIndexList(view)
      restrictForgerList.map(_.toInt)
    } else {
      Seq()
    }
}

object ForgerStakeMsgProcessor {

  val LinkedListTipKey: Array[Byte] = Blake2b256.hash("Tip")
  val LinkedListNullValue: Array[Byte] = Blake2b256.hash("Null")
  val RestrictedForgerFlagsList: Array[Byte] = Blake2b256.hash("ClosedForgerList")


  val GetListOfForgersCmd: String = getABIMethodId("getAllForgersStakes()")
  val AddNewStakeCmd: String = getABIMethodId("delegate(bytes32,bytes32,bytes1,address)")
  val RemoveStakeCmd: String = getABIMethodId("withdraw(bytes32,bytes1,bytes32,bytes32)")
  val OpenStakeForgerListCmd: String  = getABIMethodId("openStakeForgerList(uint32,bytes32,bytes32")

  // ensure we have strings consistent with size of opcode
  require(
    GetListOfForgersCmd.length == 2 * METHOD_ID_LENGTH &&
    AddNewStakeCmd.length == 2 * METHOD_ID_LENGTH &&
    RemoveStakeCmd.length == 2 * METHOD_ID_LENGTH &&
    OpenStakeForgerListCmd.length == 2 * METHOD_ID_LENGTH
  )

  def getRemoveStakeCmdMessageToSign(stakeId: Array[Byte], from: Address, nonce: Array[Byte]): Array[Byte] = {
    Bytes.concat(from.toBytes, nonce, stakeId)
  }

  def getOpenStakeForgerListCmdMessageToSign(forgerIndex: Int, from: Address, nonce: Array[Byte]): Array[Byte] = {
    require(!(forgerIndex <0))
    Bytes.concat(Ints.toByteArray(forgerIndex), from.toBytes, nonce)
  }
}

