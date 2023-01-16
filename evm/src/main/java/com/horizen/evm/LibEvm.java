package com.horizen.evm;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.horizen.evm.interop.*;
import com.horizen.evm.utils.Hash;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

import java.math.BigInteger;

final class LibEvm {
    static native void Free(Pointer ptr);
    static native void SetLogCallback(LibEvmLogCallback callback);
    static native void SetLogLevel(String level);

    private static native JsonPointer Invoke(String method, JsonPointer args);

    static String getOSLibExtension() {
        var os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac os")) {
            return "dylib";
        } else if (os.contains("windows")) {
            return "dll";
        }
        // default to linux file extension
        return "so";
    }

    static {
        // bind native methods in this class to libevm
        Native.register("libevm." + getOSLibExtension());
        LibEvmLogCallback.Register();
    }

    private LibEvm() {
        // prevent instantiation of this class
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

    private static <R> R invoke(String method, JsonPointer args, Class<R> responseType) {
        var json = Invoke(method, args);
        // build type information to deserialize to generic type InteropResult<R>
        var type = TypeFactory.defaultInstance().constructParametricType(InteropResult.class, responseType);
        InteropResult<R> response = json.deserialize(type);
        if (response.isError()) {
            var message = String.format(
                    "Error: \"%s\" occurred for method %s, with arguments %s",
                    response.error,
                    method,
                    args == null ? null : args.toNative()
            );
            throw new InvokeException(message);
        }
        return response.result;
    }

    private static void invoke(String method, JsonPointer args) {
        invoke(method, args, Void.class);
    }

    private static void invoke(String method) {
        invoke(method, null, Void.class);
    }

    public static int openMemoryDB() {
        return invoke("OpenMemoryDB", null, int.class);
    }

    public static int openLevelDB(String path) {
        return invoke("OpenLevelDB", new LevelDBParams(path), int.class);
    }

    public static void closeDatabase(int handle) {
        invoke("CloseDatabase", new DatabaseParams(handle));
    }

    public static int stateOpen(int databaseHandle, byte[] root) {
        return invoke("StateOpen", new OpenStateParams(databaseHandle, root), int.class);
    }

    public static void stateClose(int handle) {
        invoke("StateClose", new HandleParams(handle));
    }

    public static void stateFinalize(int handle) {
        invoke("StateFinalize", new HandleParams(handle));
    }

    public static byte[] stateIntermediateRoot(int handle) {
        return invoke("StateIntermediateRoot", new HandleParams(handle), Hash.class).toBytes();
    }

    public static byte[] stateCommit(int handle) {
        return invoke("StateCommit", new HandleParams(handle), Hash.class).toBytes();
    }

    public static boolean stateEmpty(int handle, byte[] address) {
        return invoke("StateEmpty", new AccountParams(handle, address), boolean.class);
    }

    public static BigInteger stateGetBalance(int handle, byte[] address) {
        return invoke("StateGetBalance", new AccountParams(handle, address), BigInteger.class);
    }

    public static void stateAddBalance(int handle, byte[] address, BigInteger amount) {
        invoke("StateAddBalance", new BalanceParams(handle, address, amount));
    }

    public static void stateSubBalance(int handle, byte[] address, BigInteger amount) {
        invoke("StateSubBalance", new BalanceParams(handle, address, amount));
    }

    public static void stateSetBalance(int handle, byte[] address, BigInteger amount) {
        invoke("StateSetBalance", new BalanceParams(handle, address, amount));
    }

    public static BigInteger stateGetNonce(int handle, byte[] address) {
        return invoke("StateGetNonce", new AccountParams(handle, address), BigInteger.class);
    }

    public static void stateSetNonce(int handle, byte[] address, BigInteger nonce) {
        invoke("StateSetNonce", new NonceParams(handle, address, nonce));
    }

    public static byte[] stateGetCodeHash(int handle, byte[] address) {
        return invoke("StateGetCodeHash", new AccountParams(handle, address), Hash.class).toBytes();
    }

    public static byte[] stateGetCode(int handle, byte[] address) {
        return invoke("StateGetCode", new AccountParams(handle, address), byte[].class);
    }

    public static void stateSetCode(int handle, byte[] address, byte[] code) {
        invoke("StateSetCode", new CodeParams(handle, address, code));
    }

    public static BigInteger stateGetRefund(int handle) {
        return invoke("StateGetRefund", new HandleParams(handle), BigInteger.class);
    }

    public static byte[] stateGetStorage(int handle, byte[] address, byte[] key) {
        return invoke("StateGetStorage", new StorageParams(handle, address, key), Hash.class).toBytes();
    }

    public static void stateSetStorage(int handle, byte[] address, byte[] key, byte[] value) {
        invoke("StateSetStorage", new SetStorageParams(handle, address, key, value));
    }

    public static void stateRemoveStorage(int handle, byte[] address, byte[] key) {
        invoke("StateRemoveStorage", new StorageParams(handle, address, key));
    }

    public static byte[] stateGetStorageBytes(int handle, byte[] address, byte[] key) {
        return invoke("StateGetStorageBytes", new StorageParams(handle, address, key), byte[].class);
    }

    public static void stateSetStorageBytes(int handle, byte[] address, byte[] key, byte[] value) {
        invoke("StateSetStorageBytes", new SetStorageBytesParams(handle, address, key, value));
    }

    public static void stateRemoveStorageBytes(int handle, byte[] address, byte[] key) {
        invoke("StateRemoveStorageBytes", new StorageParams(handle, address, key));
    }

    public static ProofAccountResult stateGetProof(int handle, byte[] address, byte[][] keys) {
        return invoke("StateGetProof", new ProofParams(handle, address, keys), ProofAccountResult.class);
    }

    public static int stateSnapshot(int handle) {
        return invoke("StateSnapshot", new HandleParams(handle), int.class);
    }

    public static void stateRevertToSnapshot(int handle, int revisionId) {
        invoke("StateRevertToSnapshot", new SnapshotParams(handle, revisionId));
    }

    public static EvmLog[] stateGetLogs(int handle, byte[] txHash) {
        return invoke("StateGetLogs", new GetLogsParams(handle, txHash), EvmLog[].class);
    }

    public static void stateAddLog(int handle, EvmLog evmLog) {
        invoke("StateAddLog", new AddLogParams(handle, evmLog));
    }

    public static void stateSetTxContext(int handle, byte[] txHash, int txIndex) {
        invoke("StateSetTxContext", new SetTxContextParams(handle, txHash, txIndex));
    }

    public static EvmResult evmApply(
            int handle,
            byte[] from,
            byte[] to,
            BigInteger value,
            byte[] input,
            BigInteger gasLimit,
            BigInteger gasPrice,
            EvmContext context,
            TraceOptions traceOptions
    ) {
        var params = new EvmParams(handle, from, to, value, input, gasLimit, gasPrice, context, traceOptions);
        return invoke("EvmApply", params, EvmResult.class);
    }

    public static byte[] hashRoot(byte[][] values) {
        return invoke("HashRoot", new HashParams(values), Hash.class).toBytes();
    }
}
