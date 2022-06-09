package com.horizen.evm;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.horizen.evm.interop.*;
import com.horizen.evm.utils.Hash;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.math.BigInteger;

final class LibEvm {
    private interface LibEvmInterface extends Library {
        void Free(Pointer ptr);

        JsonPointer Invoke(String method, JsonPointer args);
    }

    private static final LibEvmInterface instance;

    static {
        var os = System.getProperty("os.name").toLowerCase();
        String libExtension;
        if (os.contains("mac os")) {
            libExtension = "dylib";
        } else if (os.contains("windows")) {
            libExtension = "dll";
        } else {
            libExtension = "so";
        }
        var lib = "libevm." + libExtension;
        instance = Native.load(lib, LibEvmInterface.class);
    }

    private LibEvm() {
        // prevent instantiation of this class
    }

    static void Free(Pointer ptr) {
        instance.Free(ptr);
    }

    private static class InteropResult<R> {
        public String error;
        public R result;

        public boolean isError() {
            return !error.isEmpty();
        }

        @Override
        public String toString() {
            if (!error.isEmpty()) {
                return String.format("error: %s", error);
            }
            return "success";
        }
    }

    private static <R> R invoke(String method, JsonPointer args, Class<R> responseType) throws Exception {
        var json = instance.Invoke(method, args);
        // build type information to deserialize to generic type InteropResult<R>
        var type = TypeFactory.defaultInstance().constructParametricType(InteropResult.class, responseType);
        InteropResult<R> response = json.deserialize(type);
        if (response.isError()) {
            throw new Exception(response.error);
        }
        return response.result;
    }

    private static void invoke(String method, JsonPointer args) throws Exception {
        invoke(method, args, Void.class);
    }

    private static void invoke(String method) throws Exception {
        invoke(method, null, Void.class);
    }

    public static void openMemoryDB() throws Exception {
        invoke("OpenMemoryDB");
    }

    public static void openLevelDB(String path) throws Exception {
        invoke("OpenLevelDB", new LevelDBParams(path));
    }

    public static void closeDatabase() throws Exception {
        invoke("CloseDatabase");
    }

    public static int stateOpen(byte[] root) throws Exception {
        return invoke("StateOpen", new OpenStateParams(root), int.class);
    }

    public static void stateClose(int handle) throws Exception {
        invoke("StateClose", new HandleParams(handle));
    }

    public static byte[] stateIntermediateRoot(int handle) throws Exception {
        return invoke("StateIntermediateRoot", new HandleParams(handle), Hash.class).toBytes();
    }

    public static byte[] stateCommit(int handle) throws Exception {
        return invoke("StateCommit", new HandleParams(handle), Hash.class).toBytes();
    }

    public static BigInteger stateGetBalance(int handle, byte[] address) throws Exception {
        return invoke("StateGetBalance", new AccountParams(handle, address), BigInteger.class);
    }

    public static void stateAddBalance(int handle, byte[] address, BigInteger amount) throws Exception {
        invoke("StateAddBalance", new BalanceParams(handle, address, amount));
    }

    public static void stateSubBalance(int handle, byte[] address, BigInteger amount) throws Exception {
        invoke("StateSubBalance", new BalanceParams(handle, address, amount));
    }

    public static void stateSetBalance(int handle, byte[] address, BigInteger amount) throws Exception {
        invoke("StateSetBalance", new BalanceParams(handle, address, amount));
    }

    public static BigInteger stateGetNonce(int handle, byte[] address) throws Exception {
        return invoke("StateGetNonce", new AccountParams(handle, address), BigInteger.class);
    }

    public static void stateSetNonce(int handle, byte[] address, BigInteger nonce) throws Exception {
        invoke("StateSetNonce", new NonceParams(handle, address, nonce));
    }

    public static byte[] stateGetCodeHash(int handle, byte[] address) throws Exception {
        return invoke("StateGetCodeHash", new AccountParams(handle, address), Hash.class).toBytes();
    }

    public static byte[] stateGetStorage(int handle, byte[] address, byte[] key) throws Exception {
        return invoke("StateGetStorage", new StorageParams(handle, address, key), Hash.class).toBytes();
    }

    public static void stateSetStorage(int handle, byte[] address, byte[] key, byte[] value) throws Exception {
        invoke("StateSetStorage", new SetStorageParams(handle, address, key, value));
    }

    public static byte[] stateGetStorageBytes(int handle, byte[] address, byte[] key) throws Exception {
        return invoke("StateGetStorageBytes", new StorageParams(handle, address, key), byte[].class);
    }

    public static void stateSetStorageBytes(int handle, byte[] address, byte[] key, byte[] value) throws Exception {
        invoke("StateSetStorageBytes", new SetStorageBytesParams(handle, address, key, value));
    }

    public static EvmResult evmApply(int handle, byte[] from, byte[] to, BigInteger value, byte[] input, BigInteger nonce, BigInteger gasLimit, BigInteger gasPrice)
        throws Exception {
        var params = new EvmParams(handle, from, to, value, input, nonce, gasLimit, gasPrice);
        // TODO: set context parameters
        params.context = new EvmContext();
        // TODO: decide what EIPs we are implementing, setting the baseFee to zero currently allows a gas price of zero
        params.context.baseFee = BigInteger.ZERO;
        return invoke("EvmApply", params, EvmResult.class);
    }
}
