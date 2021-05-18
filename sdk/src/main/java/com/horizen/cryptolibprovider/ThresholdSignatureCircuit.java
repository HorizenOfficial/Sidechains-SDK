package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.librustsidechains.FieldElement;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuit {
    byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt,
                                     int epochNumber,
                                     byte[] endCumulativeScTxCommTreeRoot,
                                     long btrFee,
                                     long ftMinAmount);

    //None elements for schnorrSignatureBytesList if no secret key available, thus schnorrSignatureBytesList.size() == schnorrPublicKeysBytesList.size()
    //threshold is the same as in generateSysDataConstant
    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   int epochNumber,
                                   byte[] endCumulativeScTxCommTreeRoot,
                                   long btrFee,
                                   long ftMinAmount,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   List<byte[]> schnorrPublicKeysBytesList,
                                   long threshold,
                                   String provingKeyPath,
                                   boolean checkProvingKey,
                                   boolean zk); // todo check name

    Boolean verifyProof(List<WithdrawalRequestBox> bt,
                        int epochNumber,
                        byte[] endCumulativeScTxCommTreeRoot,
                        long btrFee,
                        long ftMinAmount,
                        byte[] constant,
                        long quality, byte[] proof,
                        boolean checkProof,
                        String verificationKeyPath,
                        boolean checkVerificationKey);

    byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold);

    boolean generateCoboundaryMarlinDLogKeys(String g1KeyPath);

    boolean generateCoboundaryMarlinSnarkKeys(long maxPks, String provingKeyPath, String verificationKeyPath);

    String getCoboundaryMarlinSnarkVerificationKeyHex(String verificationKeyPath);
}
