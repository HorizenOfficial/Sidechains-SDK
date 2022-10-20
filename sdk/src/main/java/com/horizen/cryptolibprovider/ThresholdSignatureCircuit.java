package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuit {
    byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt,
                                     byte[] sidechainId,
                                     int epochNumber,
                                     byte[] endCumulativeScTxCommTreeRoot,
                                     long btrFee,
                                     long ftMinAmount,
                                     List<byte[]> customParameters);

    //None elements for schnorrSignatureBytesList if no secret key available, thus schnorrSignatureBytesList.size() == schnorrPublicKeysBytesList.size()
    //threshold is the same as in generateSysDataConstant
    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   byte[] sidechainId,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   List<byte[]> customParameters,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   List<byte[]> schnorrPublicKeysBytesList,
                                   long threshold,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk);

    Boolean verifyProof(List<WithdrawalRequestBox> bt,
                        byte[] sidechainId,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        List<byte[]> customFields,
                        byte[] constant,
                        long quality, byte[] proof,
                        boolean checkProof,
                        String verificationKeyPath,
                        boolean checkVerificationKey);

    byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold);

    boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath, int customFieldsNum);

    List<byte[]> getCertificateCustomFields(List<byte[]> customFields);

    byte[] reconstructUtxoMerkleTreeRoot(byte[] fe1Bytes, byte[] fe2Bytes);
}
