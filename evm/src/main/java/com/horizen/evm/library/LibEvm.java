package com.horizen.evm.library;

import com.sun.jna.Native;

public final class LibEvm {
    public static LibEvmInterface Instance;

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
        Instance = Native.load(lib, LibEvmInterface.class);
    }

    private LibEvm() {
        // prevent instantiation of this class
    }

    public static <R> R Invoke(String method, JsonPointer args) throws Exception {
        var response = LibEvm.Instance.<R>Invoke(method, args);
        if (response.isError()) {
            throw new Exception(response.Error);
        }
        return response.Result;
    }

    public static class OpenStateParams extends JsonPointer {
        public String Root;
    }

    public static class HandleParams extends JsonPointer {
        public int Handle;
    }

    public static class AccountParams extends HandleParams {
        public String Address;
    }

    public static int StateOpen(String stateRootHex) throws Exception {
        var params = new OpenStateParams();
        params.Root = stateRootHex;
        return LibEvm.Invoke("StateOpen", params);
    }

    public static void StateClose(int handle) throws Exception {
        var params = new HandleParams();
        params.Handle = handle;
        LibEvm.Invoke("StateClose", params);
    }

    public static String StateIntermediateRoot(int handle) throws Exception {
        var params = new HandleParams();
        params.Handle = handle;
        return LibEvm.Invoke("StateIntermediateRoot", params);
    }

    public static String StateCommit(int handle) throws Exception {
        var params = new HandleParams();
        params.Handle = handle;
        return LibEvm.Invoke("StateCommit", params);
    }

    public static String StateGetAccountBalance(int handle, String address) throws Exception {
        var params = new AccountParams();
        params.Handle = handle;
        params.Address = address;
        return LibEvm.Invoke("StateGetAccountBalance", params);
    }

    public static class InteropResult<R> extends JsonPointer {
        public String Error;
        public R Result;

        public boolean isError() {
            return !Error.isEmpty();
        }

        @Override
        public String toString() {
            if (!Error.isEmpty()) {
                return String.format("error: %s", Error);
            }
            if (Result == null) {
                return "success";
            }
            return String.format("result: %s", Result);
        }
    }

//    public static class InteropResult extends JsonPointer {
//        public int code;
//        public String message;
//
//        @Override
//        public String toString() {
//            return String.format("code %d message %s", code, message.isEmpty() ? "none" : message);
//        }
//    }
//
//    public static class HandleResult extends InteropResult {
//        public int handle;
//
//        @Override
//        public String toString() {
//            return String.format("%s handle %d", super.toString(), handle);
//        }
//    }
//
//    public static class StateRootResult extends InteropResult {
//        public String stateRoot;
//
//        @Override
//        public String toString() {
//            return String.format("%s stateRoot %s", super.toString(), stateRoot);
//        }
//    }
//
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
//
//    public static class BalanceParams extends JsonPointer {
//        public String address;
//        public String value;
//
//        @Override
//        public String toString() {
//            return String.format("address %s value %s", address, value);
//        }
//    }
}
