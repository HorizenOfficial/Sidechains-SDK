package com.horizen.cryptolibProvider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuit {
    //message are created on JNI side; For 0 epoch no certificates; For 1 epoch create cert for 1 epoch (get WR for 0 epoch, null); for N cert for N epoch (get WR N-1, WR N-2)
    byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt, byte[] endWithdrawalEpochBlockHash, byte[] prevEndWithdrawalEpochBlockHash);

    //null elements for schnorrSignatureBytesList if no secret key available, thus schnorrSignatureBytesList.size() == schnorrPublicKeysBytesList.size();
    //threshold is the same as in generatePoseidonHash
    //provingKey shall be generated during SC creation (how to generate it?)
    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   byte[] endEpochBlockHash,
                                   byte[] prevEndEpochBlockHash,
                                   List<byte[]> schnorrPublicKeysBytesList,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   long threshold,
                                   String provingKeyPath);

    Boolean verifyProof(List<WithdrawalRequestBox> bt, List<byte[]> schnorrPublicKeysBytesList, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash,
                        long threshold, long quality, byte[] proof, String verificationKeyPath);

    //For verifying schnorr public keys //sigproof threshold signature circuite
    byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold);
}
