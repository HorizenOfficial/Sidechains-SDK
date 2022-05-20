package com.horizen.evm;

import com.sun.jna.Native;

import java.util.Map;

public final class Evm {
    public static EvmLib Instance;

    static {
        String os = System.getProperty("os.name").toLowerCase();
        String libExtension;
        if (os.contains("mac os")) {
            libExtension = "dylib";
        } else if (os.contains("windows")) {
            libExtension = "dll";
        } else {
            libExtension = "so";
        }
        String lib = "libevm." + libExtension;
        Instance = Native.load(lib, EvmLib.class);
    }

    private Evm() {
        // prevent instantiation of this class
    }

    public static class InteropResult extends JsonPointer {
        public int code;
        public String message;

        @Override
        public String toString() {
            return String.format("code %d message %s", code, message.isEmpty() ? "none" : message);
        }
    }

    public static class StateRootResult extends InteropResult {
        public String stateRoot;

        @Override
        public String toString() {
            return String.format("%s stateRoot %s", super.toString(), stateRoot);
        }
    }

    public static class ContractParams extends JsonPointer {
        public String difficulty; // uint256
        public String origin; // address
        public String coinbase; // address
        public String blockNumber; // uint256
        public String time; // uint256
        public long gasLimit; // uint64
        public String gasPrice; // uint256
        public String value; // uint256
        public String baseFee; // uint256

        @Override
        public String toString() {
            return String.format("%s origin %s value %s", super.toString(), origin, value);
        }
    }

    public static class ContractCreateParams extends ContractParams {
        public byte[] input;
        public boolean discardState;

        @Override
        public String toString() {
            return String.format("%s input.length %d discard %b", super.toString(), input.length, discardState);
        }
    }

    public static class ContractCreateResult extends InteropResult {
        public String address;
        public long leftOverGas;
        public Map<String, String> balanceChanges;

        @Override
        public String toString() {
            return String.format(
                "%s address %s leftOverGas %d balanceChanges %s",
                super.toString(),
                address,
                leftOverGas,
                balanceChanges
            );
        }
    }

    public static class ContractCallParams extends ContractParams {
        public String address;
        public byte[] input;
        public boolean discardState;

        @Override
        public String toString() {
            return String.format(
                "%s address %s input.length %d discard %b",
                super.toString(),
                address,
                input.length,
                discardState
            );
        }
    }

    public static class ContractCallResult extends InteropResult {
        public byte[] ret;
        public long leftOverGas;
        public Map<String, String> balanceChanges;

        @Override
        public String toString() {
            return String.format(
                "%s ret %s leftOverGas %d balanceChanges %s",
                super.toString(),
                ret == null ? "null" : String.format("0x%s", EvmUtils.toHexString(ret)),
                leftOverGas,
                balanceChanges
            );
        }
    }

    public static class BalanceParams extends JsonPointer {
        public String address;
        public String value;

        @Override
        public String toString() {
            return String.format("address %s value %s", address, value);
        }
    }
}
