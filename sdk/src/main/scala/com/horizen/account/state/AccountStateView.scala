package com.horizen.account.state

import com.google.common.primitives.Bytes
import com.horizen.SidechainTypes
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.state.ForgerStakeMsgProcessor.{AddNewStakeCmd, ForgerStakeSmartContractAddress}
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.account.transaction.EthereumTransaction
import com.horizen.account.utils.ZenWeiConverter
import com.horizen.block.{MainchainBlockReferenceData, MainchainTxForwardTransferCrosschainOutput, MainchainTxSidechainCreationCrosschainOutput, WithdrawalEpochCertificate}
import com.horizen.box.data.WithdrawalRequestBoxData
import com.horizen.box.{ForgerBox, WithdrawalRequestBox}
import com.horizen.consensus.{ConsensusEpochNumber, ForgingStakeInfo}
import com.horizen.evm.{StateDB, StateStorageStrategy}
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.state.StateView
import com.horizen.transaction.mainchain.{ForwardTransfer, SidechainCreation}
import com.horizen.utils.{BlockFeeInfo, BytesUtils, WithdrawalEpochInfo}
import scorex.core.VersionTag
import scorex.util.ScorexLogging

import java.math.BigInteger
import scala.collection.JavaConverters.collectionAsScalaIterableConverter
import scala.util.Try

class AccountStateView(val metadataStorageView: AccountStateMetadataStorageView,
                       val stateDb: StateDB,
                       messageProcessors: Seq[MessageProcessor])
  extends StateView[SidechainTypes#SCAT, AccountStateView]
    with AccountStateReader
    with AutoCloseable
    with ScorexLogging {

  view: AccountStateView =>

  override type NVCT = this.type

  lazy val withdrawalReqProvider: WithdrawalRequestProvider = messageProcessors.find(_.isInstanceOf[WithdrawalRequestProvider]).get.asInstanceOf[WithdrawalRequestProvider]
  lazy val forgerStakesProvider: ForgerStakesProvider = messageProcessors.find(_.isInstanceOf[ForgerStakesProvider]).get.asInstanceOf[ForgerStakesProvider]

  // modifiers
  override def applyMainchainBlockReferenceData(refData: MainchainBlockReferenceData): Try[Unit] = Try {

    refData.sidechainRelatedAggregatedTransaction.foreach(aggTx => {
      aggTx.mc2scTransactionsOutputs().asScala.map {
        case sc: SidechainCreation =>
          // While processing sidechain creation output:
          // 1. extract first forger stake info: block sign public key, vrf public key, owner address, stake amount
          // 2. store the stake info record in the forging fake smart contract storage
          val scOut: MainchainTxSidechainCreationCrosschainOutput = sc.getScCrOutput

          val stakedAmount = ZenWeiConverter.convertZenniesToWei(scOut.amount)

          // we must get 20 bytes out of 32 with the proper padding and byte order
          // MC prepends a padding of 0 bytes (if needed) in the sc_create command when a 32 bytes address is specified.
          // After reversing the bytes, the padding is trailed to the correct 20 bytes proposition
          val ownerAddressProposition = new AddressProposition(BytesUtils.reverseBytes(scOut.address.take(com.horizen.account.utils.Account.ADDRESS_SIZE)))

          // customData = vrf key | blockSignerKey
          val vrfPublicKey = new VrfPublicKey(scOut.customCreationData.take(VrfPublicKey.KEY_LENGTH))
          val blockSignerProposition = new PublicKey25519Proposition(scOut.customCreationData.slice(VrfPublicKey.KEY_LENGTH, VrfPublicKey.KEY_LENGTH + PublicKey25519Proposition.KEY_LENGTH))

          val cmdInput = AddNewStakeCmdInput(
            ForgerPublicKeys(blockSignerProposition, vrfPublicKey),
            ownerAddressProposition,
          )

          val data: Array[Byte] = Bytes.concat(
            BytesUtils.fromHexString(AddNewStakeCmd),
            AddNewStakeCmdInputSerializer.toBytes(cmdInput))

          val message = new Message(
            ownerAddressProposition,
            ForgerStakeSmartContractAddress,
            BigInteger.ZERO, // gasPrice
            BigInteger.ZERO, // gasFeeCap
            BigInteger.ZERO, // gasTipCap
            BigInteger.ZERO, // gasLimit
            stakedAmount,
            BigInteger.ONE.negate(), // a negative nonce value will rule out collision with real transactions
            data)

          forgerStakesProvider.addScCreationForgerStake(message, view) match {
            case res: ExecutionFailed =>
              log.error(res.getReason.getMessage)
              throw new IllegalArgumentException(res.getReason)
            case res: InvalidMessage =>
              log.error(res.getReason.getMessage)
              throw new IllegalArgumentException(res.getReason)
            case res: ExecutionSucceeded =>
              log.debug(s"sc creation forging stake added with stakeid: ${BytesUtils.toHexString(res.returnData())}")
          }

        case ft: ForwardTransfer =>
          val ftOut: MainchainTxForwardTransferCrosschainOutput = ft.getFtOutput

          // we trust the MC that this is a valid amount
          val value = ZenWeiConverter.convertZenniesToWei(ftOut.amount)

          // we must get 20 bytes out of 32 with the proper padding and byte order
          // MC prepends a padding of 0 bytes (if needed) in the sc_create command when a 32 bytes address is specified.
          // After reversing the bytes, the padding is trailed to the correct 20 bytes proposition
          val recipientProposition = new AddressProposition(BytesUtils.reverseBytes(ftOut.propositionBytes.take(com.horizen.account.utils.Account.ADDRESS_SIZE)))

          // stateDb will implicitly create account if not existing yet
          view.addBalance(recipientProposition.address(), value)
          log.debug(s"added FT amount = $value to address=$recipientProposition")
      }
    })
  }

  def getOrderedForgingStakeInfoSeq : Seq[ForgingStakeInfo] = {

    val forgerStakeList = forgerStakesProvider.getListOfForgers(this)

    forgerStakeList.map {
      item => ForgingStakeInfo(
        item.forgerStakeData.forgerPublicKeys.blockSignPublicKey,
        item.forgerStakeData.forgerPublicKeys.vrfPublicKey,
        ZenWeiConverter.convertWeiToZennies(item.forgerStakeData.stakedAmount))
    }.sorted(Ordering[ForgingStakeInfo].reverse)
  }


  def setupTxContext(tx: EthereumTransaction): Unit = {
    // TODO
  }

  override def applyTransaction(tx: SidechainTypes#SCAT): Try[AccountStateView] = Try {
    if (tx.isInstanceOf[EthereumTransaction]) {
      val ethTx = tx.asInstanceOf[EthereumTransaction]
      setupTxContext(ethTx)
      val message: Message = Message.fromTransaction(ethTx)
      val processor = messageProcessors.find(_.canProcess(message, this)).getOrElse(
        throw new IllegalArgumentException(s"Transaction ${ethTx.id} has no known processor.")
      )
      processor.process(message, this) match {
        case success: ExecutionSucceeded => this // TODO
        case failed: ExecutionFailed => this // TODO
        case invalid: InvalidMessage => throw new Exception(s"Transaction ${ethTx.id} is invalid.", invalid.getReason)
      }
    } else
      throw new IllegalArgumentException(s"Unsupported transaction type ${tx.getClass.getName}")
  }

  def isEoaAccount(address: Array[Byte]): Boolean = stateDb.isEoaAccount(address)

  def isSmartContractAccount(address: Array[Byte]): Boolean = stateDb.isSmartContractAccount(address)

  def accountExists(address: Array[Byte]): Boolean = !stateDb.isEmpty(address)

  // account modifiers:
  def addAccount(address: Array[Byte], codeHash: Array[Byte]): Try[Unit] = Try {
    stateDb.setCodeHash(address, codeHash)
  }

  def addBalance(address: Array[Byte], amount: BigInteger): Try[Unit] = Try {
    stateDb.addBalance(address, amount)
  }

  def subBalance(address: Array[Byte], amount: BigInteger): Try[Unit] = Try {
    // stateDb lib does not do any sanity check, and negative balances might arise (and java/go json IF does not correctly handle it)
    // TODO: for the time being do the checks here, later they will be done in the caller stack
    require(amount.compareTo(BigInteger.ZERO) >= 0)
    val balance = stateDb.getBalance(address)
    require(balance.compareTo(amount) >= 0)

    stateDb.subBalance(address, amount)
  }

  protected def updateAccountStorageRoot(address: Array[Byte], root: Array[Byte]): Try[AccountStateView] = ???

  def updateAccountStorage(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit] = Try {
    stateDb.setStorage(address, key, value, StateStorageStrategy.RAW)
  }

  def updateAccountStorageBytes(address: Array[Byte], key: Array[Byte], value: Array[Byte]): Try[Unit] = Try {
    stateDb.setStorage(address, key, value, StateStorageStrategy.CHUNKED)
  }

  def getAccountStorage(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]] =
    Try {
      stateDb.getStorage(address, key, StateStorageStrategy.RAW)
    }

  def getAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Try[Array[Byte]] =
    Try {
      stateDb.getStorage(address, key, StateStorageStrategy.CHUNKED)
    }

  def removeAccountStorage(address: Array[Byte], key: Array[Byte]): Try[Unit] =
    Try {
      stateDb.removeStorage(address, key, StateStorageStrategy.RAW)
    }

  def removeAccountStorageBytes(address: Array[Byte], key: Array[Byte]): Try[Unit] =
    Try {
      stateDb.removeStorage(address, key, StateStorageStrategy.CHUNKED)
    }

  // log handling
  // def addLog(log: EvmLog) : Try[Unit] = ???

  // out-of-the-box helpers
  override def addCertificate(cert: WithdrawalEpochCertificate): Try[AccountStateView] = Try {
    metadataStorageView.updateTopQualityCertificate(cert)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  override def delegateStake(fb: ForgerBox): Try[AccountStateView] = ???

  override def spendStake(fb: ForgerBox): Try[AccountStateView] = ???

  // note: probably must be "set" than "add". Because we allow it only once per "commit".
  override def addFeeInfo(info: BlockFeeInfo): Try[AccountStateView] = Try {
    metadataStorageView.addFeePayment(info)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  override def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Try[AccountStateView] = Try {
    metadataStorageView.updateWithdrawalEpochInfo(withdrawalEpochInfo)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  override def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Try[AccountStateView] = Try {
    metadataStorageView.updateConsensusEpochNumber(consensusEpochNum)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  def updateAccountStateRoot(accountStateRoot: Array[Byte]): Try[AccountStateView] = Try {
    metadataStorageView.updateAccountStateRoot(accountStateRoot)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  override def setCeased(): Try[AccountStateView] = Try {
    metadataStorageView.setCeased()
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  // view controls
  override def savepoint(): Unit = ???

  override def rollbackToSavepoint(): Try[AccountStateView] = ???

  override def commit(version: VersionTag): Try[Unit] = Try {
    // Update StateDB without version, then set the rootHash and commit metadataStorageView
    val rootHash = stateDb.commit()
    metadataStorageView.updateAccountStateRoot(rootHash)
    metadataStorageView.commit(version)
  }

  // versions part
  override def version: VersionTag = ???

  override def maxRollbackDepth: Int = ???

  // getters
  override def withdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = {
    val listOfWr = withdrawalReqProvider.getListOfWithdrawalReqRecords(withdrawalEpoch, this)
    listOfWr.map(wr => {
      val boxData = new WithdrawalRequestBoxData(wr.proposition, ZenWeiConverter.convertWeiToZennies(wr.value))
      new WithdrawalRequestBox(boxData, 0)
    })
  }

  override def certificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch)
  }

  override def certificateTopQuality(referencedWithdrawalEpoch: Int): Long = {
    metadataStorageView.getTopQualityCertificate(referencedWithdrawalEpoch) match {
      case Some(certificate) => certificate.quality
      case None => 0
    }
  }

  // get the record of storage or return WithdrawalEpochInfo(0,0) if state is empty
  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    metadataStorageView.getWithdrawalEpochInfo
  }

  override def hasCeased: Boolean = metadataStorageView.hasCeased

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = metadataStorageView.getConsensusEpochNumber

  override def getBlockFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = {
    metadataStorageView.getFeePayments(withdrawalEpochNumber)
  }

  // account specific getters
  override def getAccount(address: Array[Byte]): Account = ???

  override def getBalance(address: Array[Byte]): Try[java.math.BigInteger] = Try {
    stateDb.getBalance(address)
  }


  override def getCodeHash(address: Array[Byte]): Array[Byte] = {
    stateDb.getCodeHash(address)
  }

  override def getAccountStateRoot: Option[Array[Byte]] = metadataStorageView.getAccountStateRoot

  def close() : Unit = {
    // when a method is called on a closed handle, LibEvm throws an exception
    stateDb.close()
  }

}
