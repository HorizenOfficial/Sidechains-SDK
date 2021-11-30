package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.NaiveThresholdSigProof;
import com.horizen.provingsystemnative.CreateProofResult;
import com.horizen.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitImplZendoo implements ThresholdSignatureCircuit {
    private static final SchnorrSignature signaturePlaceHolder = new SchnorrSignature();

    private static BackwardTransfer withdrawalRequestBoxToBackwardTransfer(WithdrawalRequestBox box) {
        return new BackwardTransfer(box.proposition().bytes(), box.value());
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
        List<FieldElement> customFe = new ArrayList<>();
        customFe.add(FieldElement.deserialize(utxoMerkleTreeRoot));

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
        List<FieldElement> customFe = new ArrayList<>();
        customFe.add(FieldElement.deserialize(utxoMerkleTreeRoot));

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
        List<FieldElement> customFe = new ArrayList<>();
        customFe.add(FieldElement.deserialize(utxoMerkleTreeRoot));

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

    // Result data max size values are the same as in MC
    private static final int maxProofPlusVkSize = 9 * 1024;
    private static final int thresholdSignatureCustomFieldsNum = 1;

    @Override
    public boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath) {
        return NaiveThresholdSigProof.setup(ProvingSystemType.COBOUNDARY_MARLIN, maxPks, thresholdSignatureCustomFieldsNum, provingKeyPath, verificationKeyPath, maxProofPlusVkSize);
    }
}
