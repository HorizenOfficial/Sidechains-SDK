package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.poseidonnative.PoseidonHash;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.sigproofnative.BackwardTransfer;
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

        //Proof creation will return quality as well, @TODO put it in result pair
        byte[] proofBytes = NaiveThresholdSigProof.createProof(
                backwardTransfers, endEpochBlockHash, prevEndEpochBlockHash, signatures, publicKeys, threshold, provingKeyPath);

        publicKeys.forEach(SchnorrPublicKey::freePublicKey);
        signatures.forEach(SchnorrSignature::freeSignature);

        return new Pair<>(proofBytes, 0L);
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt, List<byte[]> schnorrPublicKeysBytesList, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash, long threshold, long quality, byte[] proof, String verificationKeyPath) {
        List<BackwardTransfer> backwardTransfers =
                bt.stream().map(ThresholdSignatureCircuitImplZendoo::withdrawalRequestBoxToBackwardTransfer).collect(Collectors.toList());

        List<SchnorrPublicKey> publicKeys =
                schnorrPublicKeysBytesList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        boolean verificationResult = NaiveThresholdSigProof.verifyProof(
                backwardTransfers, publicKeys, endEpochBlockHash, prevEndEpochBlockHash, threshold, quality, proof, verificationKeyPath);

        publicKeys.forEach(SchnorrPublicKey::freePublicKey);

        return verificationResult;
    }

    @Override
    public byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold){
        FieldElement thresholdFieldElement = FieldElement.createFromLong(threshold);
        List<FieldElement> fieldElements = publicKeysList.stream().map(FieldElementUtils::messageToFieldElement).collect(Collectors.toList());
        fieldElements.add(thresholdFieldElement);

        FieldElement sysDataConstant = PoseidonHash.computeHash(fieldElements.toArray(new FieldElement[0]));
        byte[] sysDataConstantBytes = sysDataConstant.serializeFieldElement();

        sysDataConstant.freeFieldElement();
        fieldElements.forEach(FieldElement::freeFieldElement);

        return sysDataConstantBytes;
    }


}
