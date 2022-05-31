package com.horizen.evm.library;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.horizen.evm.StateAccount;
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

    private static class InitializeParams extends JsonPointer {
        public String path;
    }

    private static class OpenStateParams extends JsonPointer {
        public String root;
    }

    private static class HandleParams extends JsonPointer {
        public int handle;
    }

    private static class AccountParams extends HandleParams {
        public String address;
    }

    public static void Initialize(String path) throws Exception {
        var params = new InitializeParams();
        params.path = path;
        Invoke("Initialize", params);
    }

    public static int StateOpen(String stateRootHex) throws Exception {
        var params = new OpenStateParams();
        params.root = stateRootHex;
        return Invoke("StateOpen", params, int.class);
    }

    public static void StateClose(int handle) throws Exception {
        var params = new HandleParams();
        params.handle = handle;
        Invoke("StateClose", params);
    }

    public static String StateIntermediateRoot(int handle) throws Exception {
        var params = new HandleParams();
        params.handle = handle;
        return Invoke("StateIntermediateRoot", params, String.class);
    }

    public static String StateCommit(int handle) throws Exception {
        var params = new HandleParams();
        params.handle = handle;
        return Invoke("StateCommit", params, String.class);
    }

    public static String StateGetAccountBalance(int handle, String address) throws Exception {
        var params = new AccountParams();
        params.handle = handle;
        params.address = address;
        return Invoke("StateGetAccountBalance", params, String.class);
    }

    public static StateAccount StateGetAccount(int handle, String address) throws Exception {
        var params = new AccountParams();
        params.handle = handle;
        params.address = address;
        return Invoke("StateGetAccount", params, StateAccount.class);
    }

//    public static class ContractParams extends JsonPointer {
//        public String difficulty; // uint256
//        public String origin; // address
//        public String coinbase; // address
//        public String blockNumber; // uint256
//        public String time; // uint256
//        public long gasLimit; // uint64
//        public String gasPrice; // uint256
//        public String value; // uint256
//        public String baseFee; // uint256
//
//        @Override
//        public String toString() {
//            return String.format("%s origin %s value %s", super.toString(), origin, value);
//        }
//    }
//
//    public static class ContractCreateParams extends ContractParams {
//        public byte[] input;
//        public boolean discardState;
//
//        @Override
//        public String toString() {
//            return String.format("%s input.length %d discard %b", super.toString(), input.length, discardState);
//        }
//    }
//
//    public static class ContractCreateResult extends InteropResult {
//        public String address;
//        public long leftOverGas;
//        public Map<String, String> balanceChanges;
//
//        @Override
//        public String toString() {
//            return String.format(
//                "%s address %s leftOverGas %d balanceChanges %s",
//                super.toString(),
//                address,
//                leftOverGas,
//                balanceChanges
//            );
//        }
//    }
//
//    public static class ContractCallParams extends ContractParams {
//        public String address;
//        public byte[] input;
//        public boolean discardState;
//
//        @Override
//        public String toString() {
//            return String.format(
//                "%s address %s input.length %d discard %b",
//                super.toString(),
//                address,
//                input.length,
//                discardState
//            );
//        }
//    }
//
//    public static class ContractCallResult extends InteropResult {
//        public byte[] ret;
//        public long leftOverGas;
//        public Map<String, String> balanceChanges;
//
//        @Override
//        public String toString() {
//            return String.format(
//                "%s ret %s leftOverGas %d balanceChanges %s",
//                super.toString(),
//                ret == null ? "null" : String.format("0x%s", Converter.toHexString(ret)),
//                leftOverGas,
//                balanceChanges
//            );
//        }
//    }
}
