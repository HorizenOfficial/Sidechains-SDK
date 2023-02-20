package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.receipt.EthereumConsensusDataReceipt.ReceiptStatus
import com.horizen.account.receipt.{EthereumConsensusDataLog, EthereumConsensusDataReceipt}
import com.horizen.account.state.ForgerStakeMsgProcessor.AddNewStakeCmd
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_SMART_CONTRACT_ADDRESS
import com.horizen.account.utils.{BigIntegerUtil, MainchainTxCrosschainOutputAddressUtil, ZenWeiConverter}
import com.horizen.block.{
  MainchainBlockReferenceData,
  MainchainTxForwardTransferCrosschainOutput,
  MainchainTxSidechainCreationCrosschainOutput
}
import com.horizen.certificatesubmitter.keys.{CertifiersKeys, KeyRotationProof, KeyRotationProofTypes}
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.evm.interop.{EvmLog, ProofAccountResult}
import com.horizen.evm.utils.{Address, Hash}
import com.horizen.evm.{ResourceHandle, StateDB}
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.utils.BytesUtils
import sparkz.crypto.hash.Keccak256
import sparkz.util.SparkzLogging

import java.math.BigInteger
import java.util.Optional
import scala.collection.JavaConverters.asScalaBufferConverter
import scala.util.Try

class StateDbAccountStateView(
    stateDb: StateDB,
    messageProcessors: Seq[MessageProcessor]
) extends BaseAccountStateView
      with AutoCloseable
      with SparkzLogging {

  lazy val withdrawalReqProvider: WithdrawalRequestProvider =
    messageProcessors.find(_.isInstanceOf[WithdrawalRequestProvider]).get.asInstanceOf[WithdrawalRequestProvider]
  lazy val forgerStakesProvider: ForgerStakesProvider =
    messageProcessors.find(_.isInstanceOf[ForgerStakesProvider]).get.asInstanceOf[ForgerStakesProvider]
  // certificateKeysProvider is present only for NaiveThresholdSignatureCircuitWithKeyRotation
  lazy val certificateKeysProvider: CertificateKeysProvider =
    messageProcessors.find(_.isInstanceOf[CertificateKeysProvider]).get.asInstanceOf[CertificateKeysProvider]

  override def keyRotationProof(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof] = {
    certificateKeysProvider.getKeyRotationProof(withdrawalEpoch, indexOfSigner, KeyRotationProofTypes(keyType), this)
  }

  override def certifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys] = {
    Some(certificateKeysProvider.getCertifiersKeys(withdrawalEpoch, this))
  }

  override def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequest] =
    withdrawalReqProvider.getListOfWithdrawalReqRecords(withdrawalEpoch, this)

  override def getForgerStakeData(stakeId: String): Option[ForgerStakeData] =
    forgerStakesProvider.findStakeData(this, BytesUtils.fromHexString(stakeId))

  override def isForgingOpen: Boolean =
    forgerStakesProvider.isForgerListOpen(this)

  override def getListOfForgersStakes: Seq[AccountForgingStakeInfo] =
    forgerStakesProvider.getListOfForgersStakes(this)

  override def getAllowedForgerList: Seq[Int] =
    forgerStakesProvider.getAllowedForgerListIndexes(this)

  def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Unit = {
    refData.sidechainRelatedAggregatedTransaction.foreach(aggTx => {
      aggTx.mc2scTransactionsOutputs().asScala.map {
        case sc: SidechainCreation =>
          // While processing sidechain creation output:
          // 1. extract first forger stake info: block sign public key, vrf public key, owner address, stake amount
          // 2. store the stake info record in the forging native smart contract storage
          val scOut: MainchainTxSidechainCreationCrosschainOutput = sc.getScCrOutput
          val stakedAmount = ZenWeiConverter.convertZenniesToWei(scOut.amount)
          val ownerAddress = MainchainTxCrosschainOutputAddressUtil.getAccountAddress(scOut.address)

          // customData = vrf key | blockSignerKey
          val vrfPublicKey = new VrfPublicKey(scOut.customCreationData.take(VrfPublicKey.KEY_LENGTH))
          val blockSignerProposition = new PublicKey25519Proposition(
            scOut.customCreationData
              .slice(VrfPublicKey.KEY_LENGTH, VrfPublicKey.KEY_LENGTH + PublicKey25519Proposition.KEY_LENGTH)
          )

          val cmdInput = AddNewStakeCmdInput(ForgerPublicKeys(blockSignerProposition, vrfPublicKey), ownerAddress)
          val data = Bytes.concat(BytesUtils.fromHexString(AddNewStakeCmd), cmdInput.encode())

          val message = new Message(
            ownerAddress,
            Optional.of(FORGER_STAKE_SMART_CONTRACT_ADDRESS),
            BigInteger.ZERO, // gasPrice
            BigInteger.ZERO, // gasFeeCap
            BigInteger.ZERO, // gasTipCap
            BigInteger.ZERO, // gasLimit
            stakedAmount,
            BigInteger.ONE.negate(), // a negative nonce value will rule out collision with real transactions
            data,
            false
          )

          val returnData = forgerStakesProvider.addScCreationForgerStake(message, this)
          log.debug(s"sc creation forging stake added with stakeid: ${BytesUtils.toHexString(returnData)}")

        case ft: ForwardTransfer =>
          val ftOut: MainchainTxForwardTransferCrosschainOutput = ft.getFtOutput

          // we trust the MC that this is a valid amount
          val value = ZenWeiConverter.convertZenniesToWei(ftOut.amount)

          val recipientProposition = new AddressProposition(
            MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOut.propositionBytes)
          )

          if (isEoaAccount(recipientProposition.address())) {
            // stateDb will implicitly create account if not existing yet
            addBalance(recipientProposition.address(), value)
            log.debug(s"added FT amount = $value to address=$recipientProposition")
          } else {
            val burnAddress = Address.ZERO
            log.warn(
              s"ignored FT to non-EOA account, amount=$value to address=$recipientProposition (the amount was burned by sending balance to $burnAddress address)"
            )
            addBalance(burnAddress, value)
            // TODO: we should return the amount back to mcReturnAddress instead of just burning it
          }
      }
    })
  }

  def getOrderedForgingStakesInfoSeq: Seq[ForgingStakeInfo] = {
    // get forger stakes list view (scala lazy collection)
    getListOfForgersStakes.view

      // group delegation stakes by blockSignPublicKey/vrfPublicKey pairs
      .groupBy(stake =>
        (stake.forgerStakeData.forgerPublicKeys.blockSignPublicKey, stake.forgerStakeData.forgerPublicKeys.vrfPublicKey)
      )

      // create a seq of forging stake info for every group entry summing all the delegation amounts.
      // Note: ForgingStakeInfo amount is a long and contains a Zennies amount converted from a BigInteger wei amount
      //       That is safe since the stakedAmount is checked in creation phase to be an exact zennies amount
      .map { case ((blockSignKey, vrfKey), stakes) =>
        ForgingStakeInfo(
          blockSignKey,
          vrfKey,
          stakes.map(stake =>
            ZenWeiConverter.convertWeiToZennies(stake.forgerStakeData.stakedAmount)
          ).sum
        )
      }
      .toSeq

      // sort the resulting sequence by decreasing stake amount
      .sorted(Ordering[ForgingStakeInfo].reverse)
  }

  @throws(classOf[InvalidMessageException])
  @throws(classOf[ExecutionFailedException])
  def applyMessage(msg: Message, blockGasPool: GasPool, blockContext: BlockContext): Array[Byte] = {
    new StateTransition(this, messageProcessors, blockGasPool, blockContext).transition(msg)
  }

  /**
   * Possible outcomes:
   *   - tx applied succesfully => Receipt with status success
   *   - tx execution failed => Receipt with status failed
   *     - if any ExecutionFailedException was thrown, including but not limited to:
   *     - OutOfGasException (not intrinsic gas, see below!)
   *     - EvmException (EVM reverted) / native contract exception
   *   - tx could not be applied => throws an exception (this will lead to an invalid block)
   *     - any of the preChecks fail
   *     - not enough gas for intrinsic gas
   *     - block gas limit reached
   */
  def applyTransaction(
      tx: SidechainTypes#SCAT,
      txIndex: Int,
      blockGasPool: GasPool,
      blockContext: BlockContext
  ): Try[EthereumConsensusDataReceipt] = Try {
    if (!tx.isInstanceOf[EthereumTransaction])
      throw new IllegalArgumentException(s"Unsupported transaction type ${tx.getClass.getName}")

    val ethTx = tx.asInstanceOf[EthereumTransaction]

    // It should never happen if the tx has been accepted in mempool.
    // In some negative test scenario this can happen when forcing an unsigned tx to be forged in a block.
    // In this case the 'from' attribute in the msg would not be
    // set, and it would be difficult to rootcause the reason why gas and nonce checks would fail
    if (!ethTx.isSigned)
      throw new IllegalArgumentException(s"Transaction is not signed: ${ethTx.id}")

    val txHash = BytesUtils.fromHexString(ethTx.id)
    val msg = ethTx.asMessage(blockContext.baseFee)

    // Tx context for stateDB, to know where to keep EvmLogs
    setupTxContext(txHash, txIndex)

    log.debug(s"applying msg: used pool gas ${blockGasPool.getUsedGas}")
    // apply message to state
    val status =
      try {
        applyMessage(msg, blockGasPool, blockContext)
        ReceiptStatus.SUCCESSFUL
      } catch {
        // any other exception will bubble up and invalidate the block
        case err: ExecutionFailedException =>
          log.error(s"applying message failed, tx.id=${ethTx.id}", err)
          ReceiptStatus.FAILED
      } finally {
        // finalize pending changes, clear the journal and reset refund counter
        stateDb.finalizeChanges()
      }
    val consensusDataReceipt = new EthereumConsensusDataReceipt(
      ethTx.version(),
      status.id,
      blockGasPool.getUsedGas,
      getLogs(txHash)
    )
    log.debug(s"Returning consensus data receipt: ${consensusDataReceipt.toString()}")
    log.debug(s"applied msg: used pool gas ${blockGasPool.getUsedGas}")

    consensusDataReceipt
  }

  override def isEoaAccount(address: Address): Boolean =
    stateDb.isEoaAccount(address)

  override def isSmartContractAccount(address: Address): Boolean =
    stateDb.isSmartContractAccount(address)

  override def accountExists(address: Address): Boolean =
    !stateDb.isEmpty(address)

  // account modifiers:
  override def addAccount(address: Address, code: Array[Byte]): Unit =
    stateDb.setCode(address, code)

  override def increaseNonce(address: Address): Unit =
    stateDb.setNonce(address, getNonce(address).add(BigInteger.ONE))

  @throws(classOf[ExecutionFailedException])
  override def addBalance(address: Address, amount: BigInteger): Unit = {
    amount.signum() match {
      case x if x == 0 => // amount is zero
      case x if x < 0 =>
        throw new ExecutionFailedException("cannot add negative amount to balance")
      case _ =>
        stateDb.addBalance(address, amount)
    }
  }

  @throws(classOf[ExecutionFailedException])
  override def subBalance(address: Address, amount: BigInteger): Unit = {
    // stateDb lib does not do any sanity check, and negative balances might arise (and java/go json IF does not correctly handle it)
    amount.signum() match {
      case x if x == 0 => // amount is zero, do nothing
      case x if x < 0 =>
        throw new ExecutionFailedException("cannot subtract negative amount from balance")
      case _ =>
        // The check on the address balance to be sufficient to pay the amount at this point has already been
        // done by the state while validating the origin tx
        stateDb.subBalance(address, amount)
    }
  }

  override def getAccountStorage(address: Address, key: Array[Byte]): Array[Byte] =
    stateDb.getStorage(address, new Hash(key)).toBytes

  override def updateAccountStorage(address: Address, key: Array[Byte], value: Array[Byte]): Unit =
    stateDb.setStorage(address, new Hash(key), new Hash(value))

  final override def removeAccountStorage(address: Address, key: Array[Byte]): Unit =
    updateAccountStorage(address, key, Hash.ZERO.toBytes)

  // random data used to salt chunk keys in the storage trie when accessed via get/updateAccountStorageBytes
  private val chunkKeySalt =
    BytesUtils.fromHexString("fa09428dd8121ea57327c9f21af74ffad8bfd5e6e39dc3dc6c53241a85ec5b0d")

  // chunk keys are generated by hashing a salt, the original key and the chunk index
  // the salt was added to reduce the risk of accidental hash collisions because similar strategies
  // to generate storage keys might be used by the caller
  private def getChunkKey(key: Array[Byte], chunkIndex: Int): Array[Byte] =
    Keccak256.hash(chunkKeySalt, key, BigIntegerUtil.toUint256Bytes(BigInteger.valueOf(chunkIndex)))

  final override def getAccountStorageBytes(address: Address, key: Array[Byte]): Array[Byte] = {
    val length = new BigInteger(1, getAccountStorage(address, key)).intValueExact()
    val data = new Array[Byte](length)
    for (chunkIndex <- 0 until (length + Hash.LENGTH - 1) / Hash.LENGTH) {
      getAccountStorage(address, getChunkKey(key, chunkIndex)).copyToArray(data, chunkIndex * Hash.LENGTH)
    }
    data
  }

  final override def updateAccountStorageBytes(address: Address, key: Array[Byte], value: Array[Byte]): Unit = {
    // get previous length of value stored, if any
    val oldLength = new BigInteger(1, getAccountStorage(address, key)).intValueExact()
    // values are split up into 32-bytes chunks:
    // the length of the value is stored at the original key and the chunks are stored at hash(key, i)
    val newLength = value.length
    // if the new value is empty remove all key-value pairs, including the one holding the value length
    updateAccountStorage(address, key, BigIntegerUtil.toUint256Bytes(BigInteger.valueOf(newLength)))
    for (start <- 0 until Math.max(newLength, oldLength) by Hash.LENGTH) {
      val chunkIndex = start / Hash.LENGTH
      val chunkKey = getChunkKey(key, chunkIndex)
      if (start < newLength) {
        // (over-)write chunks
        updateAccountStorage(address, chunkKey, value.slice(start, start + Hash.LENGTH).padTo(Hash.LENGTH, 0.toByte))
      } else {
        // remove previous chunks that are not needed anymore
        removeAccountStorage(address, chunkKey)
      }
    }
  }

  final override def removeAccountStorageBytes(address: Address, key: Array[Byte]): Unit =
    updateAccountStorageBytes(address, key, Array.empty)

  def getProof(address: Address, keys: Array[Array[Byte]]): ProofAccountResult =
    stateDb.getProof(address, keys.map(new Hash(_)))

  // account specific getters
  override def getNonce(address: Address): BigInteger = stateDb.getNonce(address)

  override def getBalance(address: Address): BigInteger = stateDb.getBalance(address)

  override def getCodeHash(address: Address): Array[Byte] = stateDb.getCodeHash(address).toBytes

  override def getCode(address: Address): Array[Byte] = stateDb.getCode(address)

  override def getLogs(txHash: Array[Byte]): Array[EthereumConsensusDataLog] =
    stateDb.getLogs(new Hash(txHash)).map(EthereumConsensusDataLog.apply)

  override def addLog(log: EthereumConsensusDataLog): Unit =
    stateDb.addLog(new EvmLog(log.address, log.topics, log.data))

  // when a method is called on a closed handle, LibEvm throws an exception
  override def close(): Unit = stateDb.close()

  override def getStateDbHandle: ResourceHandle = stateDb

  override def getIntermediateRoot: Array[Byte] = stateDb.getIntermediateRoot.toBytes

  // set context for the created events/logs assignment
  def setupTxContext(txHash: Array[Byte], idx: Integer): Unit = stateDb.setTxContext(new Hash(txHash), idx)

  // reset and prepare account access list
  def setupAccessList(msg: Message): Unit = stateDb.accessSetup(msg.getFrom, msg.getTo.orElse(Address.ZERO))

  def getRefund: BigInteger = stateDb.getRefund

  def snapshot: Int = stateDb.snapshot()

  def finalizeChanges(): Unit = stateDb.finalizeChanges()

  def revertToSnapshot(revisionId: Int): Unit = stateDb.revertToSnapshot(revisionId)

  override def getGasTrackedView(gas: GasPool): BaseAccountStateView =
    new StateDbAccountStateViewGasTracked(stateDb, messageProcessors, gas)
}
