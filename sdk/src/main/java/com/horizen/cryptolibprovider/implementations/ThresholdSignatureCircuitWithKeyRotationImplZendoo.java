package com.horizen.cryptolibprovider.implementations;

import com.horizen.block.SidechainCreationVersions;
import com.horizen.block.WithdrawalEpochCertificate;
import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certificatesubmitter.keys.SchnorrKeysSignaturesListBytes;
import com.horizen.certnative.CreateProofResult;
import com.horizen.certnative.NaiveThresholdSignatureWKeyRotation;
import com.horizen.certnative.WithdrawalCertificate;
import com.horizen.cryptolibprovider.CommonCircuit;
import com.horizen.cryptolibprovider.ThresholdSignatureCircuitWithKeyRotation;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.schnorrnative.ValidatorKeysUpdatesList;
import com.horizen.utils.Pair;

import java.util.*;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitWithKeyRotationImplZendoo implements ThresholdSignatureCircuitWithKeyRotation {
    // Note: supportedSegmentSize should correlate with the snark circuit complexity,
    // but is always less or equal the one defined in the MC network (maxSegmentSize).
    private static final int supportedSegmentSize = (1 << 17);

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
    public byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt,
                                            byte[] sidechainId,
                                            int epochNumber,
                                            byte[] endCumulativeScTxCommTreeRoot,
                                            long btrFee,
                                            long ftMinAmount,
                                            List<byte[]> customParameters) {

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        List<FieldElement> customFields;
        FieldElement messageToSign;
        byte[] messageAsBytes;
        try (FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId)) {
            customFields = prepareCustomFieldElements(customParameters);

            WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                    FieldElement.deserialize(sidechainId),
                    epochNumber,
                    CommonCircuit.getBackwardTransfers(bt),
                    endCumulativeScTxCommTreeRootFe,
                    ftMinAmount,
                    btrFee,
                    customFields
            );
            messageToSign = NaiveThresholdSignatureWKeyRotation.createMsgToSign(withdrawalCertificate);
            messageAsBytes = messageToSign.serializeFieldElement();

            endCumulativeScTxCommTreeRootFe.freeFieldElement();
            sidechainIdFe.freeFieldElement();
        }
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
             Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
             int sidechainCreationVersionNumber,
             byte[] genesisKeysRootHash,
             String provingKeyPath,
             boolean checkProvingKey,
             boolean zk
            ) {
        List<SchnorrSignature> signatures = CommonCircuit.getSignatures(schnorrSignatureBytesList);

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFieldElement = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFieldsElements = prepareCustomFieldElements(customFields);

        Optional<WithdrawalCertificate> previousCertificateOption = previousEpochCertificateOption
                .map(c -> CommonCircuit.createWithdrawalCertificate(c, SidechainCreationVersions.apply(sidechainCreationVersionNumber)));

        ValidatorKeysUpdatesList validatorKeysUpdatesList = SchnorrKeysSignaturesListBytes.getSchnorrKeysSignaturesList(schnorrKeysSignaturesListBytes);
        SchnorrPublicKey[] signingPublicKeys = validatorKeysUpdatesList.getSigningKeys();

        WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                sidechainIdFieldElement,
                epochNumber,
                CommonCircuit.getBackwardTransfers(bt),
                endCumulativeScTxCommTreeRootFe,
                ftMinAmount,
                btrFee,
                customFieldsElements
        );
        CreateProofResult proofAndQuality = NaiveThresholdSignatureWKeyRotation.createProof(validatorKeysUpdatesList,
                withdrawalCertificate, previousCertificateOption, signatures,
                signingPublicKeys.length, threshold, FieldElement.deserialize(genesisKeysRootHash), Optional.empty(),
                provingKeyPath, false, zk, true, true);

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFieldElement.freeFieldElement();
        Arrays.stream(validatorKeysUpdatesList.getSigningKeys()).forEach(SchnorrPublicKey::freePublicKey);
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
                               Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
                               byte[] genesisConstantBytes,
                               int sidechainCreationVersionNumber,
                               byte[] proof,
                               String verificationKeyPath) {
        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        List<FieldElement> customFieldsElements;
        FieldElement genesisConstant;
        boolean verificationResult;
        try (FieldElement constantFe = FieldElement.deserialize(constant)) {
            FieldElement sidechainIdFIeldElement = FieldElement.deserialize(sidechainId);
            customFieldsElements = prepareCustomFieldElements(customFields);
            genesisConstant = FieldElement.deserialize(genesisConstantBytes);

            WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                    sidechainIdFIeldElement,
                    epochNumber,
                    CommonCircuit.getBackwardTransfers(bt),
                    quality,
                    endCumulativeScTxCommTreeRootFe,
                    ftMinAmount,
                    btrFee,
                    customFieldsElements
            );

            Optional<WithdrawalCertificate> previousCertificateOption = previousEpochCertificateOption
                    .map(c -> CommonCircuit.createWithdrawalCertificate(c, SidechainCreationVersions.apply(sidechainCreationVersionNumber)));


            verificationResult = NaiveThresholdSignatureWKeyRotation.verifyProof(withdrawalCertificate, previousCertificateOption, genesisConstant, proof, verificationKeyPath);

            endCumulativeScTxCommTreeRootFe.freeFieldElement();
            sidechainIdFIeldElement.freeFieldElement();
            constantFe.freeFieldElement();
        }
        customFieldsElements.forEach(FieldElement::freeFieldElement);
        genesisConstant.freeFieldElement();
        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList, long threshold) {

        List<SchnorrPublicKey> signerPublicKeys = signerPublicKeysList.stream().map(key -> SchnorrPublicKey.deserialize(key)).
                collect(Collectors.toList());
        List<SchnorrPublicKey> masterPublicKeys = masterPublicKeysList.stream().map(key -> SchnorrPublicKey.deserialize(key)).
                collect(Collectors.toList());

        ValidatorKeysUpdatesList schnorrKeysSignaturesList = new ValidatorKeysUpdatesList(
                signerPublicKeys,
                masterPublicKeys,
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );

        // Note: sc-cryptolib return constant in LittleEndian
        FieldElement sysDataConstant;
        try {
            sysDataConstant = NaiveThresholdSignatureWKeyRotation.getConstant(schnorrKeysSignaturesList.getKeysRootHash(signerPublicKeys.size()), threshold);
        } catch (Exception e) {
            throw new IllegalArgumentException("Fail to get the Constant");
        }

        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();

        return sysDataConstantBytes;
    }

    @Override
    public List<byte[]> getCertificateCustomFields(List<byte[]> customFields) {
        List<FieldElement> fes = prepareCustomFieldElements(customFields);
        List<byte[]> fesBytes = fes.stream().map(FieldElement::serializeFieldElement).collect(Collectors.toList());
        fes.forEach(FieldElement::freeFieldElement);
        return fesBytes;
    }

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath, int customFieldsNum) {
        return NaiveThresholdSignatureWKeyRotation.setup(ProvingSystemType.COBOUNDARY_MARLIN, maxPks, customFieldsNum,
                Optional.of(supportedSegmentSize),
                provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }

    public byte[] generateKeysRootHash(List<byte[]> publicSignersKeysList, List<byte[]> publicMastersKeysList) {
        FieldElement fieldElement;
        try {
            try (ValidatorKeysUpdatesList validatorKeysUpdatesList = new ValidatorKeysUpdatesList(
                    publicSignersKeysList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList()),
                    publicMastersKeysList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList()),
                    publicSignersKeysList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList()),
                    publicMastersKeysList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList()),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>(),
                    new ArrayList<>()
            )) {
                fieldElement = validatorKeysUpdatesList.getUpdatedKeysRootHash(validatorKeysUpdatesList.getSigningKeys().length);
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        byte[] b =  fieldElement.serializeFieldElement();
        fieldElement.freeFieldElement();
        return b;
    }
}
