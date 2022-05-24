package com.horizen.account.storage

import com.google.common.primitives.{Bytes, Ints}
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

  // all getters same as in StateMetadataStorage, but looking first in the cached/dirty entries in memory

  override def getWithdrawalEpochInfo: Option[WithdrawalEpochInfo] = {
    withdrawalEpochInfoOpt match {
      case None => getWithdrawalEpochInfoFromStorage
      case Some(_) => withdrawalEpochInfoOpt
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

  override def getAccountStateRoot: Option[Array[Byte]] = {
    accountStateRootOpt match {
      case Some(_) => accountStateRootOpt
      case _ => getAccountStateRootFromStorage
    }
  }


  // put in memory cache and mark the entry as "dirty"
  def updateWithdrawalEpochInfo(withdrawalEpochInfo: WithdrawalEpochInfo): Unit =
    withdrawalEpochInfoOpt = Some(withdrawalEpochInfo)

  def updateTopQualityCertificate(topQualityCertificate: WithdrawalEpochCertificate): Unit =
    topQualityCertificateOpt = Some(topQualityCertificate)

  def addFeePayment(blockFeeInfo: BlockFeeInfo): Unit = {
    require(withdrawalEpochInfoOpt.nonEmpty, "WithdrawalEpochInfo must be set before adding fee info.")
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
  }

  private[horizen] def saveToStorage(version: ByteArrayWrapper): Unit = {
    require(withdrawalEpochInfoOpt.nonEmpty, "WithdrawalEpochInfo must be NOT NULL.")
    require(blockFeeInfoOpt.nonEmpty, "BlockFeeInfo must be NOT NULL.")
    require(accountStateRootOpt.nonEmpty, "Account State Root must be NOT NULL.")

    val updateList = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    val removeList = new JArrayList[ByteArrayWrapper]()

    // Update Withdrawal epoch related data
    updateList.add(new JPair(withdrawalEpochInformationKey,
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfoOpt.get))))

    // Store the top quality cert for epoch if present
    topQualityCertificateOpt.foreach(certificate => {
      updateList.add(new JPair(getTopQualityCertificateKey(certificate.epochNumber),
        WithdrawalEpochCertificateSerializer.toBytes(certificate)))
    }
    )

    // Update BlockFeeInfo data
    val nextBlockFeeInfoCounter: Int = getBlockFeeInfoCounter(withdrawalEpochInfoOpt.get.epoch) + 1
    updateList.add(new JPair(getBlockFeeInfoCounterKey(withdrawalEpochInfoOpt.get.epoch),
      new ByteArrayWrapper(Ints.toByteArray(nextBlockFeeInfoCounter))))
    updateList.add(new JPair(getBlockFeeInfoKey(withdrawalEpochInfoOpt.get.epoch, nextBlockFeeInfoCounter),
      new ByteArrayWrapper(BlockFeeInfoSerializer.toBytes(blockFeeInfoOpt.get))))

    // Update Consensus related data
    consensusEpochOpt.foreach(currConsensusEpoch => {
      if (getConsensusEpochNumberFromStorage.getOrElse(intToConsensusEpochNumber(0)) != currConsensusEpoch)
        updateList.add(new JPair(consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(currConsensusEpoch))))
    })

    updateList.add(new JPair(accountStateRootKey, new ByteArrayWrapper(accountStateRootOpt.get)))

    // If sidechain has ceased set the flag
    hasCeasedOpt.foreach(_ => updateList.add(new JPair(ceasingStateKey, new ByteArrayWrapper(Array.emptyByteArray))))
    //update the height
    val nextHeight = getHeight + 1
    updateList.add(new JPair(heightKey, new ByteArrayWrapper(Ints.toByteArray(nextHeight))))


    // If withdrawal epoch switched to the next one, then perform some database clean-up:
    // 1) remove outdated topQualityCertificate retrieved 3 epochs before and referenced to the 4 epochs before.
    //    Note: we should keep last 2 epoch certificates, so in case SC has ceased we have an access to the last active cert.
    // 1) remove outdated BlockFeeInfo records

    val isWithdrawalEpochSwitched: Boolean = getWithdrawalEpochInfoFromStorage match {
      case Some(storedEpochInfo) => storedEpochInfo.epoch != withdrawalEpochInfoOpt.get.epoch
      case _ => false
    }
    if (isWithdrawalEpochSwitched) {
      val certEpochNumberToRemove: Int = withdrawalEpochInfoOpt.get.epoch - 4
      removeList.add(getTopQualityCertificateKey(certEpochNumberToRemove))

      val blockFeeInfoEpochToRemove: Int = withdrawalEpochInfoOpt.get.epoch - 1
      for (counter <- 0 to getBlockFeeInfoCounter(blockFeeInfoEpochToRemove)) {
        removeList.add(getBlockFeeInfoKey(blockFeeInfoEpochToRemove, counter))
      }
      removeList.add(getBlockFeeInfoCounterKey(blockFeeInfoEpochToRemove))
    }

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


}

