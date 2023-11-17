package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.fork.ZenDAOFork
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.state.McAddrOwnershipMsgProcessor.{AddNewMultisigOwnershipCmd, AddNewOwnershipCmd, GetListOfAllOwnershipsCmd, GetListOfOwnerScAddressesCmd, GetListOfOwnershipsCmd, OwnershipLinkedListNullValue, OwnershipsLinkedListTipKey, RemoveOwnershipCmd, ScAddressRefsLinkedListNullValue, ScAddressRefsLinkedListTipKey, checkMcRedeemScriptForMultisig, checkMultisigAddress, ecParameters, getMcHashedMsg, getMcSignature, getOwnershipId, verifySignaturesWithThreshold}
import io.horizen.account.state.NativeSmartContractMsgProcessor.NULL_HEX_STRING_32
import io.horizen.account.state.events.{AddMcAddrOwnership, RemoveMcAddrOwnership}
import io.horizen.account.utils.BigIntegerUInt256.getUnsignedByteArray
import io.horizen.account.utils.Secp256k1.{PUBLIC_KEY_SIZE, SIGNATURE_RS_SIZE}
import io.horizen.account.utils.WellKnownAddresses.MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
import io.horizen.params.NetworkParams
import io.horizen.utils.BytesUtils
import io.horizen.evm.Address
import io.horizen.utils.BytesUtils.{OP_CHECKMULTISIG, padWithZeroBytes, toHorizenPublicKeyAddress}
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
import scala.util.control.Breaks.{break, breakable}

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
case class McAddrOwnershipMsgProcessor(networkParams: NetworkParams) extends NativeSmartContractWithFork
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
    val computedTaddr = toHorizenPublicKeyAddress(mcPubkeyhash, networkParams)

    computedTaddr.equals(mcTransparentAddress)
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
      s" scAddress=${msg.getFrom}, mcAddress=$mcTransparentAddress, mcSignature=$mcSignature")

    val addNewMcAddrOwnershipEvt = AddMcAddrOwnership(msg.getFrom, mcTransparentAddress)
    val evmLog = getEthereumConsensusDataLog(addNewMcAddrOwnershipEvt)
    view.addLog(evmLog)

    // result in case of success execution might be useful for RPC commands
    newOwnershipId
  }


  def doAddNewMultisigOwnershipCmd(invocation: Invocation, view: BaseAccountStateView, msg: Message): Array[Byte] = {

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

    val senderScAddress = msg.getFrom

    // check that sender account exists
    if (!view.accountExists(senderScAddress) ) {
      val errMsg = s"Sender account does not exist: msg = $msg"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    val inputParams = getArgumentsFromData(invocation.input)

    val cmdInput = AddNewMultisigOwnershipCmdInputDecoder.decode(inputParams)
    val mcMultisigAddress = cmdInput.mcTransparentAddress
    val mcSignatures = cmdInput.mcSignatures
    val redeemScript = cmdInput.redeemScript

    // get threshold signature value and all pub keys from redeemScript.
    // If any semantic error is detected while parsing an exception is raised
    var retValue : (Int, Seq[Array[Byte]]) = null
    try {
      retValue = checkMcRedeemScriptForMultisig(redeemScript)
    } catch {
      case e: IllegalArgumentException =>
        val errMsg = s"Unexpected format of redeemScript: ${e.getMessage}"
        log.warn(errMsg)
        throw new ExecutionRevertedException(errMsg)
    }
    val thresholdSignatureValue = retValue._1
    val pubKeys = retValue._2

    // check validity of the addr/redeemscript pair
    if(!checkMultisigAddress(mcMultisigAddress, redeemScript, networkParams)) {
      val errMsg = s"Could not verify multisig address against redeemScript"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // compute ownershipId
    val newOwnershipId = getOwnershipId(mcMultisigAddress)

    // get the signatures, it might trow upon errors
    val signatures: Seq[SignatureSecp256k1] = mcSignatures.map( s => getMcSignature(s))

    // verify the minimum number of signatures required
    val score = verifySignaturesWithThreshold(senderScAddress, mcMultisigAddress, pubKeys, signatures, thresholdSignatureValue)
    if (score < thresholdSignatureValue) {
      val errMsg = s"Invalid number of verified signatures: $score, need: $thresholdSignatureValue: invocation = $invocation"
      log.warn(errMsg)
      throw new ExecutionRevertedException(errMsg)
    }

    // check mc address is not yet associated to any sc address. This could happen by mistake or even if a malicious voter wants
    // to use many times a mc address he really owns
    getExistingAssociation(view, newOwnershipId) match {
      case Some(scAddrStr) =>
        val errMsg = s"MC address $mcMultisigAddress is already associated to sc address $scAddrStr: invocation = $invocation"
        log.warn(errMsg)
        throw new ExecutionRevertedException(errMsg)
      case None => // do nothing
    }

    // add the obj to stateDb
    addMcAddrOwnership(view, newOwnershipId, senderScAddress, mcMultisigAddress)
    log.debug(s"Added ownership to stateDb: newOwnershipId=${BytesUtils.toHexString(newOwnershipId)}," +
      s" scAddress=$senderScAddress}, mcMultisigAddress=$mcMultisigAddress")

    // trigger an event
    val addNewMcAddrOwnershipEvt = AddMcAddrOwnership(senderScAddress, mcMultisigAddress)
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
      case AddNewMultisigOwnershipCmd => doAddNewMultisigOwnershipCmd(invocation, gasView, context.msg)
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

  val AddNewMultisigOwnershipCmd: String = getABIMethodId("sendMultisigKeysOwnership(string,string,string[])")
  val AddNewOwnershipCmd: String = getABIMethodId("sendKeysOwnership(bytes3,bytes32,bytes24,bytes32,bytes32)")
  val RemoveOwnershipCmd: String = getABIMethodId("removeKeysOwnership(bytes3,bytes32)")
  val GetListOfAllOwnershipsCmd: String = getABIMethodId("getAllKeyOwnerships()")
  val GetListOfOwnershipsCmd: String = getABIMethodId("getKeyOwnerships(address)")
  val GetListOfOwnerScAddressesCmd: String = getABIMethodId("getKeyOwnerScAddresses()")

  // ecdsa curve y^2 mod p = (x^3 + 7) mod p
  val ecParameters: X9ECParameters = SECNamedCurves.getByName("secp256k1")

  // ensure we have strings consistent with size of opcode
  require(
    AddNewMultisigOwnershipCmd.length == 2 * METHOD_ID_LENGTH &&
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

  def checkMcRedeemScriptForMultisig(redeemScript: String): (Int, Seq[Array[Byte]]) = {

    val redeemScriptBytes = BytesUtils.fromHexString(redeemScript)
    val redeemScriptLen = redeemScriptBytes.length

    // check we have a trailing OP_CHECKMULTISIG op code (0xAE) in the redeem script because we support only this type as of now
    require(redeemScriptBytes(redeemScriptLen-1) == BytesUtils.OP_CHECKMULTISIG, s"Tail of redeemScript should be OP_CHECKMULTSIG (0x${OP_CHECKMULTISIG.formatted("%02X")})")

    // read the number of pubKeys
    val numOfPubKeyByte = redeemScriptBytes(redeemScriptLen-2)
    // we can have from 1 to 16 elements, even if actually the limit on the script size in MC would only allow 15 elements (in case of compressed pub key format, otherwise even less)
    require((numOfPubKeyByte >= BytesUtils.OP_1) && (numOfPubKeyByte <= BytesUtils.OP_16), s"Number of pub keys byte 0x${numOfPubKeyByte.formatted("%02X")} out of bounds")
    val numOfPubKeys : Int = numOfPubKeyByte - BytesUtils.OFFSET_FOR_OP_N

    val thresholdSignatureValueByte = redeemScriptBytes(0)
    require((thresholdSignatureValueByte >= BytesUtils.OP_1) && (thresholdSignatureValueByte <= BytesUtils.OP_16), s"Invalid threshold signatures byte 0x${thresholdSignatureValueByte.formatted("%02X")}")
    val thresholdSignatureValue : Int = thresholdSignatureValueByte - BytesUtils.OFFSET_FOR_OP_N

    // consistency check
    require(thresholdSignatureValue <= numOfPubKeys, s"Invalid value for threshold signature value: $thresholdSignatureValue > $numOfPubKeys")

    // get all pub keys, starting from the second byte of the script (the first is the threshold sig value)
    var pos = 1
    val pubKeys = (0 until numOfPubKeys).map {
      n => {
        // read size of next pub key and advance pos
        val nextPubKeySize = redeemScriptBytes(pos)
        pos += 1

        // check that the declared size of the pub key is one in the expected pair
        require(
          nextPubKeySize == BytesUtils.HORIZEN_COMPRESSED_PUBLIC_KEY_LENGTH ||
          nextPubKeySize == BytesUtils.HORIZEN_UNCOMPRESSED_PUBLIC_KEY_LENGTH,
          s"Invalid compressed/uncompressed pub key length $nextPubKeySize red in redeemScript for pub key $n")

        // read the pub key and advance pos
        val pubKey = redeemScriptBytes.slice(pos, pos+nextPubKeySize)
        pos += nextPubKeySize

        pubKey
      }
    }

    // total length should be the value of pos and the last 2 bytes (OP_N, OP_MULTISIG)
    require(redeemScriptLen == (2 + pos), s"Invalid length of redeemScript: $redeemScriptLen")

    (thresholdSignatureValue, pubKeys)
  }


  def checkMcAddresses(mcAddress: String, params: NetworkParams): Array[Byte] = {

    // this throws if the address is not base 58 decoded. The returned data can be a pubKeyHash or a scriptHash, depending
    // on the address type
    val decodedAddressDataHash: Array[Byte] = BytesUtils.fromHorizenMcTransparentAddress(mcAddress, params)

    // check decoded length
    require(decodedAddressDataHash.length == BytesUtils.HORIZEN_ADDRESS_HASH_LENGTH,
      s"MC address decoded ${BytesUtils.toHexString(decodedAddressDataHash)}, length should be ${BytesUtils.HORIZEN_ADDRESS_HASH_LENGTH}, found ${decodedAddressDataHash.length}")

    decodedAddressDataHash
  }

  def checkMultisigAddress(mcMultisigAddress: String, redeemScript: String, params: NetworkParams) : Boolean = {
    val scriptHash = try {
      checkMcAddresses(mcMultisigAddress, params)
    } catch {
      case e: Throwable =>
        val msgStr = s"invalid mc address: ${e.getMessage}"
        log.warn(msgStr)
        NULL_HEX_STRING_32
    }
    val computedScriptHash = Ripemd160Sha256Hash(BytesUtils.fromHexString(redeemScript))
    scriptHash.sameElements(computedScriptHash)
  }

  def isValidOwnershipSignature(scAddress: Address, mcMultisigAddress: String, pubKey: Array[Byte], mcSignature: SignatureSecp256k1): Boolean = {
    // get a signature data obj for the verification
    val v_barr = getUnsignedByteArray(mcSignature.getV)
    val r_barr = padWithZeroBytes(getUnsignedByteArray(mcSignature.getR), SIGNATURE_RS_SIZE)
    val s_barr = padWithZeroBytes(getUnsignedByteArray(mcSignature.getS), SIGNATURE_RS_SIZE)

    val signatureData = new Sign.SignatureData(v_barr, r_barr, s_barr)

    // the sc address hex string used in the message to sign must have a checksum format (EIP-55: Mixed-case checksum address encoding)
    val hashedMsg = getMcHashedMsg(mcMultisigAddress + Keys.toChecksumAddress(Numeric.toHexString(scAddress.toBytes)))

    // verify MC message signature
    val recPubKey = Sign.signedMessageHashToKey(hashedMsg, signatureData)
    val recUncompressedPubKeyBytes = Bytes.concat(Array[Byte](0x04), Numeric.toBytesPadded(recPubKey, PUBLIC_KEY_SIZE))
    val ecpointRec = ecParameters.getCurve.decodePoint(recUncompressedPubKeyBytes)
    val recCompressedPubKeyBytes = ecpointRec.getEncoded(true)

    pubKey.sameElements(recCompressedPubKeyBytes)
  }

  def verifySignaturesWithThreshold(senderScAddress: Address, mcMultisigAddress: String, pubKeys: Seq[Array[Byte]], signatures: Seq[SignatureSecp256k1], thresholdSignatureValue: Int) : Int = {
    var score = 0
    var verifiedPubKeyIndexes = Seq[Int]()
    val pubKeyIndexPairs = pubKeys.zipWithIndex

    signatures.foreach {
      signature => {
        breakable {
          pubKeyIndexPairs.foreach {
            pk_idx_pair => {
              if (score < thresholdSignatureValue && // we are still below the threshold of needed verified signatures
                !verifiedPubKeyIndexes.contains(pk_idx_pair._2) && // current pk has not yet been verified
                isValidOwnershipSignature(senderScAddress, mcMultisigAddress, pk_idx_pair._1, signature) // current pk is verified against current signature
              ) {
                score += 1
                verifiedPubKeyIndexes = pk_idx_pair._2 +: verifiedPubKeyIndexes
                break
              }
            }
          }
        }
      }
    }
    score
  }

  // this reproduces the MC way of getting a message for signing it via rpc signmessage cmd
  def getMcHashedMsg(messageToSignString: String) = {
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
}

