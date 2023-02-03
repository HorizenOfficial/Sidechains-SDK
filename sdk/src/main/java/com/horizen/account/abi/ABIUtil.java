package com.horizen.account.abi;

import com.horizen.account.state.ExecutionFailedException;
import com.horizen.utils.BytesUtils;
import org.web3j.utils.Numeric;
import sparkz.crypto.hash.Keccak256;

import java.util.Arrays;

public class ABIUtil {

    public static final int METHOD_ID_LENGTH = 4;

    public static byte[] getArgumentsFromData(byte[] data) throws ExecutionFailedException {
        return getSlice(data, METHOD_ID_LENGTH, data.length);
    }

    public static String getFunctionSignature(byte[] data) throws ExecutionFailedException {
        return BytesUtils.toHexString(getSlice(data, 0, METHOD_ID_LENGTH));
    }

    private static byte[] getSlice(byte[] data, int from, int to) throws ExecutionFailedException {
        if (data.length < METHOD_ID_LENGTH) {
            throw new ExecutionFailedException("Data length " + data.length + " must be >= " + METHOD_ID_LENGTH);
        }
        return Arrays.copyOfRange(data, from, to);
    }

    public static String getABIMethodId(String methodSig) {
        return Numeric.toHexStringNoPrefix(Arrays.copyOfRange(((byte[]) Keccak256.hash(methodSig.getBytes())), 0, METHOD_ID_LENGTH));
    }

}

