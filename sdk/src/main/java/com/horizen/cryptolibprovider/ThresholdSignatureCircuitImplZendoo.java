package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.sigproofnative.BackwardTransfer;
import com.horizen.sigproofnative.CreateProofResult;
import com.horizen.sigproofnative.NaiveThresholdSigProof;
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
    public Boolean verifyProof(List<WithdrawalRequestBox> bt, List<byte[]> schnorrPublicKeys, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash, long threshold, long quality, byte[] proof, String verificationKeyPath) {
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());
        List<SchnorrPublicKey> spk = schnorrPublicKeys.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        return NaiveThresholdSigProof.verifyProof(backwardTransfers, spk, endEpochBlockHash, prevEndEpochBlockHash, threshold, quality, proof, verificationKeyPath);
    }
}
