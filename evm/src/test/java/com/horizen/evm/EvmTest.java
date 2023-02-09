package com.horizen.evm;

import com.horizen.evm.interop.EvmResult;
import com.horizen.evm.interop.TraceOptions;
import com.horizen.evm.utils.Address;
import com.horizen.evm.utils.Hash;
import org.junit.Test;

import java.math.BigInteger;
import java.util.Arrays;

import static org.junit.Assert.*;

public class EvmTest extends LibEvmTestBase {
    @Test
    public void evmApply() throws Exception {
        final var txHash = new Hash("0x4545454545454545454545454545454545454545454545454545454545454545");
        final var codeHash = new Hash("0xaa87aee0394326416058ef46b907882903f3646ef2a6d0d20f9e705b87c58c77");
        final var addr1 = new Address("0x1234561234561234561234561234561234561230");
        final var addr2 = new Address("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b");

        final var contractCode = bytes(
            "608060405234801561001057600080fd5b5060405161023638038061023683398101604081905261002f916100f6565b6000819055604051339060008051602061021683398151915290610073906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03166000805160206102168339815191526040516100bf906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b9994690600090a15061010f565b60006020828403121561010857600080fd5b5051919050565b60f98061011d6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea2646970667358221220769e4dd8320afae06d27e8e201c885728883af2ea321d02071c47704c1b3c24f64736f6c634300080e00330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0");
        final var initialValue = new Hash("0x0000000000000000000000000000000000000000000000000000000000000000");
        final var anotherValue = new Hash("0x00000000000000000000000000000000000000000000000000000000000015b3");

        final var funcStore = bytes("6057361d");
        final var funcRetrieve = bytes("2e64cec1");

        final var v10m = BigInteger.valueOf(10000000);
        final var v5m = BigInteger.valueOf(5000000);
        final var gasLimit = BigInteger.valueOf(200000);
        final var gasPrice = BigInteger.valueOf(10);

        EvmResult result;
        Address contractAddress;
        Hash modifiedStateRoot;
        byte[] calldata;

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                // test a simple value transfer
                statedb.addBalance(addr1, v10m);
                // Due to nonce increase before any transaction on sdk side
                statedb.setNonce(addr1, BigInteger.ONE);
                result = Evm.Apply(statedb, addr1, addr2, v5m, null, gasLimit, gasPrice, null, null, null);
                assertEquals("", result.evmError);
                assertEquals(v5m, statedb.getBalance(addr2));
                // gas fees should not have been deducted
                assertEquals(v5m, statedb.getBalance(addr1));
                // gas fees should not be moved to the coinbase address (which currently defaults to the zero-address)
                assertEquals(BigInteger.ZERO, statedb.getBalance(null));

                // test contract deployment
                calldata = concat(contractCode, initialValue.toBytes());
                statedb.setTxContext(txHash, 0);
                // Due to nonce increase before any transaction on sdk side
                statedb.setNonce(addr2, BigInteger.ONE);
                final var createResult = Evm.Apply(
                    statedb, addr2, null, null, calldata, gasLimit, gasPrice, null, null, null);
                assertEquals("", createResult.evmError);
                contractAddress = createResult.contractAddress;
                assertEquals(codeHash, statedb.getCodeHash(contractAddress));
                var logs = statedb.getLogs(txHash);
                assertEquals("should generate 3 log entries", 3, logs.length);
                Arrays
                    .stream(logs)
                    .forEach(log ->
                        assertArrayEquals(log.address.toBytes(), createResult.contractAddress.toBytes())
                    );

                // call "store" function on the contract to set a value
                calldata = concat(funcStore, anotherValue.toBytes());
                result = Evm.Apply(statedb, addr2, contractAddress, null, calldata, gasLimit, gasPrice, null, null, null);
                assertEquals("", result.evmError);

                // call "retrieve" on the contract to fetch the value we just set
                result = Evm.Apply(
                    statedb, addr2, contractAddress, null, funcRetrieve, gasLimit, gasPrice, null, new TraceOptions(), null);
                assertEquals("", result.evmError);
                assertEquals(anotherValue, new Hash(result.returnData));
                assertNotNull(result.tracerResult);

                modifiedStateRoot = statedb.commit();
            }

            // reopen the state and retrieve a value
            try (var statedb = new StateDB(db, modifiedStateRoot)) {
                result = Evm.Apply(statedb, addr2, contractAddress, null, funcRetrieve, gasLimit, gasPrice, null, null, null);
                assertEquals("", result.evmError);
                assertEquals(anotherValue, new Hash(result.returnData));
            }
        }
    }

    @Test
    public void insufficientBalanceTransfer() throws Exception {
        final var addr1 = new Address("0x1234561234561234561234561234561234561230");
        final var addr2 = new Address("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b");

        final var v5m = BigInteger.valueOf(5000000);
        final var gasLimit = BigInteger.valueOf(200000);
        final var gasPrice = BigInteger.valueOf(10);

        EvmResult result;

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                statedb.setNonce(addr1, BigInteger.ZERO);
                result = Evm.Apply(statedb, addr1, addr2, v5m, null, gasLimit, gasPrice, null, null, null);
                assertNotNull(result.evmError);
            }
        }
    }

    @Test
    public void brokenCodeExecution() throws Exception {
        final var addr1 = new Address("0x1234561234561234561234561234561234561230");

        final var v5m = BigInteger.valueOf(5000000);
        final var gasLimit = BigInteger.valueOf(200000);
        final var gasPrice = BigInteger.valueOf(10);
        final var input = bytes(
            "5234801561001057600080fd521683398151915290610073906020808252600c90820190565b60405180910390a2336001600160a01b03");

        EvmResult result;

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                statedb.setBalance(addr1, v5m);
                statedb.setNonce(addr1, BigInteger.ZERO);
                result = Evm.Apply(statedb, addr1, null, null, input, gasLimit, gasPrice, null, null, null);
                assertNotNull(result.evmError);
            }
        }
    }

    @Test
    public void insufficientGasLimit() throws Exception {
        final var addr1 = new Address("0x1234561234561234561234561234561234561230");

        final var v5m = BigInteger.valueOf(5000000);
        final var gasLimit = BigInteger.valueOf(100000);
        final var gasPrice = BigInteger.valueOf(10);
        final var input = bytes(
            "608060405234801561001057600080fd5b5060405161023638038061023683398101604081905261002f916100f6565b6000819055604051339060008051602061021683398151915290610073906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03166000805160206102168339815191526040516100bf906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b9994690600090a15061010f565b60006020828403121561010857600080fd5b5051919050565b60f98061011d6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea2646970667358221220769e4dd8320afae06d27e8e201c885728883af2ea321d02071c47704c1b3c24f64736f6c634300080e00330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0");

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                statedb.setBalance(addr1, v5m);
                statedb.setNonce(addr1, BigInteger.ZERO);
                var result = Evm.Apply(statedb, addr1, null, null, input, gasLimit, gasPrice, null, null, null);
                assertNotNull(result.evmError);
            }
        }
    }
}
