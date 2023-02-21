package com.horizen.evm;

import com.horizen.evm.results.EvmResult;
import org.junit.Test;

import java.math.BigInteger;

import static org.junit.Assert.*;

public class EvmTest extends LibEvmTestBase {

    private final Address addr1 = new Address("0x1234561234561234561234561234561234561230");
    private final Address addr2 = new Address("0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b");
    private final BigInteger gasLimit = BigInteger.valueOf(200000);
    private final BigInteger v10m = BigInteger.valueOf(10000000);
    private final BigInteger v5m = BigInteger.valueOf(5000000);
    private final BigInteger gasPrice = BigInteger.valueOf(10);

    @Test
    public void evmApply() throws Exception {
        final var txHash = new Hash("0x4545454545454545454545454545454545454545454545454545454545454545");
        final var codeHash = new Hash("0xaa87aee0394326416058ef46b907882903f3646ef2a6d0d20f9e705b87c58c77");

        final var contractCode = bytes(
            "608060405234801561001057600080fd5b5060405161023638038061023683398101604081905261002f916100f6565b6000819055604051339060008051602061021683398151915290610073906020808252600c908201526b48656c6c6f20576f726c642160a01b604082015260600190565b60405180910390a2336001600160a01b03166000805160206102168339815191526040516100bf906020808252600a908201526948656c6c6f2045564d2160b01b604082015260600190565b60405180910390a26040517ffe1a3ad11e425db4b8e6af35d11c50118826a496df73006fc724cb27f2b9994690600090a15061010f565b60006020828403121561010857600080fd5b5051919050565b60f98061011d6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea2646970667358221220769e4dd8320afae06d27e8e201c885728883af2ea321d02071c47704c1b3c24f64736f6c634300080e00330738f4da267a110d810e6e89fc59e46be6de0c37b1d5cd559b267dc3688e74e0");
        final var testValue = new Hash("0x00000000000000000000000000000000000000000000000000000000000015b3");

        final var funcStore = bytes("6057361d");
        final var funcRetrieve = bytes("2e64cec1");

        EvmResult result;
        Address contractAddress;
        Hash modifiedStateRoot;
        byte[] calldata;

        try (var db = new MemoryDatabase()) {
            try (var statedb = new StateDB(db, Hash.ZERO)) {
                // test a simple value transfer
                statedb.addBalance(addr1, v10m);
                result = Evm.Apply(statedb, addr1, addr2, v5m, null, gasLimit, gasPrice, null, null);
                assertEquals("", result.evmError);
                assertEquals(v5m, statedb.getBalance(addr2));
                // gas fees should not have been deducted
                assertEquals(v5m, statedb.getBalance(addr1));
                // gas fees should not be moved to the coinbase address (which currently defaults to the zero-address)
                assertEquals(BigInteger.ZERO, statedb.getBalance(Address.ZERO));

                // test contract deployment
                calldata = concat(contractCode, Hash.ZERO.toBytes());
                statedb.setTxContext(txHash, 0);
                var createResult = Evm.Apply(statedb, addr2, null, null, calldata, gasLimit, gasPrice, null, null);
                assertEquals("", createResult.evmError);
                contractAddress = createResult.contractAddress;
                assertEquals(codeHash, statedb.getCodeHash(contractAddress));
                var logs = statedb.getLogs(txHash);
                assertEquals("should generate 3 log entries", 3, logs.length);
                for (var log : logs) {
                    assertEquals(log.address, createResult.contractAddress);
                }

                // call "store" function on the contract to set a value
                calldata = concat(funcStore, testValue.toBytes());
                result = Evm.Apply(statedb, addr2, contractAddress, null, calldata, gasLimit, gasPrice, null, null);
                assertEquals("", result.evmError);

                // call "retrieve" on the contract to fetch the value we just set
                result = Evm.Apply(
                    statedb, addr2, contractAddress, null, funcRetrieve, gasLimit, gasPrice, null, new TraceOptions());
                assertEquals("", result.evmError);
                assertEquals(testValue, new Hash(result.returnData));
                assertNotNull(result.tracerResult);

                modifiedStateRoot = statedb.commit();
            }

            // reopen the state and retrieve a value
            try (var statedb = new StateDB(db, modifiedStateRoot)) {
                result = Evm.Apply(statedb, addr2, contractAddress, null, funcRetrieve, gasLimit, gasPrice, null, null);
                assertEquals("", result.evmError);
                assertEquals(testValue, new Hash(result.returnData));
            }
        }
    }

    @Test
    public void blockHashCallback() throws Exception {
        final var contractCode = bytes(
            "608060405234801561001057600080fd5b50610157806100206000396000f3fe608060405234801561001057600080fd5b50600436106100935760003560e01c8063557ed1ba11610066578063557ed1ba146100bf578063564b81ef146100c55780639663f88f146100cb578063aacc5a17146100d3578063d1a82a9d146100d957600080fd5b806315e812ad146100985780631a93d1c3146100ad57806342cbb15c146100b3578063455259cb146100b9575b600080fd5b485b6040519081526020015b60405180910390f35b4561009a565b4361009a565b3a61009a565b4261009a565b4661009a565b61009a6100e7565b4461009a565b6040514181526020016100a4565b60006100f46001436100fa565b40905090565b8181038181111561011b57634e487b7160e01b600052601160045260246000fd5b9291505056fea2646970667358221220a629106cbdbc0017022eedc70f72757902db9dc7881e188747a544aaa638345d64736f6c63430008120033");
        final var funcBlockHash = bytes("9663f88f");
        final var blockHash = new Hash("0x4be4fcab0081094826c46187731326cd17e81ef522975e5607327891d94d3ce7");
        final var height = BigInteger.valueOf(1234);

        class BlockHashGetter extends BlockHashCallback {
            private boolean throwIfCalled;

            public void enable() { throwIfCalled = true; }

            public void disable() { throwIfCalled = false; }

            @Override
            protected Hash getBlockHash(BigInteger blockNumber) {
                assertFalse("should not have been called", throwIfCalled);
                // getBlockHash() on the OpCode test contract should request the block hash for height - 1
                assertEquals("unexpected block hash requested", height.subtract(BigInteger.ONE), blockNumber);
                return blockHash;
            }
        }

        try (
            var db = new MemoryDatabase();
            var statedb = new StateDB(db, Hash.ZERO);
            var blockHashGetterA = new BlockHashGetter();
            var blockHashGetterB = new BlockHashGetter()
        ) {
            // deploy OpCode test contract
            var createResult = Evm.Apply(statedb, addr1, null, null, contractCode, gasLimit, gasPrice, null, null);
            assertEquals("", createResult.evmError);
            var contractAddress = createResult.contractAddress;

            // setup context
            var context = new EvmContext();
            context.blockNumber = height;
            context.blockHashCallback = blockHashGetterA;

            // throw if B is called
            blockHashGetterA.disable();
            blockHashGetterB.enable();

            // call getBlockHash() function on the contract
            var resultA = Evm.Apply(
                statedb, addr1, contractAddress, null, funcBlockHash, gasLimit, gasPrice, context, null);
            assertEquals("unexpected error message", "", resultA.evmError);
            assertEquals("unexpected block hash", blockHash, new Hash(resultA.returnData));

            // throw if A is called
            context.blockHashCallback = blockHashGetterB;
            blockHashGetterA.enable();
            blockHashGetterB.disable();

            // call getBlockHash() function on the contract
            var resultB = Evm.Apply(
                statedb, addr1, contractAddress, null, funcBlockHash, gasLimit, gasPrice, context, null);
            assertEquals("unexpected error message", "", resultB.evmError);
            assertEquals("unexpected block hash", blockHash, new Hash(resultB.returnData));
        }

        // sanity check for unregistering callbacks
        try (var blockHashGetterC = new BlockHashGetter()) {
            // handle 0 will always be used by the log callback
            // we released all other callbacks and created a new one here, so we expect the handle to be 1
            assertEquals("callback handles were not released", 1, blockHashGetterC.handle);
        }
    }

    @Test
    public void insufficientBalanceTransfer() throws Exception {
        try (var db = new MemoryDatabase(); var statedb = new StateDB(db, Hash.ZERO)) {
            var result = Evm.Apply(statedb, addr1, addr2, v5m, null, gasLimit, gasPrice, null, null);
            assertEquals("unexpected error message", "insufficient balance for transfer", result.evmError);
            assertEquals("unexpected gas usage", BigInteger.ZERO, result.usedGas);
        }
    }

    @Test
    public void brokenCodeExecution() throws Exception {
        final var input = bytes(
            "5234801561001057600080fd521683398151915290610073906020808252600c90820190565b60405180910390a2336001600160a01b03");
        try (var db = new MemoryDatabase(); var statedb = new StateDB(db, Hash.ZERO)) {
            statedb.setBalance(addr1, v5m);
            var result = Evm.Apply(statedb, addr1, null, null, input, gasLimit, gasPrice, null, null);
            assertTrue("unexpected error message", result.evmError.startsWith("stack underflow"));
            assertEquals("unexpected gas usage", gasLimit, result.usedGas);
        }
    }

    @Test
    public void insufficientGasLimit() throws Exception {
        final var input = bytes(
            "608060405234801561001057600080fd5b50610157806100206000396000f3fe608060405234801561001057600080fd5b50600436106100935760003560e01c8063557ed1ba11610066578063557ed1ba146100bf578063564b81ef146100c55780639663f88f146100cb578063aacc5a17146100d3578063d1a82a9d146100d957600080fd5b806315e812ad146100985780631a93d1c3146100ad57806342cbb15c146100b3578063455259cb146100b9575b600080fd5b485b6040519081526020015b60405180910390f35b4561009a565b4361009a565b3a61009a565b4261009a565b4661009a565b61009a6100e7565b4461009a565b6040514181526020016100a4565b60006100f46001436100fa565b40905090565b8181038181111561011b57634e487b7160e01b600052601160045260246000fd5b9291505056fea2646970667358221220a629106cbdbc0017022eedc70f72757902db9dc7881e188747a544aaa638345d64736f6c63430008120033");
        var gasLimit = BigInteger.valueOf(50000);
        try (var db = new MemoryDatabase(); var statedb = new StateDB(db, Hash.ZERO)) {
            statedb.setBalance(addr1, v5m);
            var result = Evm.Apply(statedb, addr1, null, null, input, gasLimit, gasPrice, null, null);
            assertEquals("unexpected error message", "contract creation code storage out of gas", result.evmError);
            assertEquals("unexpected gas usage", gasLimit, result.usedGas);
        }
    }
}
