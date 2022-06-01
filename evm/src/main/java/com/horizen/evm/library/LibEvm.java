package com.horizen.evm.library;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;

public final class LibEvm {
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

    private static <R> R Invoke(String method, JsonPointer args, Class<R> responseType) throws Exception {
        var json = instance.Invoke(method, args);
        // build type information to deserialize to generic type InteropResult<R>
        var type = TypeFactory.defaultInstance().constructParametricType(InteropResult.class, responseType);
        InteropResult<R> response = json.deserialize(type);
        if (response.isError()) {
            throw new Exception(response.error);
        }
        return response.result;
    }

    private static void Invoke(String method, JsonPointer args) throws Exception {
        Invoke(method, args, Void.class);
    }

    private static void Invoke(String method) throws Exception {
        Invoke(method, null, Void.class);
    }

    public static class InitializeParams extends JsonPointer {
        public String path;

        public InitializeParams() {
        }

        public InitializeParams(String path) {
            this.path = path;
        }
    }

    public static class OpenStateParams extends JsonPointer {
        public String root;

        public OpenStateParams() {
        }

        public OpenStateParams(String root) {
            this.root = root;
        }
    }

    public static class HandleParams extends JsonPointer {
        public int handle;

        public HandleParams() {
        }

        public HandleParams(int handle) {
            this.handle = handle;
        }
    }

    public static class AccountParams extends HandleParams {
        public String address;

        public AccountParams() {
        }

        public AccountParams(int handle, String address) {
            super(handle);
            this.address = address;
        }
    }

    public static class BalanceParams extends AccountParams {
        public String amount;

        public BalanceParams() {
        }

        public BalanceParams(int handle, String address, String amount) {
            super(handle, address);
            this.amount = amount;
        }
    }

    public static class NonceParams extends AccountParams {
        public long nonce;

        public NonceParams() {
        }

        public NonceParams(int handle, String address, long nonce) {
            super(handle, address);
            this.nonce = nonce;
        }
    }

    public static class EvmConfig {
        public String difficulty; // uint256
        public String origin; // address
        public String coinbase; // address
        public String blockNumber; // uint256
        public String time; // uint256
        public long gasLimit; // uint64
        public String gasPrice; // uint256
        public String value; // uint256
        public String baseFee; // uint256
    }

    public static class EvmParams extends HandleParams {
        public EvmConfig config;
        public String address;
        public byte[] input;

        public EvmParams() {
        }

        public EvmParams(int handle, EvmConfig config, String address, byte[] input) {
            super(handle);
            this.config = config;
            this.address = address;
            this.input = input;
        }
    }

    public static class EvmResult {
        public byte[] returnData;
        public String address;
        public long leftOverGas;
        public String evmError;
    }

    public static void Initialize(String path) throws Exception {
        Invoke("Initialize", new InitializeParams(path));
    }

    public static void CloseDatabase() throws Exception {
        Invoke("CloseDatabase");
    }

    public static int StateOpen(String stateRootHex) throws Exception {
        return Invoke("StateOpen", new OpenStateParams(stateRootHex), int.class);
    }

    public static void StateClose(int handle) throws Exception {
        Invoke("StateClose", new HandleParams(handle));
    }

    public static String StateIntermediateRoot(int handle) throws Exception {
        return Invoke("StateIntermediateRoot", new HandleParams(handle), String.class);
    }

    public static String StateCommit(int handle) throws Exception {
        return Invoke("StateCommit", new HandleParams(handle), String.class);
    }

    public static String StateGetBalance(int handle, String address) throws Exception {
        return Invoke("StateGetBalance", new AccountParams(handle, address), String.class);
    }

    public static void StateAddBalance(int handle, String address, String amount) throws Exception {
        Invoke("StateAddBalance", new BalanceParams(handle, address, amount));
    }

    public static void StateSubBalance(int handle, String address, String amount) throws Exception {
        Invoke("StateSubBalance", new BalanceParams(handle, address, amount));
    }

    public static void StateSetBalance(int handle, String address, String amount) throws Exception {
        Invoke("StateSetBalance", new BalanceParams(handle, address, amount));
    }

    public static long StateGetNonce(int handle, String address) throws Exception {
        return Invoke("StateGetNonce", new AccountParams(handle, address), long.class);
    }

    public static void StateSetNonce(int handle, String address, long nonce) throws Exception {
        Invoke("StateSetNonce", new NonceParams(handle, address, nonce));
    }

    public static String StateGetCodeHash(int handle, String address) throws Exception {
        return Invoke("StateGetCodeHash", new AccountParams(handle, address), String.class);
    }

    public static EvmResult EvmExecute(int handle, String from, String to, String value, byte[] input)
        throws Exception {
        var cfg = new EvmConfig();
        cfg.origin = from;
        cfg.value = value;
        cfg.gasLimit = 100000;
        return Invoke("EvmExecute", new EvmParams(handle, cfg, to, input), EvmResult.class);
    }
}
