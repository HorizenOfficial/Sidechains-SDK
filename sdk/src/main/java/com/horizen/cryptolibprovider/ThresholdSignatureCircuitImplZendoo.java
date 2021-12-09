package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.librustsidechains.Constants;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.NaiveThresholdSigProof;
import com.horizen.certnative.CreateProofResult;
import com.horizen.utils.Pair;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitImplZendoo implements ThresholdSignatureCircuit {
    private static final SchnorrSignature signaturePlaceHolder = new SchnorrSignature();

    private static BackwardTransfer withdrawalRequestBoxToBackwardTransfer(WithdrawalRequestBox box) {
        return new BackwardTransfer(box.proposition().bytes(), box.value());
    }

    @Override
    public List<byte[]> splitUtxoMerkleTreeRoot(byte[] utxoMerkleTreeRoot) {
        List<FieldElement> fes = splitUtxoMerkleTreeRootToFieldElements(utxoMerkleTreeRoot);
        List<byte[]> fesBytes = fes.stream().map(fe -> fe.serializeFieldElement()).collect(Collectors.toList());
        fes.forEach(fe -> fe.freeFieldElement());
        return fesBytes;
    }

    // TODO: replace with sc-cryptolib implementation
    private List<FieldElement> splitUtxoMerkleTreeRootToFieldElements(byte[] utxoMerkleTreeRoot) {
        byte[] fe1Bytes = new byte[32];
        byte[] fe2Bytes = new byte[32];

        System.arraycopy(utxoMerkleTreeRoot, 0, fe1Bytes, 0, 16);
        System.arraycopy(utxoMerkleTreeRoot, 16, fe2Bytes, 0, 16);

        FieldElement fe1 = FieldElement.deserialize(fe1Bytes);
        FieldElement fe2 = FieldElement.deserialize(fe2Bytes);

        return Arrays.asList(fe1, fe2);
    }

    @Override
    public byte[] reconstructUtxoMerkleTreeRoot(byte[] fe1Bytes, byte[] fe2Bytes) {
        if(fe1Bytes.length != Constants.FIELD_ELEMENT_LENGTH() || fe2Bytes.length != Constants.FIELD_ELEMENT_LENGTH())
            return new byte[0];
        // TODO: replace with sc-cryptolib implementation
        byte[] utxoMerkleTreeRoot = new byte[32];
        System.arraycopy(fe1Bytes, 0, utxoMerkleTreeRoot, 0, 16);
        System.arraycopy(fe2Bytes, 0, utxoMerkleTreeRoot, 16, 16);
        return utxoMerkleTreeRoot;
    }

    @Override
    public byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt,
                                            byte[] sidechainId,
                                            int epochNumber,
                                            byte[] endCumulativeScTxCommTreeRoot,
                                            long btrFee,
                                            long ftMinAmount,
                                            byte[] utxoMerkleTreeRoot) {
        BackwardTransfer[] backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).toArray(BackwardTransfer[]::new);

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = splitUtxoMerkleTreeRootToFieldElements(utxoMerkleTreeRoot);

        FieldElement messageToSign = NaiveThresholdSigProof.createMsgToSign(backwardTransfers, sidechainIdFe,
                epochNumber, endCumulativeScTxCommTreeRootFe, btrFee, ftMinAmount, customFe);
        byte[] messageAsBytes = messageToSign.serializeFieldElement();

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        customFe.forEach(fe -> fe.freeFieldElement());
        messageToSign.freeFieldElement();

        return messageAsBytes;
    }

    @Override
    public Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                          byte[] sidechainId,
                                          int epochNumber,
                                          byte[] endCumulativeScTxCommTreeRoot,
                                          long btrFee,
                                          long ftMinAmount,
                                          byte[] utxoMerkleTreeRoot,
                                          List<Optional<byte[]>> schnorrSignatureBytesList,
                                          List<byte[]> schnorrPublicKeysBytesList,
                                          long threshold,
                                          String provingKeyPath,
                                          boolean checkProvingKey,
                                          boolean zk) {  // TODO: what is zk
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        List<SchnorrSignature> signatures = schnorrSignatureBytesList
                                                .stream()
                                                .map(signatureBytesOpt -> signatureBytesOpt.map(bytes -> SchnorrSignature.deserialize(bytes)).orElse(signaturePlaceHolder))
                                                .collect(Collectors.toList());

        List<SchnorrPublicKey> publicKeys =
                schnorrPublicKeysBytesList.stream().map(bytes -> SchnorrPublicKey.deserialize(bytes)).collect(Collectors.toList());

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = splitUtxoMerkleTreeRootToFieldElements(utxoMerkleTreeRoot);

        CreateProofResult proofAndQuality = NaiveThresholdSigProof.createProof(
                backwardTransfers, sidechainIdFe, epochNumber, endCumulativeScTxCommTreeRootFe, btrFee, ftMinAmount,
                signatures, publicKeys, threshold, customFe, provingKeyPath, checkProvingKey, zk);

        // TODO: actually it will be more efficient to pass byte arrays directly to the `createProof` and deserialize them to FEs inside. JNI calls cost a lot.
        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        publicKeys.forEach(SchnorrPublicKey::freePublicKey);
        signatures.forEach(SchnorrSignature::freeSignature);
        customFe.forEach(fe -> fe.freeFieldElement());

        return new Pair<>(proofAndQuality.getProof(), proofAndQuality.getQuality());
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt,
                               byte[] sidechainId,
                               int epochNumber,
                               byte[] endCumulativeScTxCommTreeRoot,
                               long btrFee,
                               long ftMinAmount,
                               byte[] utxoMerkleTreeRoot,
                               byte[] constant,
                               long quality, byte[] proof,
                               boolean checkProof,
                               String verificationKeyPath,
                               boolean checkVerificationKey) {
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement constantFe = FieldElement.deserialize(constant);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = splitUtxoMerkleTreeRootToFieldElements(utxoMerkleTreeRoot);

        boolean verificationResult = NaiveThresholdSigProof.verifyProof(backwardTransfers, sidechainIdFe, epochNumber,
                endCumulativeScTxCommTreeRootFe, btrFee, ftMinAmount, constantFe, quality, customFe, proof, checkProof,
                verificationKeyPath, checkVerificationKey);

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        constantFe.freeFieldElement();
        customFe.forEach(fe -> fe.freeFieldElement());

        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold){
        List<SchnorrPublicKey> schnorrPublicKeys = publicKeysList.stream().map(bytes -> SchnorrPublicKey.deserialize(bytes)).collect(Collectors.toList());

        // Note: sc-cryptolib return constant in LittleEndian
        FieldElement sysDataConstant = NaiveThresholdSigProof.getConstant(schnorrPublicKeys, threshold);
        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();
        schnorrPublicKeys.forEach(SchnorrPublicKey::freePublicKey);

        return sysDataConstantBytes;
    }

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath) {
        return NaiveThresholdSigProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, maxPks, CommonCircuit.customFieldsNumber,
                provingKeyPath, verificationKeyPath, CommonCircuit.maxProofPlusVkSize);
    }
}
