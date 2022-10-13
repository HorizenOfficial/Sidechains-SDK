package com.horizen.certificatesubmitter.strategies

import akka.pattern.ask
import com.horizen.block.SidechainCreationVersions.SidechainCreationVersion
import com.horizen.block.WithdrawalEpochCertificate
import com.horizen.box.WithdrawalRequestBox
import com.horizen.certificatesubmitter.CertificateSubmitter.SignaturesStatus
import com.horizen.certificatesubmitter.dataproof.{DataForProofGeneration, DataForProofGenerationWithKeyRotation}
import com.horizen.certificatesubmitter.keys.ActualKeys.getMerkleRootOfPublicKeys
import com.horizen.certificatesubmitter.keys.{ActualKeys, KeyRotationProof, KeyRotationProofType, SchnorrKeysSignaturesListBytes}
import com.horizen.cryptolibprovider.CryptoLibProvider
import com.horizen.params.NetworkParams
import com.horizen.websocket.server.WebSocketServerRef.sidechainNodeViewHolderRef
import com.horizen.{SidechainSettings, SidechainState}
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import java.util.Optional
import scala.collection.JavaConverters._
import scala.collection.mutable
import scala.compat.java8.OptionConverters.RichOptionForJava8
import scala.concurrent.Await
import scala.util.{Failure, Success, Try}

class WithKeyRotationStrategy(settings: SidechainSettings, params: NetworkParams) extends KeyRotationStrategy(settings, params) {

  override def generateProof(dataForProofGeneration: DataForProofGeneration): com.horizen.utils.Pair[Array[Byte], java.lang.Long] = {

    val (_: Seq[Array[Byte]], signaturesBytes: Seq[Optional[Array[Byte]]]) =
      dataForProofGeneration.schnorrKeyPairs.map {
        case (proposition, proof) => (proposition.bytes(), proof.map(_.bytes()).asJava)
      }.unzip

    log.info(s"Start generating proof with parameters: dataForProofGeneration = $dataForProofGeneration, " +
      s"signersThreshold = ${
        params.signersThreshold
      }. " +
      s"It can take a while.")

    val dataForProofGenerationWithKeyRotation = dataForProofGeneration.asInstanceOf[DataForProofGenerationWithKeyRotation]

    val schnorrKeysSignaturesListBytes = SchnorrKeysSignaturesListBytes(
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.schnorrSignersPublicKeysBytesList,
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.schnorrMastersPublicKeysBytesList,
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.newSchnorrSignersPublicKeysBytesList,
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.newSchnorrMastersPublicKeysBytesList,
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.updatedSigningKeysSkSignatures,
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.updatedSigningKeysMkSignatures,
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.updatedMasterKeysSkSignatures,
      dataForProofGenerationWithKeyRotation.schnorrKeysSignaturesListBytes.updatedMasterKeysMkSignatures
    )
    //create and return proof with quality
    val sidechainCreationVersion: SidechainCreationVersion = params.sidechainCreationVersion
//    CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation.createProof(
//      dataForProofGeneration.withdrawalRequests.asJava,
//      dataForProofGeneration.sidechainId,
//      dataForProofGeneration.referencedEpochNumber,
//      dataForProofGeneration.endEpochCumCommTreeHash,
//      dataForProofGeneration.btrFee,
//      dataForProofGeneration.ftMinAmount,
//      dataForProofGeneration.customFields,
//      signaturesBytes.asJava,
//      schnorrKeysSignaturesListBytes,
//      params.signersThreshold,
//      provingFileAbsolutePath,
//      true,
//      true,
//      dataForProofGenerationWithKeyRotation.previousCertificateOption,
//      sidechainCreationVersion.id,
//      dataForProofGenerationWithKeyRotation.genesisKeysRootHash
//    )
    null
  }

  override def buildDataForProofGeneration(sidechainNodeView: View, status: SignaturesStatus): DataForProofGenerationWithKeyRotation = {
    val history = sidechainNodeView.history
    val state: SidechainState = sidechainNodeView.state

    val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(status.referencedEpoch)

    val btrFee: Long = getBtrFee(status.referencedEpoch)
    val ftMinAmount: Long = getFtMinAmount(status.referencedEpoch)
    val endEpochCumCommTreeHash = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, status.referencedEpoch)
    val sidechainId = params.sidechainId

    val customFields: Array[Byte] = getActualKeysMerkleRoot(status.referencedEpoch, state)

    val signersPublicKeyWithSignatures = params.signersPublicKeys.zipWithIndex.map {
      case (pubKey, pubKeyIndex) =>
        (pubKey, status.knownSigs.find(info => info.pubKeyIndex == pubKeyIndex).map(_.signature))
    }

    val actualKeysOption: Option[ActualKeys] = state.actualKeys(status.referencedEpoch)
    val previousCertificateOption: Option[WithdrawalEpochCertificate] = state.certificate(status.referencedEpoch - 1)

    val keyRotationProofs: Seq[KeyRotationProof] = state.keyRotationProofs(status.referencedEpoch)
    val schnorrSignersPublicKeysBytesList: mutable.IndexedSeq[Array[Byte]] = actualKeysOption match {
      case Some(actualKeys) => scala.collection.mutable.ArraySeq(actualKeys.signingKeys.map(_.bytes()):_*)
      case None => mutable.IndexedSeq[Array[Byte]]()
    }
    val schnorrMastersPublicKeysBytesList: mutable.IndexedSeq[Array[Byte]] = actualKeysOption match {
      case Some(actualKeys) => scala.collection.mutable.ArraySeq(actualKeys.masterKeys.map(_.bytes()): _*)
      case None => mutable.IndexedSeq[Array[Byte]]()
    }
    val newSchnorrSignersPublicKeysBytesList = schnorrSignersPublicKeysBytesList.clone()
    val newSchnorrMastersPublicKeysBytesList = schnorrMastersPublicKeysBytesList.clone()
    val updatedSigningKeysSkSignatures = mutable.IndexedSeq[Array[Byte]]()
    val updatedSigningKeysMkSignatures = mutable.IndexedSeq[Array[Byte]]()
    val updatedMasterKeysSkSignatures = mutable.IndexedSeq[Array[Byte]]()
    val updatedMasterKeysMkSignatures = mutable.IndexedSeq[Array[Byte]]()

    keyRotationProofs.foreach(keyRotationProof => {
      keyRotationProof.keyType match {
        case KeyRotationProofType.SigningKeyRotationProofType =>
          newSchnorrMastersPublicKeysBytesList(keyRotationProof.index) = keyRotationProof.newValueOfKey.bytes()
          updatedSigningKeysSkSignatures(keyRotationProof.index) = keyRotationProof.signingKeySignature.bytes()
          updatedSigningKeysMkSignatures(keyRotationProof.index) = keyRotationProof.masterKeySignature.bytes()
        case KeyRotationProofType.MasterKeyRotationProofType =>
          newSchnorrMastersPublicKeysBytesList(keyRotationProof.index) = keyRotationProof.newValueOfKey.bytes()
          updatedSigningKeysSkSignatures(keyRotationProof.index) = keyRotationProof.signingKeySignature.bytes()
          updatedSigningKeysMkSignatures(keyRotationProof.index) = keyRotationProof.masterKeySignature.bytes()
      }
    })

    val schnorrKeysSignaturesListBytes =  SchnorrKeysSignaturesListBytes(
      schnorrSignersPublicKeysBytesList,
      schnorrMastersPublicKeysBytesList,
      newSchnorrSignersPublicKeysBytesList,
      newSchnorrMastersPublicKeysBytesList,
      updatedSigningKeysSkSignatures,
      updatedSigningKeysMkSignatures,
      updatedMasterKeysSkSignatures,
      updatedMasterKeysMkSignatures
    )

    DataForProofGenerationWithKeyRotation(
      status.referencedEpoch,
      sidechainId,
      withdrawalRequests,
      endEpochCumCommTreeHash,
      btrFee,
      ftMinAmount,
      Seq(customFields),
      signersPublicKeyWithSignatures,
      schnorrKeysSignaturesListBytes,
      previousCertificateOption,
      ActualKeys.getMerkleRootOfPublicKeys(ActualKeys(params.signersPublicKeys.toVector, params.mastersPublicKeys.toVector))
    )
  }

  override def getMessageToSign(referencedWithdrawalEpochNumber: Int): Try[Array[Byte]] = Try {
    def getMessage(sidechainNodeView: View): Try[Array[Byte]] = Try {
      val history = sidechainNodeView.history
      val state = sidechainNodeView.state

      val withdrawalRequests: Seq[WithdrawalRequestBox] = state.withdrawalRequests(referencedWithdrawalEpochNumber)

      val btrFee: Long = getBtrFee(referencedWithdrawalEpochNumber)
      val ftMinAmount: Long = getFtMinAmount(referencedWithdrawalEpochNumber)

      val endEpochCumCommTreeHash: Array[Byte] = lastMainchainBlockCumulativeCommTreeHashForWithdrawalEpochNumber(history, referencedWithdrawalEpochNumber)
      val sidechainId = params.sidechainId

      val customFields: Array[Byte] = getActualKeysMerkleRoot(referencedWithdrawalEpochNumber, state)

//      CryptoLibProvider.thresholdSignatureCircuitWithKeyRotation
//        .generateMessageToBeSigned(withdrawalRequests.asJava, sidechainId, referencedWithdrawalEpochNumber,
//          endEpochCumCommTreeHash, btrFee, ftMinAmount, Seq(customFields))
      null
    }

    Await.result(sidechainNodeViewHolderRef ? GetDataFromCurrentView(getMessage), settings.sparkzSettings.restApi.timeout).asInstanceOf[Try[Array[Byte]]].get
  }

  private def getActualKeysMerkleRoot(referencedWithdrawalEpochNumber: Int, state: SidechainState): Array[Byte] = {
    Try {
      state.actualKeys(referencedWithdrawalEpochNumber).map(getMerkleRootOfPublicKeys)
    } match {
      case Failure(e: IllegalStateException) =>
        throw new Exception("CertificateSubmitter is too late against the State. " +
          s"No utxo merkle tree root for requested epoch $referencedWithdrawalEpochNumber. " +
          s"Current epoch is ${state.getWithdrawalEpochInfo.epoch}")
      case Failure(exception) => log.error("Exception while getting utxoMerkleTreeRoot", exception)
        throw new Exception(exception)
      case Success(byteArrayOption) => byteArrayOption match {
        case Some(byteArray) => byteArray
        case None => Array[Byte]()
      }
    }
  }
}
