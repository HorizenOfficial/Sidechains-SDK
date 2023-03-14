package com.horizen.cryptolibprovider.implementations;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.certnative.BackwardTransfer;
import com.horizen.certnative.CreateProofResult;
import com.horizen.certnative.NaiveThresholdSigProof;
import com.horizen.cryptolibprovider.CommonCircuit;
import com.horizen.cryptolibprovider.ThresholdSignatureCircuit;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.provingsystemnative.ProvingSystemType;
import com.horizen.schnorrnative.SchnorrPublicKey;
import com.horizen.schnorrnative.SchnorrSignature;
import com.horizen.utils.Pair;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class ThresholdSignatureCircuitImplZendoo implements ThresholdSignatureCircuit {
    // Note: supportedSegmentSize should correlate with the snark circuit complexity,
    // but is always less or equal the one defined in the MC network (maxSegmentSize).
    private static final int supportedSegmentSize = (1 << 17);

    @Override
    public List<byte[]> getCertificateCustomFields(Optional<byte[]> utxoMerkleTreeRoot) {
        List<FieldElement> fes = prepareCustomFieldElements(utxoMerkleTreeRoot);
        List<byte[]> fesBytes = fes.stream().map(FieldElement::serializeFieldElement).collect(Collectors.toList());
        fes.forEach(FieldElement::freeFieldElement);
        return fesBytes;
    }

    private List<FieldElement> prepareCustomFieldElements(Optional<byte[]> utxoMerkleTreeRoot)  {
        if (utxoMerkleTreeRoot.isPresent())   {
            return splitUtxoMerkleTreeRootToFieldElements(utxoMerkleTreeRoot.get());
        }
        else {
            return new ArrayList<>();
        }
    }

    private List<FieldElement> splitUtxoMerkleTreeRootToFieldElements(byte[] utxoMerkleTreeRoot) {
        FieldElement utxoMerkleTreeRootFe = FieldElement.deserialize(utxoMerkleTreeRoot);
        List<FieldElement> split = utxoMerkleTreeRootFe.splitAt(16);
        utxoMerkleTreeRootFe.freeFieldElement();

        return split;
    }

    @Override
    public byte[] reconstructUtxoMerkleTreeRoot(byte[] fe1Bytes, byte[] fe2Bytes) {
        FieldElement fe1 = FieldElement.deserialize(fe1Bytes);
        if(fe1 == null)
            return new byte[0];
        FieldElement fe2 = FieldElement.deserialize(fe2Bytes);
        if(fe2 == null) {
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
                                            Optional<byte[]> utxoMerkleTreeRoot) {

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = prepareCustomFieldElements(utxoMerkleTreeRoot);

        FieldElement messageToSign = NaiveThresholdSigProof.createMsgToSign(
                CommonCircuit.getBackwardTransfers(bt).toArray(BackwardTransfer[]::new), sidechainIdFe,
                epochNumber, endCumulativeScTxCommTreeRootFe, btrFee, ftMinAmount, customFe);
        byte[] messageAsBytes = messageToSign.serializeFieldElement();

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        customFe.forEach(FieldElement::freeFieldElement);
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
                                          Optional<byte[]> utxoMerkleTreeRoot,
                                          List<Optional<byte[]>> schnorrSignatureBytesList,
                                          List<byte[]> schnorrPublicKeysBytesList,
                                          long threshold,
                                          String provingKeyPath,
                                          boolean checkProvingKey,
                                          boolean zk) {
        List<SchnorrSignature> signatures = CommonCircuit.getSignatures(schnorrSignatureBytesList);

        List<SchnorrPublicKey> publicKeys =
                schnorrPublicKeysBytesList.stream().map(SchnorrPublicKey::deserialize).collect(Collectors.toList());

        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = prepareCustomFieldElements(utxoMerkleTreeRoot);

        CreateProofResult proofAndQuality = NaiveThresholdSigProof.createProof(
                CommonCircuit.getBackwardTransfers(bt), sidechainIdFe, epochNumber, endCumulativeScTxCommTreeRootFe,
                btrFee, ftMinAmount, signatures, publicKeys, threshold, customFe, Optional.of(supportedSegmentSize),
                provingKeyPath, checkProvingKey, zk);

        // TODO: actually it will be more efficient to pass byte arrays directly to the `createProof` and deserialize them to FEs inside. JNI calls cost a lot.
        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        publicKeys.forEach(SchnorrPublicKey::freePublicKey);
        signatures.forEach(SchnorrSignature::freeSignature);
        customFe.forEach(FieldElement::freeFieldElement);

        return new Pair<>(proofAndQuality.getProof(), proofAndQuality.getQuality());
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt,
                               byte[] sidechainId,
                               int epochNumber,
                               byte[] endCumulativeScTxCommTreeRoot,
                               long btrFee,
                               long ftMinAmount,
                               Optional<byte[]> utxoMerkleTreeRoot,
                               byte[] constant,
                               long quality, byte[] proof,
                               boolean checkProof,
                               String verificationKeyPath,
                               boolean checkVerificationKey) {
        FieldElement endCumulativeScTxCommTreeRootFe = FieldElement.deserialize(endCumulativeScTxCommTreeRoot);
        FieldElement constantFe = FieldElement.deserialize(constant);
        FieldElement sidechainIdFe = FieldElement.deserialize(sidechainId);
        List<FieldElement> customFe = prepareCustomFieldElements(utxoMerkleTreeRoot);

        boolean verificationResult = NaiveThresholdSigProof.verifyProof(CommonCircuit.getBackwardTransfers(bt),
                sidechainIdFe, epochNumber, endCumulativeScTxCommTreeRootFe, btrFee, ftMinAmount, constantFe, quality,
                customFe, proof, checkProof, verificationKeyPath, checkVerificationKey);

        endCumulativeScTxCommTreeRootFe.freeFieldElement();
        sidechainIdFe.freeFieldElement();
        constantFe.freeFieldElement();
        customFe.forEach(FieldElement::freeFieldElement);

        return verificationResult;
    }


    @Override
    public byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold){
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
