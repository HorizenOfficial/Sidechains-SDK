package io.horizen.utxo.storage

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.SidechainTypes
import io.horizen.block.{WithdrawalEpochCertificate, WithdrawalEpochCertificateSerializer}
import io.horizen.certificatesubmitter.keys._
import io.horizen.consensus._
import io.horizen.cryptolibprovider.CircuitTypes
import io.horizen.params.NetworkParams
import io.horizen.storage.{SidechainStorageInfo, Storage, StorageIterator, leveldb}
import io.horizen.utils.{ByteArrayWrapper, ListSerializer, WithdrawalEpochInfo, WithdrawalEpochInfoSerializer, Pair => JPair, _}
import io.horizen.utxo.backup.BoxIterator
import io.horizen.utxo.box.{WithdrawalRequestBox, WithdrawalRequestBoxSerializer}
import io.horizen.utxo.companion.SidechainBoxesCompanion
import io.horizen.utxo.forge.{ForgerList, ForgerListSerializer}
import io.horizen.utxo.utils.{BlockFeeInfo, BlockFeeInfoSerializer}
import sparkz.util.{ModifierId, SparkzLogging, bytesToId}

import java.nio.charset.StandardCharsets
import java.util.{ArrayList => JArrayList}
import scala.collection.JavaConverters._
import scala.collection.mutable.ListBuffer
import scala.compat.java8.OptionConverters._
import scala.util._

class SidechainStateStorage(storage: Storage, sidechainBoxesCompanion: SidechainBoxesCompanion, params: NetworkParams)
  extends SparkzLogging
    with SidechainStorageInfo
    with SidechainTypes
{
  // Version - block Id
  // Key - byte array box Id

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainBoxesCompanion != null, "SidechainBoxesCompanion must be NOT NULL.")

  private[horizen] val withdrawalEpochInformationKey = Utils.calculateKey("withdrawalEpochInformation".getBytes(StandardCharsets.UTF_8))
  private val withdrawalRequestSerializer = new ListSerializer[WithdrawalRequestBox](WithdrawalRequestBoxSerializer.getSerializer)

  private[horizen] val consensusEpochKey = Utils.calculateKey("consensusEpoch".getBytes(StandardCharsets.UTF_8))

  private[horizen] val ceasingStateKey = Utils.calculateKey("ceasingStateKey".getBytes(StandardCharsets.UTF_8))

  private[horizen] val forgerListIndexKey = Utils.calculateKey("forgerListIndexKey".getBytes(StandardCharsets.UTF_8))

  private val undefinedWithdrawalEpochCounter: Int = -1
  private[horizen] def getWithdrawalEpochCounterKey(withdrawalEpoch: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("withdrawalEpochCounter".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpoch)))
  }

  private[horizen] def getWithdrawalRequestsKey(withdrawalEpoch: Int, counter: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("withdrawalRequests".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpoch), Ints.toByteArray(counter)))
  }

  private[horizen] def getCertifiersStorageKey(withdrawalEpoch: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("certificateKeys".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpoch)))
  }
  private[horizen] def getKeyRotationProofKey(withdrawalEpoch: Int, indexOfSigner: Int, keyType: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("keyRotationProof".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpoch),
      Ints.toByteArray(indexOfSigner), Ints.toByteArray(keyType)))
  }

  private[horizen] def getTopQualityCertificateKey(referencedWithdrawalEpoch: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("topQualityCertificate".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(referencedWithdrawalEpoch)))
  }

  private[horizen] val getLastCertificateEpochNumberKey = Utils.calculateKey("lastCertificateEpochNumber".getBytes(StandardCharsets.UTF_8))

  private[horizen] val getLastCertificateSidechainBlockIdKey = Utils.calculateKey("getLastCertificateSidechainBlockId".getBytes(StandardCharsets.UTF_8))

  private val undefinedBlockFeeInfoCounter: Int = -1

  private[horizen] def getBlockFeeInfoCounterKey(withdrawalEpochNumber: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("blockFeeInfoCounter".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpochNumber)))
  }

  private[horizen] def getBlockFeeInfoKey(withdrawalEpochNumber: Int, counter: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("blockFeeInfo".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpochNumber), Ints.toByteArray(counter)))
  }

  private[horizen] def getUtxoMerkleTreeRootKey(withdrawalEpochNumber: Int): ByteArrayWrapper = {
    Utils.calculateKey(Bytes.concat("utxoMerkleTreeRoot".getBytes(StandardCharsets.UTF_8), Ints.toByteArray(withdrawalEpochNumber)))
  }


  def getBox(boxId: Array[Byte]): Option[SidechainTypes#SCB] = {
    storage.get(Utils.calculateKey(boxId)) match {
      case v if v.isPresent =>
        sidechainBoxesCompanion.parseBytesTry(v.get().data) match {
          case Success(box) => Option(box)
          case Failure(exception) =>
            log.error("Error while WalletBox parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getWithdrawalEpochInfo: Option[WithdrawalEpochInfo] = {
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

  def getWithdrawalEpochCounter(epoch: Int): Int = {
    storage.get(getWithdrawalEpochCounterKey(epoch)).asScala match {
      case Some(baw) =>
        Try {
          Ints.fromByteArray(baw.data)
        }.toOption.getOrElse(undefinedWithdrawalEpochCounter)
      case _ => undefinedWithdrawalEpochCounter
    }
  }

  private def getBlockFeeInfoCounter(withdrawalEpochNumber: Int): Int = {
    storage.get(getBlockFeeInfoCounterKey(withdrawalEpochNumber)).asScala match {
      case Some(baw) =>
        Try {
          Ints.fromByteArray(baw.data)
        }.toOption.getOrElse(undefinedBlockFeeInfoCounter)
      case _ => undefinedBlockFeeInfoCounter
    }
  }

  def getFeePayments(withdrawalEpochNumber: Int): Seq[BlockFeeInfo] = {
    val blockFees: ListBuffer[BlockFeeInfo] = ListBuffer()
    val lastCounter = getBlockFeeInfoCounter(withdrawalEpochNumber)
    for (counter <- 0 to lastCounter) {
      storage.get(getBlockFeeInfoKey(withdrawalEpochNumber, counter)).asScala match {
        case Some(baw) => BlockFeeInfoSerializer.parseBytesTry(baw.data) match {
          case Success(info) => blockFees.append(info)
          case Failure(exception) => throw new IllegalStateException("Error while fee payment parsing.", exception)
        }
        case None => throw new IllegalStateException("Error while fee payments retrieving: record expected to exist.")
      }
    }

    blockFees
  }

  def getWithdrawalRequests(withdrawalEpoch: Int): Seq[WithdrawalRequestBox] = {
    // Aggregate withdrawal requests until reaching the counter, where the key is not present in the storage.
    val withdrawalRequests: ListBuffer[WithdrawalRequestBox] = ListBuffer()
    val lastCounter: Int = getWithdrawalEpochCounter(withdrawalEpoch)
    for (counter <- 0 to lastCounter) {
      storage.get(getWithdrawalRequestsKey(withdrawalEpoch, counter)).asScala match {
        case Some(baw) =>
          withdrawalRequestSerializer.parseBytesTry(baw.data) match {
            case Success(wr) =>
              withdrawalRequests.appendAll(wr.asScala)
            case Failure(exception) =>
              throw new IllegalStateException("Error while withdrawal requests parsing.", exception)
          }
        case None =>
          throw new IllegalStateException("Error while withdrawal requests retrieving: record expected to exist.")
      }
    }
    withdrawalRequests
  }

  def getCertifiersKeys(withdrawalEpoch: Int): Option[CertifiersKeys] = {
    storage.get(getCertifiersStorageKey(withdrawalEpoch)).asScala match {
      case Some(baw) =>
        CertifiersKeysSerializer.parseBytesTry(baw.data) match {
          case Success(actualKeys: CertifiersKeys) => Option(actualKeys)
          case Failure(exception) =>
            log.error("Error while withdrawal epoch certificate public keys parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getKeyRotationProof(withdrawalEpochNumber: Int, indexOfSigner: Int, keyType: Int): Option[KeyRotationProof] = {
    storage.get(getKeyRotationProofKey(withdrawalEpochNumber, indexOfSigner, keyType)).asScala match {
      case Some(baw) => KeyRotationProofSerializer.parseBytesTry(baw.data) match {
        case Success(keyRotationProof) => Option(keyRotationProof)
        case Failure(exception) =>
          log.error("Error while key rotation proofs parsing.", exception)
          Option.empty
      }
      case _ => Option.empty
    }
  }

  def getTopQualityCertificate(referencedWithdrawalEpoch: Int): Option[WithdrawalEpochCertificate] = {
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

  def getLastCertificateReferencedEpoch(): Option[Int] = {
    storage.get(getLastCertificateEpochNumberKey).asScala match {
      case Some(baw) =>
        Try {
          Ints.fromByteArray(baw.data)
        } match {
          case Success(epoch) => Some(epoch)
          case Failure(exception) =>
            log.error("Error while last certificate epoch information parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getLastCertificateSidechainBlockId(): Option[ModifierId] = {
    storage.get(getLastCertificateSidechainBlockIdKey).asScala match {
      case Some(baw) =>
        Try {
          bytesToId(baw.data())
        } match {
          case Success(id) => Some(id)
          case Failure(exception) =>
            log.error("Error while last certificate sidechain block id parsing.", exception)
            Option.empty
        }
      case _ => Option.empty
    }
  }

  def getUtxoMerkleTreeRoot(withdrawalEpoch: Int): Option[Array[Byte]] = {
    storage.get(getUtxoMerkleTreeRootKey(withdrawalEpoch)).asScala.map(_.data)
  }

  def hasCeased: Boolean = {
    storage.get(ceasingStateKey).isPresent
  }

  def getConsensusEpochNumber: Option[ConsensusEpochNumber] = {
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

  def getForgerList: Option[ForgerList] = {
    storage.get(forgerListIndexKey).asScala match {
      case Some(baw) =>
        Try {
          ForgerListSerializer.parseBytesTry(baw.data)
        } match {
          case Success(Success(forgerIndexes)) => Some(forgerIndexes)
          case Success(Failure(_)) =>
            log.error("Error while forger list indexes information parsing.")
            Option.empty
          case Failure(exception) =>
            log.error("Error while forger list indexes information parsing.", exception)
            Option.empty
        }
      case None => Option.empty
    }
  }

  def update(version: ByteArrayWrapper,
             withdrawalEpochInfo: WithdrawalEpochInfo,
             boxUpdateList: Set[SidechainTypes#SCB],
             boxIdsRemoveSet: Set[ByteArrayWrapper],
             withdrawalRequestAppendSeq: Seq[WithdrawalRequestBox],
             consensusEpoch: ConsensusEpochNumber,
             topQualityCertificateOpt: Option[WithdrawalEpochCertificate],
             blockFeeInfo: BlockFeeInfo,
             utxoMerkleTreeRootOpt: Option[Array[Byte]],
             scHasCeased: Boolean,
             forgerListIndexes: Array[Int],
             forgerListSize: Int,
             keyRotationProofs: Seq[KeyRotationProof] = Seq.empty[KeyRotationProof],
             certifiersKeysOption: Option[CertifiersKeys] = None): Try[SidechainStateStorage] = Try {
    require(withdrawalEpochInfo != null, "WithdrawalEpochInfo must be NOT NULL.")
    require(boxUpdateList != null, "List of Boxes to add/update must be NOT NULL. Use empty List instead.")
    require(boxIdsRemoveSet != null, "List of Box IDs to remove must be NOT NULL. Use empty List instead.")
    require(!boxUpdateList.contains(null), "Box to add/update must be NOT NULL.")
    require(!boxIdsRemoveSet.contains(null), "BoxId to remove must be NOT NULL.")
    require(withdrawalRequestAppendSeq != null, "Seq of WithdrawalRequests to append must be NOT NULL. Use empty Seq instead.")
    require(!withdrawalRequestAppendSeq.contains(null), "WithdrawalRequest to append must be NOT NULL.")
    require(blockFeeInfo != null, "BlockFeeInfo must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()

    // Update boxes data
    for (r <- boxIdsRemoveSet)
      removeList.add(Utils.calculateKey(r.data))

    for (b <- boxUpdateList)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](Utils.calculateKey(b.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(b))))

    // Update Withdrawal epoch related data
    updateList.add(new JPair(withdrawalEpochInformationKey,
      new ByteArrayWrapper(WithdrawalEpochInfoSerializer.toBytes(withdrawalEpochInfo))))

    if (withdrawalRequestAppendSeq.nonEmpty) {
      // Calculate the next counter for storing withdrawal requests without duplication previously stored ones.
      val nextWithdrawalEpochCounter: Int = getWithdrawalEpochCounter(withdrawalEpochInfo.epoch) + 1

      updateList.add(new JPair(getWithdrawalEpochCounterKey(withdrawalEpochInfo.epoch),
        new ByteArrayWrapper(Ints.toByteArray(nextWithdrawalEpochCounter))))

      updateList.add(new JPair(getWithdrawalRequestsKey(withdrawalEpochInfo.epoch, nextWithdrawalEpochCounter),
        new ByteArrayWrapper(withdrawalRequestSerializer.toBytes(withdrawalRequestAppendSeq.asJava))))
    }

    certifiersKeysOption match {
      case Some(actualKeys: CertifiersKeys) =>
        updateList.add(new JPair(getCertifiersStorageKey(withdrawalEpochInfo.epoch),
          new ByteArrayWrapper(CertifiersKeysSerializer.toBytes(actualKeys))))

      case None => Array[Byte]()
    }

    keyRotationProofs.foreach(keyRotationProof => {
      updateList.add(new JPair(getKeyRotationProofKey(withdrawalEpochInfo.epoch, keyRotationProof.index, keyRotationProof.keyType.id),
        new ByteArrayWrapper(KeyRotationProofSerializer.toBytes(keyRotationProof))))
    })
    // Store utxo tree merkle root if present
    utxoMerkleTreeRootOpt.foreach(merkleRoot => {
      updateList.add(new JPair(getUtxoMerkleTreeRootKey(withdrawalEpochInfo.epoch), new ByteArrayWrapper(merkleRoot)))
    })

    // If withdrawal epoch switched to the next one, then:
    // 1) remove outdated withdrawal related records and counters (2 epochs before);
    // 2) remove outdated topQualityCertificate retrieved 3 epochs before and referenced to the 4 epochs before.
    //    Note: we should keep last 2 epoch certificates, so in case SC has ceased we have an access to the last active cert.
    // 3) remove outdated utxo merkle tree root record (4 epochs before).
    // 4) remove outdated BlockFeeInfo records
    val isWithdrawalEpochSwitched: Boolean = getWithdrawalEpochInfo match {
      case Some(storedEpochInfo) => storedEpochInfo.epoch != withdrawalEpochInfo.epoch
      case _ => false
    }
    if (isWithdrawalEpochSwitched) {
      if (!params.isNonCeasing) {
        // For ceasing sidechain we remove outdated information in the end of the withdrawal epoch
        val wrEpochNumberToRemove: Int = withdrawalEpochInfo.epoch - 2
        for (counter <- 0 to getWithdrawalEpochCounter(wrEpochNumberToRemove)) {
          removeList.add(getWithdrawalRequestsKey(wrEpochNumberToRemove, counter))
        }
        removeList.add(getWithdrawalEpochCounterKey(wrEpochNumberToRemove))

        val certEpochNumberToRemove: Int = withdrawalEpochInfo.epoch - 4
        removeList.add(getTopQualityCertificateKey(certEpochNumberToRemove))
        removeList.add(getUtxoMerkleTreeRootKey(certEpochNumberToRemove))
        removeList.add(getCertifiersStorageKey(certEpochNumberToRemove))
        for {
          indexOfSigner <- params.signersPublicKeys.indices
          typeOfKey <- 0 until CircuitTypes.maxId
        } {
          removeList.add(getKeyRotationProofKey(certEpochNumberToRemove, indexOfSigner, typeOfKey))
        }
      }

      val blockFeeInfoEpochToRemove: Int = withdrawalEpochInfo.epoch - 1
      for (counter <- 0 to getBlockFeeInfoCounter(blockFeeInfoEpochToRemove)) {
        removeList.add(getBlockFeeInfoKey(blockFeeInfoEpochToRemove, counter))
      }
      removeList.add(getBlockFeeInfoCounterKey(blockFeeInfoEpochToRemove))
    }


    if (params.isNonCeasing) {
      topQualityCertificateOpt.foreach(certificate => {
        // For non-ceasing sidechain store the id of the SC block which contains the certificate.
        // It is used to detect if certificate was included into the MC till the end of the "virtual withdrawal epoch"
        // or with some delay, so will have an impact on the value of the next certificate `endEpochCumScTxCommTreeRoot`.
        updateList.add(new JPair(getLastCertificateSidechainBlockIdKey, version))

        // For non-ceasing sidechain we remove outdated certificate info when we retrieve the new top quality certificate:
        // remove outdated withdrawal related records and counters from upt to the current cert data;
        // Note: SC block may contain multiple Certs, so we need to remove data for all of them.
        val prevCertReferencedEpoch = getLastCertificateReferencedEpoch().getOrElse(-1)
        for (outdatedEpochNumber <- prevCertReferencedEpoch + 1 until certificate.epochNumber) {
          val wrEpochNumberToRemove: Int = outdatedEpochNumber
          for (counter <- 0 to getWithdrawalEpochCounter(wrEpochNumberToRemove)) {
            removeList.add(getWithdrawalRequestsKey(wrEpochNumberToRemove, counter))
          }
          removeList.add(getWithdrawalEpochCounterKey(wrEpochNumberToRemove))

          removeList.add(getTopQualityCertificateKey(wrEpochNumberToRemove))
          removeList.add(getCertifiersStorageKey(wrEpochNumberToRemove))
          for {
            indexOfSigner <- params.signersPublicKeys.indices
            typeOfKey <- 0 until CircuitTypes.maxId
          } {
            removeList.add(getKeyRotationProofKey(wrEpochNumberToRemove, indexOfSigner, typeOfKey))
          }
        }
      })
    }
    // Store referenced epoch number and the top quality cert for epoch if present
    topQualityCertificateOpt.foreach(certificate => {
      updateList.add(new JPair(getLastCertificateEpochNumberKey,
       new ByteArrayWrapper(Ints.toByteArray(certificate.epochNumber))))

      updateList.add(new JPair(getTopQualityCertificateKey(certificate.epochNumber),
        WithdrawalEpochCertificateSerializer.toBytes(certificate)))
    })

    // Update BlockFeeInfo data
    val nextBlockFeeInfoCounter: Int = getBlockFeeInfoCounter(withdrawalEpochInfo.epoch) + 1
    updateList.add(new JPair(getBlockFeeInfoCounterKey(withdrawalEpochInfo.epoch),
      new ByteArrayWrapper(Ints.toByteArray(nextBlockFeeInfoCounter))))
    updateList.add(new JPair(getBlockFeeInfoKey(withdrawalEpochInfo.epoch, nextBlockFeeInfoCounter),
      new ByteArrayWrapper(BlockFeeInfoSerializer.toBytes(blockFeeInfo))))

    // Update Consensus related data
    if (getConsensusEpochNumber.getOrElse(intToConsensusEpochNumber(0)) != consensusEpoch) {
      updateList.add(new JPair(consensusEpochKey, new ByteArrayWrapper(Ints.toByteArray(consensusEpoch))))
    }

    // If sidechain has ceased set the flag
    if (scHasCeased)
      updateList.add(new JPair(ceasingStateKey, new ByteArrayWrapper(Array.emptyByteArray)))

    //Set ForgerList indexes
    getForgerList match {
      case Some(forgerList) =>
        if (forgerListIndexes.length != 0) {
          val updatedForgerList = forgerList.updateIndexes(forgerListIndexes)
          updateList.add(new JPair(forgerListIndexKey, ForgerListSerializer.toBytes(updatedForgerList)))
        }
      case None =>
        val emptyForgerListIndex = ForgerList(new Array[Int](forgerListSize))
        updateList.add(new JPair(forgerListIndexKey, ForgerListSerializer.toBytes(emptyForgerListIndex)))
    }

    storage.update(version, updateList, removeList)
    this
  }

  override def lastVersionId: Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions(): Seq[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollbackVersions(maxNumberOfVersions: Int): List[ByteArrayWrapper] = {
    storage.rollbackVersions(maxNumberOfVersions).asScala.toList
  }

  def rollback(version: ByteArrayWrapper): Try[SidechainStateStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty

  def getIterator: StorageIterator = storage.getIterator

  /**
   * This function restores the unspent boxes that come from a ceased sidechain by saving
   * them into the SidechainStateStorage
   *
   * @param backupStorageBoxIterator : storage containing the boxes saved from the ceased sidechain
   * @param lastVersion : lastVersion
   */
  def restoreBackup(backupStorageBoxIterator: BoxIterator, lastVersion: Array[Byte]): Unit = {
    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper, ByteArrayWrapper]]()
    val lastVersionWrapper = new ByteArrayWrapper(lastVersion)

    var optionalBox = backupStorageBoxIterator.nextBox
    while (optionalBox.isPresent) {
      val box = optionalBox.get.getBox
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](Utils.calculateKey(box.id()),
        new ByteArrayWrapper(sidechainBoxesCompanion.toBytes(box))))
      log.info("Restore Box id " + box.boxTypeId())
      optionalBox = backupStorageBoxIterator.nextBox
      if (updateList.size() == leveldb.Constants.BatchSize) {
        if (optionalBox.isPresent)
          storage.update(new ByteArrayWrapper(Utils.nextVersion), updateList, removeList)
        else
          storage.update(lastVersionWrapper, updateList, removeList)
        updateList.clear()
      }
    }

    if (updateList.size() != 0)
      storage.update(lastVersionWrapper, updateList, removeList)
    log.info("SidechainStateStorage restore completed successfully!")
  }
}
