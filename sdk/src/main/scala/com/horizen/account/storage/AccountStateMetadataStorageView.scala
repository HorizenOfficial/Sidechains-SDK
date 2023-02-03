package com.horizen.account.storage

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.receipt.{EthereumReceipt, EthereumReceiptSerializer}
import com.horizen.account.storage.AccountStateMetadataStorageView.DEFAULT_ACCOUNT_STATE_ROOT
import com.horizen.account.utils.{AccountBlockFeeInfo, AccountBlockFeeInfoSerializer, FeeUtils}
import com.horizen.block.SidechainBlockBase.GENESIS_BLOCK_PARENT_ID
import com.horizen.block.{WithdrawalEpochCertificate, WithdrawalEpochCertificateSerializer}
import com.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import com.horizen.storage.Storage
import com.horizen.utils.{ByteArrayWrapper, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer, Pair => JPair, _}
import scorex.crypto.hash.Blake2b256
import scorex.util.{ModifierId, ScorexLogging, bytesToId, idToBytes}
import sparkz.core.{VersionTag, versionToBytes}
import java.math.BigInteger
import java.util.{ArrayList => JArrayList}
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters._
import scala.util.{Failure, Success, Try}


class AccountStateMetadataStorageView(storage: Storage) extends AccountStateMetadataStorageReader with ScorexLogging {

  require(storage != null, "Storage must be NOT NULL.")

  private[horizen] val ceasingStateKey = calculateKey("ceasingStateKey".getBytes)
  private[horizen] val heightKey = calculateKey("heightKey".getBytes)
  private[horizen] val withdrawalEpochInformationKey = calculateKey("withdrawalEpochInformation".getBytes)
  private[horizen] val consensusEpochKey = calculateKey("consensusEpoch".getBytes)
  private[horizen] val accountStateRootKey = calculateKey("accountStateRoot".getBytes)
  private[horizen] val baseFeeKey = calculateKey("baseFee".getBytes)

  private[horizen] def topCertificateMainchainHasheKey(referencedWithdrawalEpoch: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("topCertificateMainchainHash".getBytes, Ints.toByteArray(referencedWithdrawalEpoch)))
  }

  private val undefinedBlockFeeInfoCounter: Int = -1

  private[horizen] var hasCeasedOpt: Option[Boolean] = None

  private[horizen] var withdrawalEpochInfoOpt: Option[WithdrawalEpochInfo] = None
  private[horizen] var topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = None
  private[horizen] var lastCertificateReferencedEpochOpt: Option[Int] = None
  private[horizen] var lastCertificateSidechainBlockIdOpt: Option[ModifierId] = None
  private[horizen] var blockFeeInfoOpt: Option[AccountBlockFeeInfo] = None
  private[horizen] var consensusEpochOpt: Option[ConsensusEpochNumber] = None
  private[horizen] var accountStateRootOpt: Option[Array[Byte]] = None
  private[horizen] var receiptsOpt: Option[Seq[EthereumReceipt]] = None
  private[horizen] var topCertificateMainchainHashes: Map[Int, Array[Byte]] = Map()
  //Contains the base fee to be used when forging the next block
  private[horizen] var nextBaseFeeOpt: Option[BigInteger] = None

  // all getters same as in StateMetadataStorage, but looking first in the cached/dirty entries in memory

  def addTopCertificateMainchainHash(hash: Int, mainchainHash: Array[Byte]) = {
    topCertificateMainchainHashes = topCertificateMainchainHashes + (hash -> mainchainHash)
  }

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    withdrawalEpochInfoOpt.orElse(getWithdrawalEpochInfoFromStorage).getOrElse(WithdrawalEpochInfo(0, 0))
  }

  private[horizen] def getWithdrawalEpochInfoFromStorage: Option[WithdrawalEpochInfo] = {
    storage.get(withdrawalEpochInformationKey).asScala match {
      case Some(baw) =>
        WithdrawalEpochInfoSerializer.parseBytesTry(baw.data) match {
          case Success(withdrawalEpochInfo) => Option(withdrawalEpochInfo)
          case Failure(exception) =>
            log.error("Error while withdrawal epoch info information parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  override def getFeePayments(withdrawalEpochNumber: Int): Seq[AccountBlockFeeInfo] = {
    val blockFees: ListBuffer[AccountBlockFeeInfo] = ListBuffer()
    val lastCounter = getBlockFeeInfoCounter(withdrawalEpochNumber)
    for (counter <- 0 to lastCounter) {
      storage.get(getBlockFeeInfoKey(withdrawalEpochNumber, counter)).asScala match {
        case Some(baw) => AccountBlockFeeInfoSerializer.parseBytesTry(baw.data) match {
          case Success(info) => blockFees.append(info)
          case Failure(exception) =>
            log.error("Error while fee payment parsing.", exception)
            throw new IllegalStateException("Error while fee payment parsing.", exception)

        }
        case None =>
          log.error(s"Error while fee payments retrieving: record expected to exist for epoch $withdrawalEpochNumber and counter $counter")
          throw new IllegalStateException("Error while fee payments retrieving: record expected to exist.")
      }
    }

    if (getWithdrawalEpochInfo.epoch == withdrawalEpochNumber) {
      blockFeeInfoOpt.foreach(blockFeeInfo => blockFees.append(blockFeeInfo))
    }

    blockFees
  }

  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    topQualityCertificateOpt match {
      case Some(certificate) if certificate.epochNumber == referencedWithdrawalEpoch => topQualityCertificateOpt
      case _ => getTopQualityCertificateFromStorage(referencedWithdrawalEpoch)
    }
  }

  override def lastCertificateReferencedEpoch: Option[Int] = {
    lastCertificateReferencedEpochOpt.orElse(lastCertificateReferencedEpochFromStorage)
  }

  override def getTopCertificateMainchainHash(referencedWithdrawalEpoch: Int): Option[Array[Byte]] = {
    topCertificateMainchainHashes.get(referencedWithdrawalEpoch).orElse(getTopCertificateMainchainHashesFromStorage(referencedWithdrawalEpoch))
  }

  private[horizen] def lastCertificateReferencedEpochFromStorage: Option[Int] = {
    storage.get(getLastCertificateEpochNumberKey).asScala
      .flatMap { baw =>
        Try {
          Ints.fromByteArray(baw.data)
        } match {
          case Success(epoch) => Some(epoch)
          case Failure(exception) =>
            log.error("Error while last certificate referenced epoch parsing.", exception)
            Option.empty
        }
      }
  }


  override def lastCertificateSidechainBlockId: Option[ModifierId] = {
    lastCertificateSidechainBlockIdOpt.orElse(lastCertificateSidechainBlockIdFromStorage)
  }

  private[horizen] def lastCertificateSidechainBlockIdFromStorage: Option[ModifierId] = {
    storage.get(lastCertificateSidechainBlockIdKey).asScala
      .flatMap { baw =>
        Try {
          bytesToId(baw.data())
        } match {
          case Success(blockId) => Some(blockId)
          case Failure(exception) =>
            log.error("Error while last certificate sidechain block id parsing.", exception)
            Option.empty
        }
      }
  }

    private[horizen] def getTopQualityCertificateFromStorage(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    storage.get(getTopQualityCertificateKey(referencedWithdrawalEpoch)).asScala match {
      case Some(baw) =>
        WithdrawalEpochCertificateSerializer.parseBytesTry(baw.data) match {
          case Success(certificate) => Option(certificate)
          case Failure(exception) =>
            log.error("Error while withdrawal epoch certificate information parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  private[horizen] def getTopCertificateMainchainHashesFromStorage(referencedWithdrawalEpoch: Int): Option[Array[Byte]] = {
    storage.get(topCertificateMainchainHasheKey(referencedWithdrawalEpoch)).asScala  match {
      case Some(baw) => Some(baw.data)
      case _ => Option.empty
    }
  }

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = {
    consensusEpochOpt.orElse(getConsensusEpochNumberFromStorage)
  }

  private[horizen] def getConsensusEpochNumberFromStorage: Option[ConsensusEpochNumber] = {
    storage.get(consensusEpochKey).asScala match {
      case Some(baw) =>
        Try {
          Ints.fromByteArray(baw.data)
        } match {
          case Success(epoch) => Some(intToConsensusEpochNumber(epoch))
          case Failure(exception) =>
            log.error("Error while consensus epoch information parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  override def hasCeased: Boolean = hasCeasedOpt.getOrElse(storage.get(ceasingStateKey).isPresent)

  override def getHeight: Int = {
    storage.get(heightKey).asScala.map(baw => Ints.fromByteArray(baw.data)).getOrElse(0)
  }

  private[horizen] def getAccountStateRootFromStorage: Option[Array[Byte]] = {
    storage.get(accountStateRootKey).asScala.map(_.data)
  }

  override def getAccountStateRoot: Array[Byte] = {
    accountStateRootOpt.orElse(getAccountStateRootFromStorage).getOrElse(DEFAULT_ACCOUNT_STATE_ROOT)
  }

  private[horizen] def getTransactionReceiptFromStorage(txHash: Array[Byte]): Option[EthereumReceipt] = {
    storage.get(getReceiptKey(txHash)).asScala match {
      case Some(serData) =>
        val decodedReceipt: EthereumReceipt = EthereumReceiptSerializer.parseBytes(serData)
        Some(decodedReceipt)

      case None => None
    }
  }

  override def getTransactionReceipt(txHash: Array[Byte]): Option[EthereumReceipt] = {
    val bawTxHash = new ByteArrayWrapper(txHash)
    receiptsOpt match {
      case Some(receipts) =>
        receipts.find(r => new ByteArrayWrapper(r.transactionHash) == bawTxHash)

      case None => getTransactionReceiptFromStorage(txHash)
    }
  }

  // put in memory cache and mark the entry as "dirty"
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit =
    withdrawalEpochInfoOpt = Some(withdrawalEpochInfo)

  def updateTopQualityCertificate(topQualityCertificate: WithdrawalEpochCertificate): Unit =
    topQualityCertificateOpt = Some(topQualityCertificate)
  def updateLastCertificateReferencedEpoch(lastCertificateReferencedEpoch: Int): Unit =
    lastCertificateReferencedEpochOpt = Some(lastCertificateReferencedEpoch)

  def updateLastCertificateSidechainBlockIdOpt(blockId: ModifierId): Unit = {
    lastCertificateSidechainBlockIdOpt = Some(blockId)
  }

  def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit = {
    receiptsOpt = Some(receipts)
  }

  def updateFeePaymentInfo(blockFeeInfo: AccountBlockFeeInfo): Unit = {
    blockFeeInfoOpt = Some(blockFeeInfo)
  }

  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit = {
    consensusEpochOpt = Some(consensusEpochNum)
  }

  def updateAccountStateRoot(accountStateRoot: Array[Byte]): Unit = {
    accountStateRootOpt = Some(accountStateRoot)
  }

  def updateNextBaseFee(baseFee: BigInteger): Unit = {
    nextBaseFeeOpt = Some(baseFee)
  }

  def getNextBaseFeeFromStorage: Option[BigInteger] = {
    storage.get(baseFeeKey).asScala.map(wrapper => new BigInteger(wrapper.data))
  }

  def getNextBaseFee: BigInteger = {
    nextBaseFeeOpt.orElse(getNextBaseFeeFromStorage).getOrElse(FeeUtils.INITIAL_BASE_FEE)
  }

  def setCeased(): Unit = hasCeasedOpt = Some(true)

  // update the database with "dirty" records new values
  // also increment the height value directly
  def commit(version: VersionTag): Unit = {
    val result = Try {
      saveToStorage(versionToBytes(version))
    }
    cleanUpCache()
    result match {
      case Failure(exception) =>
        log.error(s"Error while committing metadata for version $version. The modifications won't be saved", exception)
        throw exception
      case Success(_) =>
    }
  }

  private[horizen] def cleanUpCache(): Unit = {
    hasCeasedOpt = None
    withdrawalEpochInfoOpt = None
    topQualityCertificateOpt = None
    topCertificateMainchainHashes = Map()
    lastCertificateReferencedEpochOpt = None
    lastCertificateSidechainBlockIdOpt = None
    blockFeeInfoOpt = None
    consensusEpochOpt = None
    accountStateRootOpt = None
    receiptsOpt = None
    nextBaseFeeOpt = None
  }

  private[horizen] def saveToStorage(version: ByteArrayWrapper): Unit = {

    require(accountStateRootOpt.nonEmpty, "Account State Root must be NOT NULL.")

    val updateList = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    val removeList = new JArrayList[ByteArrayWrapper]()

    withdrawalEpochInfoOpt.foreach(epochInfo => {
      // Update Withdrawal epoch related data
      updateList.add(new JPair(withdrawalEpochInformationKey,
        new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(epochInfo))))
    })

    // Store the top quality cert for epoch if present
    topQualityCertificateOpt.foreach(certificate => {
      updateList.add(new JPair(getTopQualityCertificateKey(certificate.epochNumber),
        WithdrawalEpochCertificateSerializer.toBytes(certificate)))
    })


    // Store the last certificate referenced epoch if present
    lastCertificateReferencedEpochOpt.foreach(epoch => {
      updateList.add(new JPair(getLastCertificateEpochNumberKey, Ints.toByteArray(epoch)))
    })

    // Store the last certificate containing sidechain block id
    lastCertificateSidechainBlockIdOpt.foreach(blockId => {
      updateList.add(new JPair(lastCertificateSidechainBlockIdKey, idToBytes(blockId)))
    })

    blockFeeInfoOpt.foreach(feeInfo => {
      val epochInfo = withdrawalEpochInfoOpt.getOrElse(getWithdrawalEpochInfo)
      val nextBlockFeeInfoCounter: Int = getBlockFeeInfoCounter(epochInfo.epoch) + 1

      updateList.add(new JPair(getBlockFeeInfoCounterKey(epochInfo.epoch),
        new ByteArrayWrapper(Ints.toByteArray(nextBlockFeeInfoCounter))))

      updateList.add(new JPair(getBlockFeeInfoKey(epochInfo.epoch, nextBlockFeeInfoCounter),
        new ByteArrayWrapper(AccountBlockFeeInfoSerializer.toBytes(feeInfo))))
      }
    )

    // Update Consensus related data
    consensusEpochOpt.foreach(currConsensusEpoch => {
      if (getConsensusEpochNumberFromStorage.getOrElse(intToConsensusEpochNumber(0)) != currConsensusEpoch)
        updateList.add(new JPair(consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(currConsensusEpoch))))
    })

    updateList.add(new JPair(accountStateRootKey, new ByteArrayWrapper(accountStateRootOpt.get)))

    // If sidechain has ceased set the flag
    hasCeasedOpt.foreach(_ => updateList.add(new JPair(ceasingStateKey, new ByteArrayWrapper(Array.emptyByteArray))))

    // update the height unless we have the very first version of the db
    //--
    // We are assuming that the saveToStorage() is called on a per-block base.
    // The only exception is in the Sidechain initialization phase, where a commit takes place using as a
    // version `genesisBlock.parentId`, which btw is defined as the empty byte array.
    if (!version.equals(new ByteArrayWrapper(GENESIS_BLOCK_PARENT_ID))) {
      val nextHeight = getHeight + 1
      updateList.add(new JPair(heightKey, new ByteArrayWrapper(Ints.toByteArray(nextHeight))))
    }

    // If withdrawal epoch switched to the next one, then perform some database clean-up:
    // 1) remove outdated topQualityCertificate retrieved 3 epochs before and referenced to the 4 epochs before.
    //    Note: we should keep last 2 epoch certificates, so in case SC has ceased we have an access to the last active cert.
    // 2) remove outdated AccountBlockFeeInfo records, the relevant forger fee payments have been stored in history by node view holder
    withdrawalEpochInfoOpt match {
      case Some(epochInfo) =>
        val isWithdrawalEpochSwitched: Boolean = getWithdrawalEpochInfoFromStorage match {
          case Some(storedEpochInfo) => storedEpochInfo.epoch != epochInfo.epoch
          case _ => false
        }
        if (isWithdrawalEpochSwitched) {
          val certEpochNumberToRemove: Int = epochInfo.epoch - 4
          removeList.add(getTopQualityCertificateKey(certEpochNumberToRemove))

          val blockFeeInfoEpochToRemove: Int = epochInfo.epoch - 1
          for (counter <- 0 to getBlockFeeInfoCounter(blockFeeInfoEpochToRemove)) {
            removeList.add(getBlockFeeInfoKey(blockFeeInfoEpochToRemove, counter))
          }
          removeList.add(getBlockFeeInfoCounterKey(blockFeeInfoEpochToRemove))
        }
      case _ => // do nothing
    }

    receiptsOpt.foreach(receipts => {
        for (r <- receipts) {
          val key = getReceiptKey(r.transactionHash)
          val value = new ByteArrayWrapper(EthereumReceiptSerializer.toBytes(r))
          updateList.add(new JPair(key, value))
        }
    })

    nextBaseFeeOpt.foreach(baseFee => updateList.add(new JPair(baseFeeKey, new ByteArrayWrapper(baseFee.toByteArray))))

    topCertificateMainchainHashes.foreach(ele =>
      updateList.add(new JPair(topCertificateMainchainHasheKey(ele._1), ele._2))
    )

    storage.update(version, updateList, removeList)

  }


  private def getBlockFeeInfoCounter(withdrawalEpochNumber: Int): Int = {
    storage.get(getBlockFeeInfoCounterKey(withdrawalEpochNumber)).asScala match {
      case Some(baw) =>
        Try {
          Ints.fromByteArray(baw.data)
        }.getOrElse(undefinedBlockFeeInfoCounter)
      case _ => undefinedBlockFeeInfoCounter
    }
  }


  def calculateKey(key: Array[Byte]): ByteArrayWrapper = {
    new ByteArrayWrapper(Blake2b256.hash(key))
  }

  private[horizen] def getTopQualityCertificateKey(referencedWithdrawalEpoch: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("topQualityCertificate".getBytes, Ints.toByteArray(referencedWithdrawalEpoch)))
  }

  private[horizen] def getBlockFeeInfoCounterKey(withdrawalEpochNumber: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("blockFeeInfoCounter".getBytes, Ints.toByteArray(withdrawalEpochNumber)))
  }

  private[horizen] def getBlockFeeInfoKey(withdrawalEpochNumber: Int, counter: Int): ByteArrayWrapper = {
    calculateKey(Bytes.concat("blockFeeInfo".getBytes, Ints.toByteArray(withdrawalEpochNumber), Ints.toByteArray(counter)))
  }

  private[horizen] def getReceiptKey(txHash : Array[Byte]): ByteArrayWrapper = {
    calculateKey(Bytes.concat("receipt".getBytes, txHash))
  }

  private[horizen] val getLastCertificateEpochNumberKey: ByteArrayWrapper = calculateKey("lastCertificateEpochNumber".getBytes)

  private [horizen] val lastCertificateSidechainBlockIdKey: ByteArrayWrapper = calculateKey("lastCertificateSidechainBlockId".getBytes)

}

object AccountStateMetadataStorageView {
  val DEFAULT_ACCOUNT_STATE_ROOT: Array[Byte] = new Array[Byte](32)
}

