package com.horizen.cryptolibprovider;

import com.horizen.block.SidechainCreationVersions;
import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesList;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.CreateProofResult;
import com.horizen.certnative.NaiveThresholdSigProof;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.utils.Pair;
import scala.Option;
import scala.collection.Iterator;
import scala.collection.Seq;
import scala.compat.java8.OptionConverters;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitWithKeyRotationImplZendoo implements ThresholdSignatureCircuitWithKeyRotation {
    // Note: supportedSegmentSize should correlate with the snark circuit complexity,
    // but is always less or equal the one defined in the MC network (maxSegmentSize).
    private static final int supportedSegmentSize = (1 << 17);

    private static final SchnorrSignature signaturePlaceHolder = new SchnorrSignature();

    private static BackwardTransfer withdrawalRequestBoxToBackwardTransfer(WithdrawalRequestBox box) {
        return new BackwardTransfer(box.proposition().bytes(), box.value());
    }

    @Override
    public List<byte[]> getCertificateCustomFields(Seq<byte[]> customFields) {
        List<FieldElement> fes = prepareCustomFieldElements(customFields);
        List<byte[]> fesBytes = fes.stream().map(FieldElement::serializeFieldElement).collect(Collectors.toList());
        fes.forEach(FieldElement::freeFieldElement);
        return fesBytes;
    }

    private List<FieldElement> prepareCustomFieldElements(Seq<byte[]> customFields) {
        Iterator<byte[]> iterator = customFields.iterator();
        List<FieldElement> fieldElements = new ArrayList<>();
        while (iterator.hasNext()) {
            byte[] actualKeysMerkleRootHash = iterator.next();
            fieldElements.add(FieldElement.deserialize(actualKeysMerkleRootHash));
        }
        return fieldElements;
    }

    @Override
    public byte[] reconstructUtxoMerkleTreeRoot(byte[] fe1Bytes, byte[] fe2Bytes) {
        FieldElement fe1 = FieldElement.deserialize(fe1Bytes);
        if (fe1 == null)
            return new byte[0];
        FieldElement fe2 = FieldElement.deserialize(fe2Bytes);
        if (fe2 == null) {
            fe1.freeFieldElement();
            return new byte[0];
        }

        FieldElement utxoMerkleTreeRootFe = FieldElement.joinAt(fe1, 16, fe2, 16);
        byte[] utxoMerkleTreeRoot = utxoMerkleTreeRootFe.serializeFieldElement();

        fe1.freeFieldElement();
        fe2.freeFieldElement();
        utxoMerkleTreeRootFe.freeFieldElement();

        return utxoMerkleTreeRoot;
    }

    @Override
    public byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt,
                                            byte[] sidechainId,
                                            int epochNumber,
                                            byte[] endCumulativeScTxCommTreeRoot,
                                            long btrFee,
                                            long ftMinAmount,
                                            Seq<byte[]> customParameters) {
        BackwardTransfer[] backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitWithKeyRotationImplZendoo::withdrawalRequestBoxToBackwardTransfer).toArray(BackwardTransfer[]::new);

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = prepareCustomFieldElements(customParameters);

        FieldElement messageToSign = NaiveThresholdSigProof.createMsgToSign(backwardTransfers, sidechainIdFe,
                epochNumber, endCumulativeScTxCommTreeRootFe, btrFee, ftMinAmount, customFe);
        byte[] messageAsBytes = messageToSign.serializeFieldElement();

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        customFe.forEach(FieldElement::freeFieldElement);
        messageToSign.freeFieldElement();

        return messageAsBytes;
    }

    @Override
    public Pair<byte[], Long> createProof
            (List<WithdrawalRequestBox> bt,
             byte[] sidechainId,
             int epochNumber,
             byte[] endCumulativeScTxCommTreeRoot,
             long btrFee,
             long ftMinAmount,
             Seq<byte[]> customParameters,
             List<Optional<byte[]>> schnorrSignatureBytesList,
             List<byte[]> schnorrSignersPublicKeysBytesList,
             List<byte[]> schnorrMastersPublicKeysBytesList,
             List<byte[]> newSchnorrSignersPublicKeysBytesList,
             List<byte[]> newSchnorrMastersPublicKeysBytesList,
             List<byte[]> updatedSigningKeysSkSignatures,
             List<byte[]> updatedSigningKeysMkSignatures,
             List<byte[]> updatedMasterKeysSkSignatures,
             List<byte[]> updatedMasterKeysMkSignatures,
             long threshold,
             String provingKeyPath,
             boolean checkProvingKey,
             boolean zk,
             Option<WithdrawalEpochCertificate> previousEpochCertificateOption,
             int sidechainCreationVersionInt
            ) {

        List<SchnorrSignature> signatures = schnorrSignatureBytesList
                .stream()
                .map(signatureBytesOpt -> signatureBytesOpt.map(SchnorrSignature::deserialize).orElse(signaturePlaceHolder))
                .collect(Collectors.toList());

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = prepareCustomFieldElements(customParameters);

        Optional<WithdrawalCertificate> previousCertificateOption = OptionConverters.toJava(previousEpochCertificateOption)
                .map(c -> CswCircuitImplZendoo.createWithdrawalCertificate(c,
                        SidechainCreationVersions.Value(sidechainCreationVersionInt)));

        List<SchnorrPublicKey> schnorrSignersPublicKeys = byteArrayToKeysList(schnorrSignersPublicKeysBytesList);


        SchnorrKeysSignaturesList keysSignaturesList = new SchnorrKeysSignaturesList(
                schnorrSignersPublicKeys,
                byteArrayToKeysList(schnorrMastersPublicKeysBytesList),
                byteArrayToKeysList(newSchnorrSignersPublicKeysBytesList),
                byteArrayToKeysList(newSchnorrMastersPublicKeysBytesList),
                byteArrayToSignaturesList(updatedSigningKeysSkSignatures),
                byteArrayToSignaturesList(updatedSigningKeysMkSignatures),
                byteArrayToSignaturesList(updatedMasterKeysSkSignatures),
                byteArrayToSignaturesList(updatedMasterKeysMkSignatures)
        );

        CreateProofResult proofAndQuality = KeyRotationThresholdSigProof.createProof(
                keysSignaturesList, withdrawalCertificate, previousCertificateOption, schnorrSignatureBytesList,
                schnorrSignersPublicKeysBytesList.size(), threshold, genesisKeysRootHash);

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        schnorrSignersPublicKeys.forEach(SchnorrPublicKey::freePublicKey);
        signatures.forEach(SchnorrSignature::freeSignature);
        customFe.forEach(FieldElement::freeFieldElement);

        return new Pair<>(proofAndQuality.getProof(), proofAndQuality.getQuality());
    }

    private List<SchnorrPublicKey> byteArrayToKeysList(List<byte[]> schnorrPublicKeysBytesList) {
        return schnorrPublicKeysBytesList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());
    }

    private List<SchnorrSignature> byteArrayToSignaturesList(List<byte[]> schnorrSignaturesBytesList) {
        return schnorrSignaturesBytesList.stream().map(SchnorrSignature::deserialize).collect(Collectors.toList());
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt,
                               byte[] sidechainId,
                               int epochNumber,
                               byte[] endCumulativeScTxCommTreeRoot,
                               long btrFee,
                               long ftMinAmount,
                               Seq<byte[]> customFields,
                               byte[] constant,
                               long quality, byte[] proof,
                               boolean checkProof,
                               String verificationKeyPath,
                               boolean checkVerificationKey) {
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitWithKeyRotationImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement constantFe = FieldElement.deserialize(constant);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = prepareCustomFieldElements(customFields);

        boolean verificationResult = NaiveThresholdSigProof.verifyProof(backwardTransfers, sidechainIdFe, epochNumber,
                endCumulativeScTxCommTreeRootFe, btrFee, ftMinAmount, constantFe, quality, customFe, proof, checkProof,
                verificationKeyPath, checkVerificationKey);

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        constantFe.freeFieldElement();
        customFe.forEach(FieldElement::freeFieldElement);

        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold) {
        List<SchnorrPublicKey> schnorrPublicKeys = publicKeysList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        // Note: sc-cryptolib return constant in LittleEndian
        FieldElement sysDataConstant = NaiveThresholdSigProof.getConstant(schnorrPublicKeys, threshold);
        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();
        schnorrPublicKeys.forEach(SchnorrPublicKey::freePublicKey);

        return sysDataConstantBytes;
    }

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath, int customFieldsNum) {
        return NaiveThresholdSigProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, maxPks, customFieldsNum,
                Optional.of(supportedSegmentSize),
                provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }
}
