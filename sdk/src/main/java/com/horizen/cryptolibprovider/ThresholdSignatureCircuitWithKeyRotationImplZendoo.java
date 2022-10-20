package com.horizen.cryptolibprovider;

import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes;
import com.horizen.certnative.*;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrKeysSignaturesList;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.utils.Pair;
import scala.Enumeration;

import java.util.*;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitWithKeyRotationImplZendoo implements ThresholdSignatureCircuitWithKeyRotation {
    // Note: supportedSegmentSize should correlate with the snark circuit complexity,
    // but is always less or equal the one defined in the MC network (maxSegmentSize).
    private static final int supportedSegmentSize = (1 << 17);

    private static final SchnorrSignature signaturePlaceHolder = new SchnorrSignature();

    @Override
    public List<byte[]> getCertificateCustomFields(List<byte[]> customFields) {
        List<FieldElement> fes = prepareCustomFieldElements(customFields);
        List<byte[]> fesBytes = fes.stream().map(FieldElement::serializeFieldElement).collect(Collectors.toList());
        fes.forEach(FieldElement::freeFieldElement);
        return fesBytes;
    }

    private List<FieldElement> prepareCustomFieldElements(List<byte[]> customFields) {
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
                                            List<byte[]> customParameters) {
        BackwardTransfer[] backwardTransfers =
                bt.stream().map(CommonCircuit::withdrawalRequestBoxToBackwardTransfer).toArray(BackwardTransfer[]::new);

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFields = prepareCustomFieldElements(customParameters);

        WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                FieldElement.deserialize(sidechainId),
                epochNumber,
                Arrays.stream(backwardTransfers).collect(Collectors.toList()),
                0,
                FieldElement.deserialize(endCumulativeScTxCommTreeRoot),
                ftMinAmount,
                btrFee,
                customFields
        );
        FieldElement messageToSign = NaiveThresholdSignatureWKeyRotation.createMsgToSign(withdrawalCertificate);
        byte[] messageAsBytes = messageToSign.serializeFieldElement();

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        customFields.forEach(FieldElement::freeFieldElement);
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
             List<byte[]> customFields,
             List<Optional<byte[]>> schnorrSignatureBytesList,
             SchnorrKeysSignaturesListBytes schnorrKeysSignaturesListBytes,
             long threshold,
             String provingKeyPath,
             boolean checkProvingKey,
             boolean zk,
             Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
             Enumeration.Value sidechainCreationVersion,
             byte[] genesisKeysRootHash
            ) {

        List<SchnorrSignature> signatures = schnorrSignatureBytesList
                .stream()
                .map(signatureBytesOpt -> signatureBytesOpt.map(SchnorrSignature::deserialize).orElse(signaturePlaceHolder))
                .collect(Collectors.toList());

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFieldElement = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFieldsElements = prepareCustomFieldElements(customFields);

        Optional<WithdrawalCertificate> previousCertificateOption = previousEpochCertificateOption
                .map(c -> CswCircuitImplZendoo.createWithdrawalCertificate(c, sidechainCreationVersion));

        SchnorrKeysSignaturesList keysSignaturesList = SchnorrKeysSignaturesListBytes.getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes);
        SchnorrPublicKey[] signingPublicKeys = keysSignaturesList.getSigningKeys();

        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(CommonCircuit::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                sidechainIdFieldElement,
                epochNumber,
                backwardTransfers,
                0,
                FieldElement.deserialize(endCumulativeScTxCommTreeRoot),
                ftMinAmount,
                btrFee,
                customFieldsElements
        );
        CreateProofResult proofAndQuality = NaiveThresholdSignatureWKeyRotation.createProof(keysSignaturesList,
                withdrawalCertificate, previousCertificateOption, signatures,
                signingPublicKeys.length, threshold, FieldElement.deserialize(genesisKeysRootHash), Optional.empty(),
                provingKeyPath, false, zk, true, true);

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFieldElement.freeFieldElement();
        Arrays.stream(keysSignaturesList.getSigningKeys()).forEach(SchnorrPublicKey::freePublicKey);
        signatures.forEach(SchnorrSignature::freeSignature);
        customFieldsElements.forEach(FieldElement::freeFieldElement);

        return new Pair<>(proofAndQuality.getProof(), proofAndQuality.getQuality());
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt,
                               byte[] sidechainId,
                               int epochNumber,
                               byte[] endCumulativeScTxCommTreeRoot,
                               long btrFee,
                               long ftMinAmount,
                               List<byte[]> customFields,
                               byte[] constant,
                               long quality,
                               byte[] proof,
                               String verificationKeyPath,
                               Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                               byte[] genesisConstantBytes,
                               Enumeration.Value sidechainCreationVersion) {
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(CommonCircuit::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        FieldElement mcbScTxsCom = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement constantFe = FieldElement.deserialize(constant);
        FieldElement sidechainIdFIeldElement = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFieldsElements = prepareCustomFieldElements(customFields);
        FieldElement genesisConstant = FieldElement.deserialize(genesisConstantBytes);

        WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                sidechainIdFIeldElement,
                epochNumber,
                backwardTransfers,
                quality,
                mcbScTxsCom,
                ftMinAmount,
                btrFee,
                customFieldsElements
        );

        Optional<WithdrawalCertificate> previousCertificateOption = previousEpochCertificateOption
                .map(c -> CswCircuitImplZendoo.createWithdrawalCertificate(c, sidechainCreationVersion));


        boolean verificationResult = NaiveThresholdSignatureWKeyRotation.verifyProof(withdrawalCertificate, previousCertificateOption, genesisConstant, proof, verificationKeyPath);

        mcbScTxsCom.freeFieldElement();
        sidechainIdFIeldElement.freeFieldElement();
        constantFe.freeFieldElement();
        customFieldsElements.forEach(FieldElement::freeFieldElement);
        genesisConstant.freeFieldElement();
        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(byte[] genesisKeysRootHash, long threshold) {

        // Note: sc-cryptolib return constant in LittleEndian
        FieldElement sysDataConstant = NaiveThresholdSignatureWKeyRotation.getConstant(FieldElement.deserialize(genesisKeysRootHash), threshold);
        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();

        return sysDataConstantBytes;
    }

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath, int customFieldsNum) {
        return NaiveThresholdSignatureWKeyRotation.setup(ProvingSystemType.COBOUNDARY_MARLIN, maxPks, customFieldsNum,
                Optional.of(supportedSegmentSize),
                provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }
}
