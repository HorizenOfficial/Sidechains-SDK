package io.horizen.account.abi;

import io.horizen.account.state.ExecutionRevertedException;
import io.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;
import sparkz.crypto.hash.Keccak256;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

public class ABIUtil {

    public static final int METHOD_ID_LENGTH = 4;

    public static byte[] getArgumentsFromData(byte[] data) throws ExecutionRevertedException {
        return getSlice(data, METHOD_ID_LENGTH, data.length);
    }

    public static String getFunctionSignature(byte[] data) throws ExecutionRevertedException {
        return BytesUtils.toHexString(getSlice(data, 0, METHOD_ID_LENGTH));
    }

    private static byte[] getSlice(byte[] data, int from, int to) throws ExecutionRevertedException {
        if (data.length < METHOD_ID_LENGTH) {
            throw new ExecutionRevertedException("Data length " + data.length + " must be >= " + METHOD_ID_LENGTH);
        }
        return Arrays.copyOfRange(data, from, to);
    }

    public static String getABIMethodId(String methodSig) {
        return Numeric.toHexStringNoPrefix(Arrays.copyOfRange(((byte[]) Keccak256.hash(methodSig.getBytes(StandardCharsets.UTF_8))), 0, METHOD_ID_LENGTH));
    }

}

