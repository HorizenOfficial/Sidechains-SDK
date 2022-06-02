package com.horizen.evm.library;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.sun.jna.Library;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public final class LibEvm {
    private interface LibEvmInterface extends Library {
        void Free(Pointer ptr);

        JsonString Invoke(String method, JsonPointer args);
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

    public static int stateOpen(String stateRootHex) throws Exception {
        return invoke("StateOpen", new OpenStateParams(stateRootHex), int.class);
    }

    public static void stateClose(int handle) throws Exception {
        invoke("StateClose", new HandleParams(handle));
    }

    public static String stateIntermediateRoot(int handle) throws Exception {
        return invoke("StateIntermediateRoot", new HandleParams(handle), String.class);
    }

    public static String stateCommit(int handle) throws Exception {
        return invoke("StateCommit", new HandleParams(handle), String.class);
    }

    public static String stateGetBalance(int handle, String address) throws Exception {
        return invoke("StateGetBalance", new AccountParams(handle, address), String.class);
    }

    public static void stateAddBalance(int handle, String address, String amount) throws Exception {
        invoke("StateAddBalance", new BalanceParams(handle, address, amount));
    }

    public static void stateSubBalance(int handle, String address, String amount) throws Exception {
        invoke("StateSubBalance", new BalanceParams(handle, address, amount));
    }

    public static void stateSetBalance(int handle, String address, String amount) throws Exception {
        invoke("StateSetBalance", new BalanceParams(handle, address, amount));
    }

    public static long stateGetNonce(int handle, String address) throws Exception {
        return invoke("StateGetNonce", new AccountParams(handle, address), long.class);
    }

    public static void stateSetNonce(int handle, String address, long nonce) throws Exception {
        invoke("StateSetNonce", new NonceParams(handle, address, nonce));
    }

    public static String stateGetCodeHash(int handle, String address) throws Exception {
        return invoke("StateGetCodeHash", new AccountParams(handle, address), String.class);
    }

    public static EvmResult evmExecute(int handle, String from, String to, String value, byte[] input)
        throws Exception {
        var cfg = new EvmParams.EvmConfig();
        cfg.origin = from;
        cfg.value = value;
        cfg.gasLimit = 100000;
        return invoke("EvmExecute", new EvmParams(handle, cfg, to, input), EvmResult.class);
    }
}
