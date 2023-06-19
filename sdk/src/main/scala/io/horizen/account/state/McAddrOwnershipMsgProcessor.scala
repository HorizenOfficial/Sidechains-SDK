package io.horizen.account.state

import com.google.common.primitives.Bytes
import io.horizen.account.abi.ABIUtil.{METHOD_ID_LENGTH, getABIMethodId, getArgumentsFromData, getFunctionSignature}
import io.horizen.account.proof.SignatureSecp256k1
import io.horizen.account.state.McAddrOwnershipLinkedList._
import io.horizen.account.state.McAddrOwnershipMsgProcessor._
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
import sparkz.util.encode.Base64

import java.math.BigInteger
import java.nio.charset.StandardCharsets
import java.util
import scala.jdk.CollectionConverters.seqAsJavaListConverter

trait McAddrOwnershipsProvider {
  private[horizen] def getListOfMcAddrOwnerships(view: BaseAccountStateView, scAddressOpt: Option[String] = None): Seq[McAddrOwnershipData]

  private[horizen] def ownershipDataExist(view: BaseAccountStateView, ownershipId: Array[Byte]): Boolean
}

case class McAddrOwnershipMsgProcessor(params: NetworkParams) extends NativeSmartContractMsgProcessor with McAddrOwnershipsProvider {

  override val contractAddress: Address = MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS
  override val contractCode: Array[Byte] = Keccak256.hash("McAddrOwnershipSmartContractCode")

  // ecdsa curve y^2 mod p = (x^3 + 7) mod p
  val ecParameters: X9ECParameters = SECNamedCurves.getByName("secp256k1")

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

  private def addMcAddrOwnership(view: BaseAccountStateView, ownershipId: Array[Byte], scAddress: Address, mcTransparentAddress: String): Unit = {

    // add a new node to the linked list pointing to this obj data
    addNewNode(view, ownershipId, contractAddress)

    val mcAddrOwnershipData = McAddrOwnershipData(scAddress.toStringNoPrefix, mcTransparentAddress)

    // store the ownership data
    view.updateAccountStorageBytes(contractAddress, ownershipId,
      McAddrOwnershipDataSerializer.toBytes(mcAddrOwnershipData))
  }

  private def uncheckedRemoveMcAddrOwnership(view: BaseAccountStateView, ownershipId: Array[Byte]) : Unit =
  {
    // we assume that the caller have checked that the address association really exists in the stateDb.
    val nodeToRemoveId = Blake2b256.hash(ownershipId)

    // remove the data from the linked list
    uncheckedRemoveNode(view, nodeToRemoveId, contractAddress)

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

  def doAddNewOwnershipCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {

    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
    if (msg.getNonce == null) {
      throw new ExecutionRevertedException("Call must include a nonce")
    }

    // check that msg.value is zero
    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Value must be zero")
    }

    // check that sender account exists
    if (!view.accountExists(msg.getFrom) ) {
      throw new ExecutionRevertedException(s"Sender account does not exist: ${msg.getFrom}")
    }

    val inputParams = getArgumentsFromData(msg.getData)

    val cmdInput = AddNewOwnershipCmdInputDecoder.decode(inputParams)
    val mcTransparentAddress = cmdInput.mcTransparentAddress
    val mcSignature = cmdInput.mcSignature

    // compute ownershipId
    val newOwnershipId = getOwnershipId(msg.getFrom, mcTransparentAddress)

    // verify the ownership validating the signature
    val mcSignSecp256k1: SignatureSecp256k1 = getMcSignature(mcSignature)
    if (!isValidOwnershipSignature(msg.getFrom, mcTransparentAddress, mcSignSecp256k1)) {
      throw new ExecutionRevertedException(s"Ownership ${BytesUtils.toHexString(newOwnershipId)} has not a valid mc signature")
    }

    // check we do not already have this obj in the db
    if (ownershipDataExist(view, newOwnershipId)) {
      throw new ExecutionRevertedException(
        s"Ownership ${BytesUtils.toHexString(newOwnershipId)} already exists")
    }

    // check mc address is not yet associated to any sc address. This could happen by mistake or even if a malicious voter wants
    // to use many times a mc address he really owns
    isMcAddrAlreadyAssociated(view, mcTransparentAddress) match {
      case Some(scAddrStr) =>
        throw new ExecutionRevertedException(s"MC address $mcTransparentAddress is already associated to sc address $scAddrStr")
      case None =>
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

  def doRemoveOwnershipCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    // check that message contains a nonce, in the context of RPC calls the nonce might be missing
    if (msg.getNonce == null) {
      throw new ExecutionRevertedException("Call must include a nonce")
    }

    // check that msg.value is zero
    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Value must be zero")
    }

    // check that sender account exists
    if (!view.accountExists(msg.getFrom) ) {
      throw new ExecutionRevertedException(s"Sender account does not exist: ${msg.getFrom}")
    }

    val inputParams = getArgumentsFromData(msg.getData)

    val cmdInput = RemoveOwnershipCmdInputDecoder.decode(inputParams)

    cmdInput.mcTransparentAddressOpt match {
      case Some(mcTransparentAddress) =>
        // compute ownershipId
        val ownershipId = getOwnershipId(msg.getFrom, mcTransparentAddress)

        // check we have this obj in the db
        if (!ownershipDataExist(view, ownershipId)) {
          throw new ExecutionRevertedException(
            s"Ownership ${BytesUtils.toHexString(ownershipId)} does not exists")
        }

        // remove the obj from stateDb
        uncheckedRemoveMcAddrOwnership(view, ownershipId)
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

  def doGetListOfOwnershipsCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    val inputParams = getArgumentsFromData(msg.getData)

    val cmdInput = GetOwnershipsCmdInputDecoder.decode(inputParams)

    val ownershipList = getListOfMcAddrOwnerships(view, Some(cmdInput.scAddress.toStringNoPrefix))
    McAddrOwnershipDataListEncoder.encode(ownershipList.asJava)

  }

  def doGetListOfAllOwnershipsCmd(msg: Message, view: BaseAccountStateView): Array[Byte] = {
    if (msg.getValue.signum() != 0) {
      throw new ExecutionRevertedException("Call value must be zero")
    }

    // check we have no other bytes after the op code in the msg data
    if (getArgumentsFromData(msg.getData).length > 0) {
      val msgStr = s"invalid msg data length: ${msg.getData.length}, expected $METHOD_ID_LENGTH"
      log.debug(msgStr)
      throw new ExecutionRevertedException(msgStr)
    }

    val ownershipList = getListOfMcAddrOwnerships(view)
    McAddrOwnershipDataListEncoder.encode(ownershipList.asJava)

  }

  override def getListOfMcAddrOwnerships(view: BaseAccountStateView, scAddressOpt: Option[String] = None): Seq[McAddrOwnershipData] = {
    var ownershipsList = Seq[McAddrOwnershipData]()
    var nodeReference = view.getAccountStorage(contractAddress, LinkedListTipKey)

    while (!linkedListNodeRefIsNull(nodeReference)) {
      val (item: McAddrOwnershipData, prevNodeReference: Array[Byte]) = getOwnershipListItem(view, nodeReference)
      scAddressOpt match {
        case Some(scAddr) =>
          if (scAddr == item.scAddress)
            ownershipsList = item +: ownershipsList

        case None =>
          ownershipsList = item +: ownershipsList
      }
      nodeReference = prevNodeReference
    }
    ownershipsList
  }

  override def ownershipDataExist(view: BaseAccountStateView, ownershipId: Array[Byte]): Boolean = {
    // do the RAW-strategy read even if the record is actually multi-line in stateDb. It will save some gas.
    val data = view.getAccountStorage(contractAddress, ownershipId)
    // getting a not existing key from state DB using RAW strategy
    // gives an array of 32 bytes filled with 0, while using CHUNK strategy
    // gives an empty array instead
    !data.sameElements(NULL_HEX_STRING_32)  }

  private def isMcAddrAlreadyAssociated(view: BaseAccountStateView, mcAddress: String): Option[String] = {
    var nodeReference = view.getAccountStorage(contractAddress, LinkedListTipKey)
    while (!linkedListNodeRefIsNull(nodeReference)) {
      val (item: McAddrOwnershipData, prevNodeReference: Array[Byte]) = getOwnershipListItem(view, nodeReference)
      if (item.mcTransparentAddress.equals(mcAddress))
        return Some(item.scAddress)
      nodeReference = prevNodeReference
    }
    None
  }

  @throws(classOf[ExecutionFailedException])
  override def process(msg: Message, view: BaseAccountStateView, gas: GasPool, blockContext: BlockContext): Array[Byte] = {
    val gasView = view.getGasTrackedView(gas)
    getFunctionSignature(msg.getData) match {
      case AddNewOwnershipCmd => doAddNewOwnershipCmd(msg, gasView)
      case RemoveOwnershipCmd => doRemoveOwnershipCmd(msg, gasView)
      case GetListOfAllOwnershipsCmd => doGetListOfAllOwnershipsCmd(msg, gasView)
      case GetListOfOwnershipsCmd => doGetListOfOwnershipsCmd(msg, gasView)

      case opCodeHex => throw new ExecutionRevertedException(s"op code not supported: $opCodeHex")
    }
  }

}

object McAddrOwnershipMsgProcessor {

  val LinkedListTipKey: Array[Byte] = Blake2b256.hash("OwnershipTip")
  val LinkedListNullValue: Array[Byte] = Blake2b256.hash("OwnershipNull")

  val AddNewOwnershipCmd: String = getABIMethodId("sendKeysOwnership(string, string)")
  val RemoveOwnershipCmd: String = getABIMethodId("removeKeysOwnership(string)")
  val GetListOfAllOwnershipsCmd: String = getABIMethodId("getAllKeyOwnerships()")
  val GetListOfOwnershipsCmd: String = getABIMethodId("getKeyOwnerships(address)")

  // ensure we have strings consistent with size of opcode
  require(
    AddNewOwnershipCmd.length == 2 * METHOD_ID_LENGTH &&
    RemoveOwnershipCmd.length == 2 * METHOD_ID_LENGTH &&
    GetListOfAllOwnershipsCmd.length == 2 * METHOD_ID_LENGTH &&
    GetListOfOwnershipsCmd.length == 2 * METHOD_ID_LENGTH
  )


  def getOwnershipId(scAddress: Address, mcAddress: String): Array[Byte] = {
    Keccak256.hash(Bytes.concat(scAddress.toBytes, mcAddress.getBytes(StandardCharsets.UTF_8)))
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

