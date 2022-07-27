package com.horizen.account.storage

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.receipt.{EthereumReceipt, EthereumReceiptSerializer}
import com.horizen.account.storage.AccountStateMetadataStorageView.DEFAULT_ACCOUNT_STATE_ROOT
import com.horizen.block.{WithdrawalEpochCertificate, WithdrawalEpochCertificateSerializer}
import com.horizen.consensus.{ConsensusEpochNumber, intToConsensusEpochNumber}
import com.horizen.storage.Storage
import com.horizen.utils.{BlockFeeInfo, ByteArrayWrapper, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer, Pair => JPair, _}
import scorex.core._
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging

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

  private val undefinedBlockFeeInfoCounter: Int = -1

  private[horizen] var hasCeasedOpt: Option[Boolean] = None
  private[horizen] var withdrawalEpochInfoOpt: Option[WithdrawalEpochInfo] = None
  private[horizen] var topQualityCertificateOpt: Option[WithdrawalEpochCertificate] = None
  private[horizen] var blockFeeInfoOpt: Option[BlockFeeInfo] = None
  private[horizen] var consensusEpochOpt: Option[ConsensusEpochNumber] = None
  private[horizen] var accountStateRootOpt: Option[Array[Byte]] = None
  private[horizen] var receiptsOpt: Option[Seq[EthereumReceipt]] = None
  private[horizen] var listOfTransactionsIds: Option[Seq[scorex.util.ModifierId]] = None
  private[horizen] var currentBlockNumber: Int = 0

  // all getters same as in StateMetadataStorage, but looking first in the cached/dirty entries in memory

  override def getWithdrawalEpochInfo: WithdrawalEpochInfo = {
    withdrawalEpochInfoOpt match {
      case None => getWithdrawalEpochInfoFromStorage.getOrElse(WithdrawalEpochInfo(0, 0))
      case Some(res) => res
    }
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

  override def getFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = {
    val blockFees: ListBuffer[BlockFeeInfo] = ListBuffer()
    val lastCounter = getBlockFeeInfoCounter(withdrawalEpochNumber)
    for (counter <- 0 to lastCounter) {
      storage.get(getBlockFeeInfoKey(withdrawalEpochNumber, counter)).asScala match {
        case Some(baw) => BlockFeeInfoSerializer.parseBytesTry(baw.data) match {
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

    withdrawalEpochInfoOpt.foreach(epochInfo => {
      if (epochInfo.epoch == withdrawalEpochNumber) {
        blockFeeInfoOpt.foreach(blockFeeInfo => blockFees.append(blockFeeInfo))
      }
    })
    blockFees
  }


  override def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
    topQualityCertificateOpt match {
      case Some(certificate) if certificate.epochNumber == referencedWithdrawalEpoch => topQualityCertificateOpt
      case _ => getTopQualityCertificateFromStorage(referencedWithdrawalEpoch)
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

  override def getConsensusEpochNumber: Option[ConsensusEpochNumber] = {
    consensusEpochOpt match {
      case Some(_) => consensusEpochOpt
      case _ => getConsensusEpochNumberFromStorage
    }
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

  override def hasCeased: Boolean = {
    hasCeasedOpt.getOrElse(storage.get(ceasingStateKey).isPresent)
  }

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

  def updateTransactionReceipts(receipts: Seq[EthereumReceipt]): Unit = {
    receiptsOpt = Some(receipts)
  }

  def setBlockNumberForTransactions(blockNumber: Int, listOfTransactionIds: Seq[scorex.util.ModifierId]): Unit = {
    require(blockNumber > 0, s"Invalid block number: $blockNumber")
    this.currentBlockNumber = blockNumber
    this.listOfTransactionsIds = Some(listOfTransactionIds)
  }

  override def getTransactionBlockNumber(txId: scorex.util.ModifierId): Option[Int] = {
    listOfTransactionsIds match {
      case Some(txList) if txList.contains(txId)  => Some(currentBlockNumber)
      case _ => getTransactionBlockNumberFromStorage(txId)
    }
  }

  private[horizen] def getTransactionBlockNumberFromStorage(txId: scorex.util.ModifierId): Option[Int] = {
    storage.get(getTransactionBlockNumberKey(txId)).asScala match {
      case Some(serData) =>
        val blockNumber  = Ints.fromByteArray(serData)
        Some(blockNumber)

      case None => None
    }
  }

  def addFeePayment(blockFeeInfo: BlockFeeInfo): Unit = {
    blockFeeInfoOpt = Some(blockFeeInfo)
  }

  def updateConsensusEpochNumber(consensusEpochNum: ConsensusEpochNumber): Unit = {
    consensusEpochOpt = Some(consensusEpochNum)
  }

  def updateAccountStateRoot(accountStateRoot: Array[Byte]): Unit = {
    accountStateRootOpt = Some(accountStateRoot)
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
      case Success(_) =>
    }
  }

  private[horizen] def cleanUpCache(): Unit = {
    hasCeasedOpt = None
    withdrawalEpochInfoOpt = None
    topQualityCertificateOpt = None
    blockFeeInfoOpt = None
    consensusEpochOpt = None
    accountStateRootOpt = None
    receiptsOpt = None
    currentBlockNumber = 0
    listOfTransactionsIds = None
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

    blockFeeInfoOpt.foreach(feeInfo => {
      val epochInfo = withdrawalEpochInfoOpt.getOrElse(getWithdrawalEpochInfo)
      val nextBlockFeeInfoCounter: Int = getBlockFeeInfoCounter(epochInfo.epoch) + 1

      updateList.add(new JPair(getBlockFeeInfoCounterKey(epochInfo.epoch),
        new ByteArrayWrapper(Ints.toByteArray(nextBlockFeeInfoCounter))))

      updateList.add(new JPair(getBlockFeeInfoKey(epochInfo.epoch, nextBlockFeeInfoCounter),
        new ByteArrayWrapper(BlockFeeInfoSerializer.toBytes(feeInfo))))
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
    // TODO improve this: we are assuming that the saveToStorage() is call on a per-block base, this is an exception, is it the only one?
    if (!version.equals(new ByteArrayWrapper(Utils.ZEROS_HASH))) {
      val nextHeight = getHeight + 1
      updateList.add(new JPair(heightKey, new ByteArrayWrapper(Ints.toByteArray(nextHeight))))
    }

    // If withdrawal epoch switched to the next one, then perform some database clean-up:
    // 1) remove outdated topQualityCertificate retrieved 3 epochs before and referenced to the 4 epochs before.
    //    Note: we should keep last 2 epoch certificates, so in case SC has ceased we have an access to the last active cert.
    // 1) remove outdated BlockFeeInfo records

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

    listOfTransactionsIds.foreach(ids => {
      val blockNum = new ByteArrayWrapper(Ints.toByteArray(currentBlockNumber))
      ids.foreach( id => {
        val key = getTransactionBlockNumberKey(id)
        updateList.add(new JPair(key, blockNum))
      })
    })

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

  private[horizen] def getTransactionBlockNumberKey(txId : scorex.util.ModifierId): ByteArrayWrapper = {
    calculateKey(Bytes.concat("txblock".getBytes, idToBytes(txId)))
  }


}

object AccountStateMetadataStorageView {
  val DEFAULT_ACCOUNT_STATE_ROOT: Array[Byte] = new Array[Byte](32)
}

