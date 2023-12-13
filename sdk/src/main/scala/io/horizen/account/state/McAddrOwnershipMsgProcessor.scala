package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.fork.ZenDAOFork
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.state.McAddrOwnershipMsgProcessor.{AddNewOwnershipCmd, GetListOfAllOwnershipsCmd,
  GetListOfOwnerScAddressesCmd, GetListOfOwnershipsCmd, OwnershipLinkedListNullValue, OwnershipsLinkedListTipKey,
  RemoveOwnershipCmd, ScAddressRefsLinkedListNullValue, ScAddressRefsLinkedListTipKey, ecParameters, getMcSignature,
  getOwnershipId}
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state.events.{AddMcAddrOwnership, RemoveMcAddrOwnership}
import io.horizen.account.utils.BigIntegerUInt256.getUnsignedByteArray
import io.horizen.account.utils.Secp256k1.{PUBLIC_KEY_SIZE, SIGNATURE_RS_SIZE}
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
import io.horizen.params.NetworkParams
import io.horizen.utils.BytesUtils
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils.{padWithZeroBytes, toHorizenPublicKeyAddress}
import io.horizen.utils.Utils.{Ripemd160Sha256Hash, doubleSHA256Hash}
import org.bouncycastle.asn1.sec.SECNamedCurves
import org.bouncycastle.asn1.x9.X9ECParameters
import org.web3j.crypto.{Keys, Sign}
import org.web3j.utils.Numeric
import sparkz.crypto.hash.{Blake2b256, Keccak256}
import sparkz.util.SparkzLogging
import sparkz.util.encode.Base64

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import scala.jdk.CollectionConverters.seqAsJavaListConverter

trait McAddrOwnershipsProvider {
  private[horizen] def getListOfMcAddrOwnerships(view: BaseAccountStateView, scAddressOpt: Option[String] = None): Seq[McAddrOwnershipData]
  private[horizen] def getListOfOwnerScAddresses(view: BaseAccountStateView): Seq[OwnerScAddress]
  private[horizen] def ownershipDataExist(view: BaseAccountStateView, ownershipId: Array[Byte]): Boolean
}

/*
 * There are two main linked lists in this native smart contract :
 * 1) McAddrOwnershipLinkedList:
 *      keeps track of all association pairs
 *           id ---> (sc addr / mc addr)
 *    This is useful for retrieving all associations but also for checking that a mc address can be associated only to
 *    one sc address
 *
 * 2) ScAddressRefsLinkedList:
 *      keeps track of all sc addresses which owns at least one mc address
 *           id ---> sc addr
 *    This is useful for getting all owner sc address without looping on the previous list (high gas consumption)
 *
 * Moreover, for any owner sc address, there is a linked list with the associated mc addresses
 *           id ---> mc addr
 *    This can is useful for getting all mc addresses associated to an owner sc address without looping on the first
 *    list (high gas consumption)
*/
case class McAddrOwnershipMsgProcessor(params: NetworkParams) extends NativeSmartContractWithFork
  with McAddrOwnershipsProvider {

  override val contractAddress: Address = MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("McAddrOwnershipSmartContractCode")


  override protected def doSpecificInit(view: BaseAccountStateView, consensusEpochNumber: Int): Unit = {
    // set the initial value for the linked list last element (null hash)
    //-------
    // check if we have this key set to any value
    val initialTip = view.getAccountStorage(contractAddress, OwnershipsLinkedListTipKey)

    // getting a not existing key from state DB using RAW strategy as the api is doing
    // gives 32 bytes filled with 0 (CHUNK strategy gives an empty array instead)
    if (!initialTip.sameElements(NULL_HEX_STRING_32)) {

      // This should not happen, unless someone managed to write to the context of this address.
      // This should not be possible from a solidity smart contract, but could be from a different native smart contract.
      // Since the initialization is not done at genesis block, to be on the safe side, we chose not to throw an exception
      // but just log a warning and then overwrite the value.

      val warnMsg = s"Initial tip already set, overwriting it!! "
      log.warn(warnMsg)
    }

    view.updateAccountStorage(contractAddress, OwnershipsLinkedListTipKey, OwnershipLinkedListNullValue)

    // set the initial value for the linked list last element (null hash)
    //-------
    if (!view.getAccountStorage(contractAddress, ScAddressRefsLinkedListTipKey).sameElements(NULL_HEX_STRING_32)) {
      val warnMsg = s"Sc Initial tip already set, overwriting it!! "
      log.warn(warnMsg)
    }
    view.updateAccountStorage(contractAddress, ScAddressRefsLinkedListTipKey, ScAddressRefsLinkedListNullValue)
  }

  private def addMcAddrOwnership(view: BaseAccountStateView, ownershipId: Array[Byte], scAddress: Address, mcTransparentAddress: String): Unit = {

    // 1. add a new node to the linked list pointing to this obj data
    McAddrOwnershipLinkedList.addNewNode(view, ownershipId, contractAddress)

    val mcAddrOwnershipData = McAddrOwnershipData(scAddress.toStringNoPrefix, mcTransparentAddress)
    val serializedOwnershipData = McAddrOwnershipDataSerializer.toBytes(mcAddrOwnershipData)

    // store the ownership data
    view.updateAccountStorageBytes(contractAddress, ownershipId, serializedOwnershipData)

    // 2. add the info to the dynamic list of sc addresses in order to be able to retrieve association based on
    // sc address without having to loop on the whole associations list
    //--
    // get the sc address linked list (it internally perform initialization if this is the very first association for this sc address)
    val scAddrList = ScAddrOwnershipLinkedList(view, scAddress.toStringNoPrefix)

    val dataId = scAddrList.getDataId(mcTransparentAddress)
    scAddrList.addNewNode(view, dataId, contractAddress)

    // store the real data contents in state db
    val serializedData = mcTransparentAddress.getBytes(StandardCharsets.UTF_8)
    view.updateAccountStorageBytes(contractAddress, dataId, serializedData)
  }

  private def uncheckedRemoveMcAddrOwnership(
                                              view: BaseAccountStateView,
                                              ownershipId: Array[Byte],
                                              scAddressStr: String,
                                              mcTransparentAddress: String): Unit =
  {
    // we assume that the caller have checked that the address association really exists in the stateDb.

    // 1. remove the data from sc address specific linked list
    val scAddrList = ScAddrOwnershipLinkedList(view, scAddressStr)
    val dataId = scAddrList.getDataId(mcTransparentAddress)

    // in case the sc address is not associated to any mc address we should clean it up but we had rather
    // keep those initialization for future association, this saves gas twice (now and in future associations)
    scAddrList.removeNode(view, dataId, contractAddress)
    view.removeAccountStorageBytes(contractAddress, dataId)

    // 2. remove the data from the global linked list
    McAddrOwnershipLinkedList.removeNode(view, ownershipId, contractAddress)

    // remove the ownership association
    view.removeAccountStorageBytes(contractAddress, ownershipId)
  }

  def isValidOwnershipSignature(scAddress: Address, mcTransparentAddress: String, mcSignature: SignatureSecp256k1): Boolean = {
    // get a signature data obj for the verification
    val v_barr = getUnsignedByteArray(mcSignature.getV)
    val r_barr = padWithZeroBytes(getUnsignedByteArray(mcSignature.getR), SIGNATURE_RS_SIZE)
    val s_barr = padWithZeroBytes(getUnsignedByteArray(mcSignature.getS), SIGNATURE_RS_SIZE)

    val signatureData = new Sign.SignatureData(v_barr, r_barr, s_barr)

    // the sc address hex string used in the message to sign must have a checksum format (EIP-55: Mixed-case checksum address encoding)
    val hashedMsg = getMcHashedMsg(Keys.toChecksumAddress(Numeric.toHexString(scAddress.toBytes)))

    // verify MC message signature
    val recPubKey = Sign.signedMessageHashToKey(hashedMsg, signatureData)
    val recUncompressedPubKeyBytes = Bytes.concat(Array[Byte](0x04), Numeric.toBytesPadded(recPubKey, PUBLIC_KEY_SIZE))
    val ecpointRec = ecParameters.getCurve.decodePoint(recUncompressedPubKeyBytes)
    val recCompressedPubKeyBytes = ecpointRec.getEncoded(true)
    val mcPubkeyhash = Ripemd160Sha256Hash(recCompressedPubKeyBytes)
    val computedTaddr = toHorizenPublicKeyAddress(mcPubkeyhash, params)

    computedTaddr.equals(mcTransparentAddress)

  }

  // this reproduces the MC way of getting a message for signing it via rpc signmessage cmd
  private def getMcHashedMsg(messageToSignString: String) = {
    // this is the magic string prepended in zend to the message to be signed*/
    val strMessageMagic = "Zcash Signed Message:\n"
    // compute the message to be signed. Similarly to what MC does, we must prepend the size of the byte buffers
    // we are using
    val messageMagicBytes = strMessageMagic.getBytes(StandardCharsets.UTF_8)
    val mmb2 = Bytes.concat(Array[Byte](messageMagicBytes.length.asInstanceOf[Byte]), messageMagicBytes)

    // TODO: currently size < 256 which is ok for a sc address; make it generic with int_to_bytes
    val messageToSignBytes = messageToSignString.getBytes(StandardCharsets.UTF_8)
    val mts2 = Bytes.concat(Array[Byte](messageToSignBytes.length.asInstanceOf[Byte]), messageToSignBytes)
    // hash the message as MC does (double sha256)
    doubleSHA256Hash(Bytes.concat(mmb2, mts2))
  }

  def doAddNewOwnershipCmd(invocation: Invocation, view: BaseAccountStateView, msg: Message): Array[Byte] = {

    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
    if (msg.getNonce == null) {
      val errMsg = s"Call must include a nonce: msg = $msg"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // check that invocation.value is zero
    if (invocation.value.signum() != 0) {
      val errMsg = s"Value must be zero: invocation = $invocation"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // check that sender account exists
    if (!view.accountExists(msg.getFrom) ) {
      val errMsg = s"Sender account does not exist: msg = $msg"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = AddNewOwnershipCmdInputDecoder.decode(inputParams)
    val mcTransparentAddress = cmdInput.mcTransparentAddress
    val mcSignature = cmdInput.mcSignature

    // compute ownershipId
    val newOwnershipId = getOwnershipId(mcTransparentAddress)

    // verify the ownership validating the signature
    val mcSignSecp256k1: SignatureSecp256k1 = getMcSignature(mcSignature)
    if (!isValidOwnershipSignature(msg.getFrom, mcTransparentAddress, mcSignSecp256k1)) {
      val errMsg = s"Invalid mc signature $mcSignature: invocation = $invocation"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // check mc address is not yet associated to any sc address. This could happen by mistake or even if a malicious voter wants
    // to use many times a mc address he really owns
    getExistingAssociation(view, newOwnershipId) match {
      case Some(scAddrStr) =>
        val errMsg = s"MC address $mcTransparentAddress is already associated to sc address $scAddrStr: invocation = $invocation"
        log.warn(errMsg)
        throw new ExecutionRevertedException(errMsg)
      case None => // do nothing
    }

    // add the obj to stateDb
    addMcAddrOwnership(view, newOwnershipId, msg.getFrom, mcTransparentAddress)
    log.debug(s"Added ownership to stateDb: newOwnershipId=${BytesUtils.toHexString(newOwnershipId)}," +
      s" scAddress=${msg.getFrom}, mcPubKeyBytes=$mcTransparentAddress, mcSignature=$mcSignature")

    val addNewMcAddrOwnershipEvt = AddMcAddrOwnership(msg.getFrom, mcTransparentAddress)
    val evmLog = getEthereumConsensusDataLog(addNewMcAddrOwnershipEvt)
    view.addLog(evmLog)

    // result in case of success execution might be useful for RPC commands
    newOwnershipId
  }

  def doRemoveOwnershipCmd(invocation: Invocation, view: BaseAccountStateView, msg: Message): Array[Byte] = {
    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
    if (msg.getNonce == null) {
      throw new ExecutionRevertedException("Call must include a nonce")
    }

    // check that msg.value is zero
    if (invocation.value.signum() != 0) {
      throw new ExecutionRevertedException("Value must be zero")
    }

    // check that sender account exists
    if (!view.accountExists(msg.getFrom) ) {
      throw new ExecutionRevertedException(s"Sender account does not exist: ${msg.getFrom}")
    }

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = RemoveOwnershipCmdInputDecoder.decode(inputParams)

    cmdInput.mcTransparentAddressOpt match {
      case Some(mcTransparentAddress) =>
        // compute ownershipId
        val ownershipId = getOwnershipId(mcTransparentAddress)

        getExistingAssociation(view, ownershipId) match {
          case Some(scAddrStr) =>
            if (!msg.getFrom.toStringNoPrefix.equals(scAddrStr)) {
              val errMsg = s"sc address $scAddrStr is not the owner of $mcTransparentAddress: msg = $msg"
              log.warn(errMsg)
              throw new ExecutionRevertedException(errMsg)
            }
          case None =>
            throw new ExecutionRevertedException(
              s"Ownership ${BytesUtils.toHexString(ownershipId)} does not exists")
        }

        // remove the obj from stateDb
        uncheckedRemoveMcAddrOwnership(view, ownershipId, msg.getFrom.toStringNoPrefix, mcTransparentAddress)
        log.debug(s"Removed ownership from stateDb: ownershipId=${BytesUtils.toHexString(ownershipId)}," +
          s" scAddress=${msg.getFrom}, mcPubKeyBytes=$mcTransparentAddress")

        val removeMcAddrOwnershipEvt = RemoveMcAddrOwnership(msg.getFrom, mcTransparentAddress)
        val evmLog = getEthereumConsensusDataLog(removeMcAddrOwnershipEvt)
        view.addLog(evmLog)

        // result in case of success execution might be useful for RPC commands
        ownershipId

      case None =>
        // TODO handle none case, we should remove all sc address association
      throw new ExecutionRevertedException(
          s"Invalid null mc address")
    }
 }

  def doGetListOfOwnershipsCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    if (invocation.value.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = GetOwnershipsCmdInputDecoder.decode(inputParams)

    val ownershipList = getScAddrListOfMcAddrOwnerships(view, cmdInput.scAddress.toStringNoPrefix)
    McAddrOwnershipDataListEncoder.encode(ownershipList.asJava)

  }

  def doGetListOfAllOwnershipsCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    if (invocation.value.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    // check we have no other bytes after the op code in the msg data
    if (getArgumentsFromData(invocation.input).length > 0) {
      val msgStr = s"invalid msg data length: ${invocation.input.length}, expected $METHOD_ID_LENGTH"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }

    val ownershipList = getListOfMcAddrOwnerships(view)
    McAddrOwnershipDataListEncoder.encode(ownershipList.asJava)
  }

  def doGetListOfOwnerScAddressesCmd(invocation: Invocation, view: BaseAccountStateView): Array[Byte] = {
    if (invocation.value.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    // check we have no other bytes after the op code in the msg data
    if (getArgumentsFromData(invocation.input).length > 0) {
      val msgStr = s"invalid msg data length: ${invocation.input.length}, expected $METHOD_ID_LENGTH"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }

    val ownershipList = getListOfOwnerScAddresses(view)
    OwnerScAddrListEncoder.encode(ownershipList.asJava)

  }

  override def getListOfOwnerScAddresses(view: BaseAccountStateView): Seq[OwnerScAddress] = {
    var ownerScAddresses = Seq[OwnerScAddress]()

    var nodeReference = view.getAccountStorage(contractAddress, ScAddressRefsLinkedListTipKey)
    if (nodeReference.sameElements(NULL_HEX_STRING_32))
      return ownerScAddresses

    try {
      while (!ScAddressRefsLinkedList.linkedListNodeRefIsNull(nodeReference)) {
        val (item: OwnerScAddress, prevNodeReference: Array[Byte]) = ScAddressRefsLinkedList.getScAddresRefsListItem(view, nodeReference)
        ownerScAddresses = item +: ownerScAddresses
        nodeReference = prevNodeReference
      }
    } catch {
      case e: OutOfGasException =>
        log.warn(s"OOG exception thrown after getting ${ownerScAddresses.length} elements!", e)
    }

    ownerScAddresses
  }

  override def getListOfMcAddrOwnerships(view: BaseAccountStateView, scAddressOpt: Option[String] = None): Seq[McAddrOwnershipData] = {
    var ownershipsList = Seq[McAddrOwnershipData]()

    var nodeReference = view.getAccountStorage(contractAddress, OwnershipsLinkedListTipKey)
    if (nodeReference.sameElements(NULL_HEX_STRING_32))
      return ownershipsList

    scAddressOpt match {
      case Some(scAddr) =>
        return getScAddrListOfMcAddrOwnerships(view, scAddr)
      case None => // go on with this method
    }

    try {
      while (!McAddrOwnershipLinkedList.linkedListNodeRefIsNull(nodeReference)) {
        val (item: McAddrOwnershipData, prevNodeReference: Array[Byte]) = McAddrOwnershipLinkedList.getOwnershipListItem(view, nodeReference)
        ownershipsList = item +: ownershipsList
        nodeReference = prevNodeReference
      }
    } catch {
      case e: OutOfGasException =>
        log.warn(s"OOG exception thrown after getting ${ownershipsList.length} elements!", e)
    }
    ownershipsList
  }

  private def getScAddrListOfMcAddrOwnerships(view: BaseAccountStateView, scAddress: String): Seq[McAddrOwnershipData] = {

    // get the specific sc addr linked list
    val scAddrList = ScAddrOwnershipLinkedList(view, scAddress)
    var nodeReference = scAddrList.getTip(view)

    // retrieve all associations if any exists
    var ownershipsList = Seq[McAddrOwnershipData]()

    try {
      while (!scAddrList.linkedListNodeRefIsNull(nodeReference)) {
        val (item: String, prevNodeReference: Array[Byte]) = scAddrList.getItem(view, nodeReference)
        ownershipsList = McAddrOwnershipData(scAddress, item) +: ownershipsList
        nodeReference = prevNodeReference
      }
    } catch {
      case e: OutOfGasException =>
        log.warn(s"OOG exception thrown after getting ${ownershipsList.length} elements!", e)
    }

    ownershipsList
  }

  override def ownershipDataExist(view: BaseAccountStateView, ownershipId: Array[Byte]): Boolean = {
    // do the RAW-strategy read even if the record is actually multi-line in stateDb. It will save some gas.
    val data = view.getAccountStorage(contractAddress, ownershipId)
    // getting a not existing key from state DB using RAW strategy
    // gives an array of 32 bytes filled with 0, while using CHUNK strategy
    // gives an empty array instead
    !data.sameElements(NULL_HEX_STRING_32)
  }

  private def getExistingAssociation(view: BaseAccountStateView, ownershipId: Array[Byte]): Option[String] = {
    McAddrOwnershipLinkedList.getOwnershipData(view, ownershipId) match {
      case None => None
      case Some(obj) => Some(obj.scAddress)
    }
  }

  @throws(classOf[ExecutionFailedException])
  override def process(invocation: Invocation, view: BaseAccountStateView, context: ExecutionContext): Array[Byte] = {
    if (!isForkActive(context.blockContext.consensusEpochNumber)) {
      throw new ExecutionRevertedException(s"zenDao fork not active")
    }

    val gasView = view.getGasTrackedView(invocation.gasPool)
    if (!initDone(gasView)) {
      // should not happen since if the fork is active we should have perform the init at this point
      throw new ExecutionRevertedException(s"zenDao native smart contract init not done")
    }

    // this handles eoa2eoa too
    if (invocation.input.length == 0)
      throw new ExecutionRevertedException(s"No data in invocation = $invocation")

    getFunctionSignature(invocation.input) match {
      case AddNewOwnershipCmd => doAddNewOwnershipCmd(invocation, gasView, context.msg)
      case RemoveOwnershipCmd => doRemoveOwnershipCmd(invocation, gasView, context.msg)
      case GetListOfAllOwnershipsCmd => doGetListOfAllOwnershipsCmd(invocation, gasView)
      case GetListOfOwnershipsCmd => doGetListOfOwnershipsCmd(invocation, gasView)
      case GetListOfOwnerScAddressesCmd => doGetListOfOwnerScAddressesCmd(invocation, gasView)

      case opCodeHex => throw new ExecutionRevertedException(s"op code not supported: $opCodeHex")
    }
  }

  override def initDone(view: BaseAccountStateView): Boolean = {
    // depending on whether this is a warm or a cold access, this read op costs WarmStorageReadCostEIP2929 or ColdSloadCostEIP2929
    // gas units (currently defined as 100 ans 2100 resp.)
    val initialTip = view.getAccountStorage(MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS, OwnershipsLinkedListTipKey)
    !initialTip.sameElements(NULL_HEX_STRING_32)
  }

  override def isForkActive(consensusEpochNumber: Int): Boolean = {
    val forkIsActive = ZenDAOFork.get(consensusEpochNumber).active
    val strVal = if (forkIsActive) {
      "YES"
    } else {
      "NO"
    }
    log.trace(s"Epoch $consensusEpochNumber: ZenDAO fork active=$strVal")
    forkIsActive
  }

}

object McAddrOwnershipMsgProcessor extends SparkzLogging {

  val OwnershipsLinkedListTipKey: Array[Byte] = Blake2b256.hash("OwnershipTipKey")
  val OwnershipLinkedListNullValue: Array[Byte] = Blake2b256.hash("OwnershipTipNullValue")

  val ScAddressRefsLinkedListTipKey: Array[Byte] = Blake2b256.hash("ScAddrRefsTip")
  val ScAddressRefsLinkedListNullValue: Array[Byte] = Blake2b256.hash("ScAddressRefsLinkedListNull")

  val AddNewOwnershipCmd: String = getABIMethodId("sendKeysOwnership(bytes3,bytes32,bytes24,bytes32,bytes32)")
  val RemoveOwnershipCmd: String = getABIMethodId("removeKeysOwnership(bytes3,bytes32)")
  val GetListOfAllOwnershipsCmd: String = getABIMethodId("getAllKeyOwnerships()")
  val GetListOfOwnershipsCmd: String = getABIMethodId("getKeyOwnerships(address)")
  val GetListOfOwnerScAddressesCmd: String = getABIMethodId("getKeyOwnerScAddresses()")

  // ecdsa curve y^2 mod p = (x^3 + 7) mod p
  val ecParameters: X9ECParameters = SECNamedCurves.getByName("secp256k1")

  // ensure we have strings consistent with size of opcode
  require(
    AddNewOwnershipCmd.length == 2 * METHOD_ID_LENGTH &&
    RemoveOwnershipCmd.length == 2 * METHOD_ID_LENGTH &&
    GetListOfAllOwnershipsCmd.length == 2 * METHOD_ID_LENGTH &&
    GetListOfOwnershipsCmd.length == 2 * METHOD_ID_LENGTH &&
    GetListOfOwnerScAddressesCmd.length == 2 * METHOD_ID_LENGTH
  )

  def getOwnershipId(mcAddress: String): Array[Byte] = {
    // if in future we will have also any context (for instance voting id) we can concatenate it
    // to the mc addr bytes. In this way we might allow the same mc address to be associated to a different sc address
    // for a different voting
    Keccak256.hash(mcAddress.getBytes(StandardCharsets.UTF_8))
  }

  def getMcSignature(mcSignatureString: String): SignatureSecp256k1 = {

    val decodedMcSignature: Array[Byte] = Base64.decode(mcSignatureString).get

    // we subtract 0x04 from first byte which is added by mainchain to the v value indicating uncompressed
    // format of the recoverable pub key. We are not using this info
    val v: BigInteger = BigInteger.valueOf(decodedMcSignature(0) - 0x4)
    val r: BigInteger = new BigInteger(1, util.Arrays.copyOfRange(decodedMcSignature, 1, 33))
    val s: BigInteger = new BigInteger(1, util.Arrays.copyOfRange(decodedMcSignature, 33, 65))

    new SignatureSecp256k1(v, r, s)
  }

}

