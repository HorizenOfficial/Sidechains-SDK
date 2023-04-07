package io.horizen.cryptolibprovider.implementations;

import io.horizen.block.SidechainCreationVersions;
import io.horizen.block.WithdrawalEpochCertificate;
import io.horizen.certificatesubmitter.keys.SchnorrKeysSignatures;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.CreateProofResult;
import com.horizen.certnative.NaiveThresholdSignatureWKeyRotation;
import com.horizen.certnative.WithdrawalCertificate;
import io.horizen.cryptolibprovider.CommonCircuit;
import io.horizen.cryptolibprovider.ThresholdSignatureCircuitWithKeyRotation;
import io.horizen.cryptolibprovider.utils.FieldElementUtils;
import com.horizen.librustsidechains.FieldElement;
import io.horizen.proof.SchnorrProof;
import io.horizen.proposition.SchnorrProposition;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.schnorrnative.ValidatorKeysUpdatesList;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.Pair;
import scala.Option;
import scala.collection.Seq;
import java.util.*;
import java.util.stream.Collectors;

import static io.horizen.cryptolibprovider.CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION;

public class ThresholdSignatureCircuitWithKeyRotationImplZendoo implements ThresholdSignatureCircuitWithKeyRotation {
    // Note: supportedSegmentSize should correlate with the snark circuit complexity,
    // but is always less or equal the one defined in the MC network (maxSegmentSize).
    private static final int supportedSegmentSize = (1 << 18);

    @Override
    public List<byte[]> getCertificateCustomFields(List<byte[]> elementsToInsert) {
        // Create an array with zero field elements. They are just a placeholders for future needs.
        ArrayList<byte[]> customFields = new ArrayList<>(Collections.nCopies(
                CommonCircuit.CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION, new byte[FieldElementUtils.fieldElementLength()]));
        // Set genesis key root hash as a first item.
        for (int index = 0; index < elementsToInsert.size(); index++)
            customFields.set(index, elementsToInsert.get(index));
        return customFields;
    }


    private List<FieldElement> prepareCustomFieldElements(List<byte[]> customFields) {
        List<byte[]> certificateCustomFields = getCertificateCustomFields(customFields);
        Iterator<byte[]> iterator = certificateCustomFields.iterator();
        List<FieldElement> fieldElements = new ArrayList<>();
        while (iterator.hasNext()) {
            byte[] fieldBytes = iterator.next();
            fieldElements.add(FieldElement.deserialize(fieldBytes));
        }
        return fieldElements;
    }

    @Override
    public byte[] generateMessageToBeSigned(List<BackwardTransfer> bt,
                                            byte[] sidechainId,
                                            int epochNumber,
                                            byte[] endCumulativeScTxCommTreeRoot,
                                            long btrFee,
                                            long ftMinAmount,
                                            List<byte[]> customFields) {

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        List<FieldElement> customFieldElements;
        FieldElement messageToSign;
        byte[] messageAsBytes;
        try (FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId)) {
            customFieldElements = prepareCustomFieldElements(customFields);
            WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                    FieldElement.deserialize(sidechainId),
                    epochNumber,
                    bt,
                    endCumulativeScTxCommTreeRootFe,
                    ftMinAmount,
                    btrFee,
                    customFieldElements
            );
            messageToSign = NaiveThresholdSignatureWKeyRotation.createMsgToSign(withdrawalCertificate);
            messageAsBytes = messageToSign.serializeFieldElement();

            withdrawalCertificate.getScId().freeFieldElement();
            withdrawalCertificate.getMcbScTxsCom().freeFieldElement();
            Arrays.stream(withdrawalCertificate.getCustomFields()).forEach(FieldElement::freeFieldElement);

            endCumulativeScTxCommTreeRootFe.freeFieldElement();
            sidechainIdFe.freeFieldElement();
        }

        customFieldElements.forEach(FieldElement::freeFieldElement);
        messageToSign.freeFieldElement();

        return messageAsBytes;
    }

    @Override
    public Pair<byte[], Long> createProof
            (List<BackwardTransfer> bt,
             byte[] sidechainId,
             int epochNumber,
             byte[] endCumulativeScTxCommTreeRoot,
             long btrFee,
             long ftMinAmount,
             List<Optional<byte[]>> schnorrSignatureBytesList,
             SchnorrKeysSignatures schnorrKeysSignatures,
             long threshold,
             Optional<WithdrawalEpochCertificate> previousEpochCertificateOption,
             int sidechainCreationVersionNumber,
             byte[] genesisKeysRootHash,
             List<byte[]> customFields,
             String provingKeyPath,
             boolean checkProvingKey,
             boolean zk
            ) {
        List<SchnorrSignature> signatures = CommonCircuit.getSignatures(schnorrSignatureBytesList);

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFieldElement = FieldElement.deserialize(sidechainId);

        customFields.set(0, getSchnorrKeysHash(schnorrKeysSignatures));
        List<FieldElement> customFieldsElements = prepareCustomFieldElements(customFields);

        Optional<WithdrawalCertificate> previousCertificateOption = previousEpochCertificateOption
                .map(c -> CommonCircuit.createWithdrawalCertificate(c, SidechainCreationVersions.apply(sidechainCreationVersionNumber)));

        ValidatorKeysUpdatesList validatorKeysUpdatesList = getSchnorrKeysSignaturesList(schnorrKeysSignatures);
        SchnorrPublicKey[] signingPublicKeys = validatorKeysUpdatesList.getSigningKeys();

        WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                sidechainIdFieldElement,
                epochNumber,
                bt,
                endCumulativeScTxCommTreeRootFe,
                ftMinAmount,
                btrFee,
                customFieldsElements
        );
        CreateProofResult proofAndQuality = null;
        try {
            proofAndQuality = NaiveThresholdSignatureWKeyRotation.createProof(validatorKeysUpdatesList,
                    withdrawalCertificate, previousCertificateOption, signatures,
                    signingPublicKeys.length, threshold, FieldElement.deserialize(genesisKeysRootHash), Optional.of(supportedSegmentSize),
                    provingKeyPath, checkProvingKey, zk);
        } catch (Exception e) {
            e.printStackTrace();
        }

        previousCertificateOption.ifPresent(previousCertificate -> {
            previousCertificate.getScId().freeFieldElement();
            previousCertificate.getMcbScTxsCom().freeFieldElement();
            Arrays.stream(previousCertificate.getCustomFields()).forEach(FieldElement::freeFieldElement);
        });
        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFieldElement.freeFieldElement();
        Arrays.stream(validatorKeysUpdatesList.getSigningKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(validatorKeysUpdatesList.getMasterKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(validatorKeysUpdatesList.getUpdatedSigningKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(validatorKeysUpdatesList.getUpdatedMasterKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(validatorKeysUpdatesList.getUpdatedSigningKeysSkSignatures()).forEach(SchnorrSignature::freeSignature);
        Arrays.stream(validatorKeysUpdatesList.getUpdatedSigningKeysMkSignatures()).forEach(SchnorrSignature::freeSignature);
        Arrays.stream(validatorKeysUpdatesList.getUpdatedMasterKeysSkSignatures()).forEach(SchnorrSignature::freeSignature);
        Arrays.stream(validatorKeysUpdatesList.getUpdatedMasterKeysMkSignatures()).forEach(SchnorrSignature::freeSignature);
        withdrawalCertificate.getScId().freeFieldElement();
        withdrawalCertificate.getMcbScTxsCom().freeFieldElement();
        Arrays.stream(withdrawalCertificate.getCustomFields()).forEach(FieldElement::freeFieldElement);
        signatures.forEach(SchnorrSignature::freeSignature);
        customFieldsElements.forEach(FieldElement::freeFieldElement);

        return new Pair<>(proofAndQuality.getProof(), proofAndQuality.getQuality());
    }

    @Override
    public Boolean verifyProof(List<BackwardTransfer> bt,
                               byte[] sidechainId,
                               int epochNumber,
                               byte[] endCumulativeScTxCommTreeRoot,
                               long btrFee,
                               long ftMinAmount,
                               long quality,
                               Optional<WithdrawalCertificate> previousEpochCertificateOption, // todo: use WithdrawalEpochCertificate same as in createProof
                               byte[] genesisConstantBytes,
                               int sidechainCreationVersionNumber,
                               byte[] proof,
                               List<byte[]> customFields,
                               String verificationKeyPath) {
        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        boolean verificationResult = false;
        List<FieldElement> customFieldsElements = prepareCustomFieldElements(customFields);
        FieldElement genesisConstant = FieldElement.deserialize(genesisConstantBytes);
        FieldElement sidechainIdFieldElement = FieldElement.deserialize(sidechainId);

        WithdrawalCertificate withdrawalCertificate = new WithdrawalCertificate(
                sidechainIdFieldElement,
                epochNumber,
                bt,
                quality,
                endCumulativeScTxCommTreeRootFe,
                ftMinAmount,
                btrFee,
                customFieldsElements
        );

        try {
            verificationResult = NaiveThresholdSignatureWKeyRotation.verifyProof(withdrawalCertificate, previousEpochCertificateOption, genesisConstant, proof, verificationKeyPath);
        } catch (Exception e) {
            e.printStackTrace();
        }

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFieldElement.freeFieldElement();
        withdrawalCertificate.getScId().freeFieldElement();
        withdrawalCertificate.getMcbScTxsCom().freeFieldElement();
        Arrays.stream(withdrawalCertificate.getCustomFields()).forEach(FieldElement::freeFieldElement);
        customFieldsElements.forEach(FieldElement::freeFieldElement);
        genesisConstant.freeFieldElement();
        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList, long threshold) throws Exception {

        SchnorrPublicKey[] signerPublicKeys = signerPublicKeysList.stream().map(SchnorrPublicKey::deserialize).toArray(SchnorrPublicKey[]::new);
        SchnorrPublicKey[] masterPublicKeys = masterPublicKeysList.stream().map(SchnorrPublicKey::deserialize).toArray(SchnorrPublicKey[]::new);

        FieldElement hash = ValidatorKeysUpdatesList.getInputKeysRootHash(signerPublicKeys, masterPublicKeys, signerPublicKeys.length);

        // Note: sc-cryptolib return constant in LittleEndian
        FieldElement sysDataConstant = NaiveThresholdSignatureWKeyRotation.getConstant(hash, threshold);
        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();
        Arrays.stream(signerPublicKeys).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(masterPublicKeys).forEach(SchnorrPublicKey::freePublicKey);
        hash.freeFieldElement();
        return sysDataConstantBytes;
    }

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath) throws Exception {
        return NaiveThresholdSignatureWKeyRotation.setup(ProvingSystemType.COBOUNDARY_MARLIN, maxPks,
                CUSTOM_FIELDS_NUMBER_WITH_DISABLED_CSW_WITH_KEY_ROTATION, Optional.of(supportedSegmentSize),
                provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }

    public byte[] generateKeysRootHash(List<byte[]> signerPublicKeysList, List<byte[]> masterPublicKeysList) {
        SchnorrPublicKey[] signerPublicKeys = signerPublicKeysList.stream().map(SchnorrPublicKey::deserialize).toArray(SchnorrPublicKey[]::new);
        SchnorrPublicKey[] masterPublicKeys = masterPublicKeysList.stream().map(SchnorrPublicKey::deserialize).toArray(SchnorrPublicKey[]::new);

        FieldElement hash;
        try {
            hash = ValidatorKeysUpdatesList.getInputKeysRootHash(signerPublicKeys, masterPublicKeys, signerPublicKeys.length);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        byte[] b = hash.serializeFieldElement();
        hash.freeFieldElement();
        Arrays.stream(signerPublicKeys).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(masterPublicKeys).forEach(SchnorrPublicKey::freePublicKey);
        return b;
    }

    public ValidatorKeysUpdatesList getSchnorrKeysSignaturesList(SchnorrKeysSignatures schnorrKeysSignatures) {
        return new ValidatorKeysUpdatesList(
                byteArrayToKeysList(schnorrKeysSignatures.schnorrSigners()),
                byteArrayToKeysList(schnorrKeysSignatures.schnorrMasters()),
                byteArrayToKeysList(schnorrKeysSignatures.newSchnorrSigners()),
                byteArrayToKeysList(schnorrKeysSignatures.newSchnorrMasters()),
                byteArrayToSignaturesList(schnorrKeysSignatures.updatedSigningKeysSkSignatures()),
                byteArrayToSignaturesList(schnorrKeysSignatures.updatedSigningKeysMkSignatures()),
                byteArrayToSignaturesList(schnorrKeysSignatures.updatedMasterKeysSkSignatures()),
                byteArrayToSignaturesList(schnorrKeysSignatures.updatedMasterKeysMkSignatures()),
                schnorrKeysSignatures.schnorrSigners().size()
        );
    }

    public byte[] getSchnorrKeysHash(SchnorrKeysSignatures schnorrKeysSignatures) {
        ValidatorKeysUpdatesList actualKeys = getSchnorrKeysSignaturesList(schnorrKeysSignatures);
        FieldElement hash;
        try {
            hash = actualKeys.getUpdatedKeysRootHash();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        byte[] serializedHash = hash.serializeFieldElement();
        hash.freeFieldElement();
        Arrays.stream(actualKeys.getSigningKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(actualKeys.getMasterKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(actualKeys.getUpdatedSigningKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(actualKeys.getUpdatedMasterKeys()).forEach(SchnorrPublicKey::freePublicKey);
        Arrays.stream(actualKeys.getUpdatedSigningKeysSkSignatures()).forEach(SchnorrSignature::freeSignature);
        Arrays.stream(actualKeys.getUpdatedSigningKeysMkSignatures()).forEach(SchnorrSignature::freeSignature);
        Arrays.stream(actualKeys.getUpdatedMasterKeysSkSignatures()).forEach(SchnorrSignature::freeSignature);
        Arrays.stream(actualKeys.getUpdatedMasterKeysMkSignatures()).forEach(SchnorrSignature::freeSignature);
        return serializedHash;
    }

    private enum KeyType {SIGNING, MASTER}
    private FieldElement getMsgToSign(SchnorrPublicKey pubKey, int epochNumber, FieldElement scIdFe, KeyType type) throws Exception {
        switch (type){
            case SIGNING:
                return NaiveThresholdSignatureWKeyRotation.getMsgToSignForSigningKeyUpdate(pubKey, epochNumber, scIdFe);
            case MASTER:
                return NaiveThresholdSignatureWKeyRotation.getMsgToSignForMasterKeyUpdate(pubKey, epochNumber, scIdFe);
            default:
                throw new IllegalArgumentException("Invalid key type: " + type);
        }
    }

    private byte[] getMsgToSignForKeyUpdate(byte[] newKeyBytes, int epochNumber, byte[] sidechainId, KeyType type) {
        byte[] messageAsBytes;
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        SchnorrPublicKey newKey = SchnorrPublicKey.deserialize(newKeyBytes);
        try {
            if (sidechainIdFe != null && newKey != null) {

                FieldElement messageToSign = getMsgToSign(newKey, epochNumber, sidechainIdFe, type);

                if (messageToSign != null) {
                    messageAsBytes = messageToSign.serializeFieldElement();
                    messageToSign.freeFieldElement();
                } else {
                    throw new IllegalArgumentException("Could not get a valid message to sign from key (type " + type + "): " + BytesUtils.toHexString(newKeyBytes));
                }
            } else {
                throw new IllegalArgumentException(
                        "Invalid deserialized obj: sidechainId=" +  BytesUtils.toHexString(sidechainId) +
                                " / newKeyBytes=" + BytesUtils.toHexString(newKeyBytes));
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            if (sidechainIdFe != null)
                sidechainIdFe.freeFieldElement();
            if (newKey != null)
                newKey.freePublicKey();
        }
        return messageAsBytes;
    }

    @Override
    public byte[] getMsgToSignForSigningKeyUpdate(byte[] newSigningKeyBytes, int epochNumber, byte[] sidechainId) {
        return getMsgToSignForKeyUpdate(newSigningKeyBytes, epochNumber, sidechainId, KeyType.SIGNING);
    }

    @Override
    public byte[] getMsgToSignForMasterKeyUpdate(byte[] newMasterKeyBytes, int epochNumber, byte[] sidechainId) {
        return getMsgToSignForKeyUpdate(newMasterKeyBytes, epochNumber, sidechainId, KeyType.MASTER);
    }

    private static List<SchnorrPublicKey> byteArrayToKeysList(Seq<SchnorrProposition> schnorrPublicKeysBytesList) {
        return scala.collection.JavaConverters.seqAsJavaList(schnorrPublicKeysBytesList)
                .stream().map(SchnorrProposition::pubKeyBytes).map(SchnorrPublicKey::deserialize).collect(Collectors.toList());
    }


    private static List<SchnorrSignature> byteArrayToSignaturesList(Seq<Option<SchnorrProof>> schnorrSignaturesBytesSeq) {
        return scala.collection.JavaConverters.seqAsJavaList(schnorrSignaturesBytesSeq).stream().map(b -> {
            if (b.isDefined()) {
                return SchnorrSignature.deserialize(b.get().bytes());
            } else {
                return new SchnorrSignature();
            }
        }).collect(Collectors.toList());
    }
}
