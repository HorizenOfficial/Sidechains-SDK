package com.horizen.evm;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.type.TypeFactory;
import com.horizen.evm.interop.*;
import com.horizen.evm.utils.Hash;
import com.sun.jna.Callback;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.Level;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.math.BigInteger;
import java.util.HashMap;

final class LibEvm {
    private interface LibEvmLogCallback extends Callback {
        void callback(Pointer message);
    }

    static native void Free(Pointer ptr);

    private static native JsonPointer Invoke(String method, JsonPointer args);

    private static native void RegisterLogCallback(LibEvmLogCallback callback);

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

    private static Level gloglevelToLog4jLevel(String glogLevel) {
        switch (glogLevel) {
            case "trce": return Level.TRACE;
            default:
            case "dbug": return Level.DEBUG;
            case "info": return Level.INFO;
            case "warn": return Level.WARN;
            case "eror": return Level.ERROR;
            case "crit": return Level.FATAL;
        }
    }

    static {
        Native.register("libevm." + getOSLibExtension());
        var mapper = new ObjectMapper();
        Logger logger = LogManager.getLogger(LibEvm.class);
        RegisterLogCallback(message -> {
            try {
                var json = message.getString(0);
                var parsed = mapper.readValue(json, HashMap.class);
                var lvl = parsed.remove("lvl");
                var msg = parsed.remove("msg");
                parsed.remove("t");
                logger.log(gloglevelToLog4jLevel((String) lvl), String.format("%s %s", msg, parsed));
            } catch (Exception e) {
                // make sure we do not throw any exception here because this callback is called by native code
                logger.warn("received invalid log message data from libevm");
            }
        });
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
            throw new InvokeException(response.error, method, args);
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

    public static void stateSetCodeHash(int handle, byte[] address, byte[] codeHash) {
        invoke("StateSetCodeHash", new CodeHashParams(handle, address, codeHash));
    }

    public static void stateSetCode(int handle, byte[] address, byte[] code) {
        invoke("StateSetCode", new CodeParams(handle, address, code));
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

    public static int stateSnapshot(int handle) {
        return invoke("StateSnapshot", new HandleParams(handle), int.class);
    }

    public static void stateRevertToSnapshot(int handle, int revisionId) {
        invoke("StateRevertToSnapshot", new SnapshotParams(handle, revisionId));
    }

    public static EvmLog[] stateGetLogs(int handle, byte[] txHash) {
        return invoke("StateGetLogs", new GetLogsParams(handle, txHash), EvmLog[].class);
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
            EvmContext context
    ) {
        if (context == null) {
            context = new EvmContext();
            // TODO: decide what EIPs we are implementing, setting the baseFee to zero currently allows a gas price of zero
            context.baseFee = BigInteger.ZERO;
        }
        var params = new EvmParams(handle, from, to, value, input, gasLimit, gasPrice, context);
        return invoke("EvmApply", params, EvmResult.class);
    }

    public static byte[] hashRoot(byte[][] values) {
        return invoke("HashRoot", new HashParams(values), Hash.class).toBytes();
    }
}
