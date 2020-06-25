package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.sigproofnative.BackwardTransfer;
import com.horizen.sigproofnative.CreateProofResult;
import com.horizen.sigproofnative.NaiveThresholdSigProof;
import com.horizen.utils.BytesUtils;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitImplZendoo implements ThresholdSignatureCircuit {
    private static final SchnorrSignature signaturePlaceHolder = new SchnorrSignature();

    private static BackwardTransfer withdrawalRequestBoxToBackwardTransfer(WithdrawalRequestBox box) {
        return new BackwardTransfer(box.proposition().bytes(), box.value());
    }

    @Override
    public byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt, byte[] endWithdrawalEpochBlockHash, byte[] prevEndWithdrawalEpochBlockHash) {
        BackwardTransfer[] backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).toArray(BackwardTransfer[]::new);
        FieldElement messageToSign = NaiveThresholdSigProof.createMsgToSign(backwardTransfers, endWithdrawalEpochBlockHash, prevEndWithdrawalEpochBlockHash);
        byte[] messageAsBytes = messageToSign.serializeFieldElement();

        messageToSign.freeFieldElement();
        return messageAsBytes;
    }

    @Override
    public Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                          byte[] endEpochBlockHash,
                                          byte[] prevEndEpochBlockHash,
                                          List<byte[]> schnorrPublicKeysBytesList,
                                          List<Optional<byte[]>> schnorrSignatureBytesList,
                                          long threshold,
                                          String provingKeyPath){
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        List<SchnorrSignature> signatures = schnorrSignatureBytesList
                                                .stream()
                                                .map(signatureBytesOpt -> signatureBytesOpt.map(SchnorrSignature::deserialize).orElse(signaturePlaceHolder))
                                                .collect(Collectors.toList());

        List<SchnorrPublicKey> publicKeys =
                schnorrPublicKeysBytesList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        CreateProofResult proofAndQuality = NaiveThresholdSigProof.createProof(
                backwardTransfers, endEpochBlockHash, prevEndEpochBlockHash, signatures, publicKeys, threshold, provingKeyPath);

        publicKeys.forEach(SchnorrPublicKey::freePublicKey);
        signatures.forEach(SchnorrSignature::freeSignature);

        return new Pair<>(proofAndQuality.getProof(), proofAndQuality.getQuality());
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash, long quality, byte[] proof, byte[] sysDataConstant, String verificationKeyPath) {
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        FieldElement constant = FieldElement.deserialize(sysDataConstant);
        boolean verificationResult =
                NaiveThresholdSigProof.verifyProof(backwardTransfers, endEpochBlockHash, prevEndEpochBlockHash, constant, quality, proof, verificationKeyPath);

        constant.freeFieldElement();

        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold){
        List<SchnorrPublicKey> schnorrPublicKeys = publicKeysList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        FieldElement sysDataConstant = NaiveThresholdSigProof.getConstant(schnorrPublicKeys, threshold);
        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();
        schnorrPublicKeys.forEach(SchnorrPublicKey::freePublicKey);

        return sysDataConstantBytes;
    }

    @Override
    public int sysDataConstantLength() {
        return PoseidonHash.HASH_LENGTH;
    }

    @Override
    public int proofSizeLength() {
        return 771; //@TODO take it from JNI side
    }

    @Override
    public int certVkSize() {
        return 1544; //@TODO take it from JNI side
    }
}
