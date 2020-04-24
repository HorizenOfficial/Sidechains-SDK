package com.horizen.backwardtransfer;

import com.horizen.box.WithdrawalRequestBox;
import org.bouncycastle.pqc.math.linearalgebra.ByteUtils;

import java.util.EnumMap;
import java.util.List;


public class BackwardTransferFunctionsImpl implements BackwardTransferFunctions {
    @Override
    public EnumMap<KeyType, byte[]> generateSchnorrPublicAndSecretKeys(byte[] seed) {
        EnumMap<KeyType, byte[]> keys = new EnumMap<>(KeyType.class);
        keys.put(KeyType.PUBLIC, new byte[32]);
        keys.put(KeyType.SECRET, new byte[32]);
        return keys;
    }

    @Override
    public byte[] sign(byte[] secretKey, byte[] publicKey, byte[] message) {
        return new byte[32];
    }

    @Override
    public boolean verify(byte[] message, byte[] publicKey, byte[] signatureBytes) {
        return true;
    }

    @Override
    public byte[] generatePoseidonHash(List<byte[]> publicKeysList, long threshold) {
        byte [] init = publicKeysList.get(0);
        for (int i = 1; i < publicKeysList.size(); ++i) {
            init = ByteUtils.xor(init, publicKeysList.get(i));
        }
        return init;
    }

    @Override
    public byte[] createBackwardTransferMessage(List<WithdrawalRequestBox> bt, byte[] endWithdrawalEpochBlockHash, byte[] prevEndWithdrawalEpochBlockHash) {
        return new byte[32];
    }

    @Override
    public byte[] createProof(List<WithdrawalRequestBox> bt, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash, List<byte[]> schnorrSignatureBytesList, List<byte[]> schnorrPublicKeysBytesList, long threshold, String provingKeyPath) {
        return new byte[32];
    }

    @Override
    public Boolean verifyProof(List<WithdrawalRequestBox> bt, List<byte[]> schnorrPublicKeysBytesList, byte[] endEpochBlockHash, byte[] prevEndEpochBlockHash, long threshold, long quality, byte[] proof, String provingKeyPath) {
        return null;
    }
}
