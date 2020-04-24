package com.horizen.backwardtransfer;

import com.horizen.box.WithdrawalRequestBox;

import java.util.EnumMap;
import java.util.List;

public interface BackwardTransferFunctions {
    enum KeyType {
        SECRET,
        PUBLIC
    }

    //JNI doesn't support seed at this moment
    EnumMap<KeyType, byte[]> generateSchnorrPublicAndSecretKeys(byte[] seed);

    //message is taken form createBackwardTransferMessage
    byte[] sign(byte[] secretKey, byte[] publicKey, byte[] message);

    //looks like is not used at all in SDK
    boolean verify(byte[] message, byte[] publicKey, byte[] signatureBytes);

    //For verifying schnorr public keys
    byte[] generatePoseidonHash(List<byte[]> publicKeysList, long threshold);

    //message are created on JNI side; For 0 epoch no certificates; For 1 epoch create cert for 1 epoch (get WR for 0 epoch, null); for N cert for N epoch (get WR N-1, WR N-2)
    byte[] createBackwardTransferMessage(List<WithdrawalRequestBox> bt, byte[] endWithdrawalEpochBlockHash, byte[] prevEndWithdrawalEpochBlockHash);

    //null elements for schnorrSignatureBytesList if no secret key available, thus schnorrSignatureBytesList.size() == schnorrPublicKeysBytesList.size();
    //threshold is the same as in generatePoseidonHash
    //provingKey shall be generated during SC creation (how to generate it?)
    byte[] createProof(List<WithdrawalRequestBox> bt, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash,
                       List<byte[]> schnorrSignatureBytesList, List<byte[]> schnorrPublicKeysBytesList, long threshold, String provingKeyPath);

    Boolean verifyProof(List<WithdrawalRequestBox> bt, List<byte[]> schnorrPublicKeysBytesList, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash,
                        long threshold, long quality, byte[] proof, String provingKeyPath);
}
