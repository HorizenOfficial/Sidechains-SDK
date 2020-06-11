package com.horizen.cryptolibprovider;

import com.horizen.box.WithdrawalRequestBox;
import com.horizen.utils.Pair;

import java.util.List;
import java.util.Optional;

public interface ThresholdSignatureCircuit {
    //message are created on JNI side. For 0 epoch no certificates; For 1 epoch create cert for 1 epoch (get WR for 0 epoch, null); for N cert for N epoch (get WR N-1, WR N-2)
    byte[] generateMessageToBeSigned(List<WithdrawalRequestBox> bt, byte[] endWithdrawalEpochBlockHash, byte[] prevEndWithdrawalEpochBlockHash);

    //None elements for schnorrSignatureBytesList if no secret key available, thus schnorrSignatureBytesList.size() == schnorrPublicKeysBytesList.size()
    //threshold is the same as in generateSysDataConstant
    Pair<byte[], Long> createProof(List<WithdrawalRequestBox> bt,
                                   byte[] endEpochBlockHash,
                                   byte[] prevEndEpochBlockHash,
                                   List<byte[]> schnorrPublicKeysBytesList,
                                   List<Optional<byte[]>> schnorrSignatureBytesList,
                                   long threshold,
                                   String provingKeyPath);

    Boolean verifyProof(List<WithdrawalRequestBox> bt, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash, long quality, byte[] proof, byte[] sysDataConstant, String verificationKeyPath);

    byte[] generateSysDataConstant(List<byte[]> publicKeysList, long threshold);

    int sysDataConstantLength();

    int proofSizeLength();

    int certVkSize();
}
