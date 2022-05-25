package com.horizen.evm;

import com.horizen.evm.library.LibEvm;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;

public class EvmTest {
    @Rule
    public TemporaryFolder tempFolder = new TemporaryFolder();

    @Test
    public void TestInterop() throws IOException {
        var databaseFolder = tempFolder.newFolder("evm-db");
        System.out.println("temporary database folder: " + databaseFolder.getAbsolutePath());

        String initialStateRoot = "0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421";
        String origin = "0xbafe3b6f2a19658df3cb5efca158c93272ff5c0b";
        String contractCode =
            "608060405234801561001057600080fd5b5060405161015738038061015783398101604081905261002f91610037565b600055610050565b60006020828403121561004957600080fd5b5051919050565b60f98061005e6000396000f3fe60806040526004361060305760003560e01c80632e64cec1146035578063371303c01460565780636057361d14606a575b600080fd5b348015604057600080fd5b5060005460405190815260200160405180910390f35b348015606157600080fd5b506068607a565b005b606860753660046086565b600055565b6000546075906001609e565b600060208284031215609757600080fd5b5035919050565b6000821982111560be57634e487b7160e01b600052601160045260246000fd5b50019056fea26469706673582212207464e228829f86206a2f85d9740ac1707dac21a7c3790c186c66c62e00bb514664736f6c634300080c0033";
        String initialValue = "0000000000000000000000000000000000000000000000000000000000000000";
        String secondValue = "00000000000000000000000000000000000000000000000000000000000015B3";

        System.out.println("Initialize");
        var initResult = LibEvm.Instance.Initialize(databaseFolder.getAbsolutePath());
        System.out.println("Initialize result " + initResult);

//        System.out.println("SetRootHash");
//        var setStateRootResult = Evm.Instance.SetStateRoot(initialStateRoot);
//        System.out.println("SetRootHash result " + setStateRootResult);
//
//        System.out.println("GetIntermediateStateRoot");
//        var getStateRootResult = Evm.Instance.GetIntermediateStateRoot();
//        System.out.println("GetIntermediateStateRoot result " + getStateRootResult);
//
//        System.out.println("ContractCreate");
//        var createParams = new Evm.ContractCreateParams();
//        createParams.origin = origin;
//        createParams.input = Converter.fromHexString(contractCode + initialValue);
//        createParams.gasLimit = 100000;
//        var createResult = Evm.Instance.ContractCreate(createParams);
//        System.out.println("ContractCreate result " + createResult);
//
//        System.out.println("AddBalance(1)");
//        var balanceParams = new Evm.BalanceParams();
//        balanceParams.address = origin;
//        balanceParams.value = "1";
//        var balanceResult = Evm.Instance.AddBalance(balanceParams);
//        System.out.println("AddBalance result " + balanceResult);
//
//        System.out.println("ContractCall Store(5555) and transfer 1");
//        var callStoreParams = new Evm.ContractCallParams();
//        callStoreParams.address = createResult.address;
//        callStoreParams.origin = origin;
//        callStoreParams.input = Converter.fromHexString("6057361d" + secondValue);
//        callStoreParams.gasLimit = 100000;
//        callStoreParams.value = "1";
//        var callStoreResult = Evm.Instance.ContractCall(callStoreParams);
//        System.out.println("ContractCall result " + callStoreResult);
//
//        System.out.println("ContractCall Retrieve()");
//        var callRetrieveParams = new Evm.ContractCallParams();
//        callRetrieveParams.address = createResult.address;
//        callRetrieveParams.origin = origin;
//        callRetrieveParams.input = Converter.fromHexString("2e64cec1");
//        callRetrieveParams.gasLimit = 1000;
//        var callRetrieveResult = Evm.Instance.ContractCall(callRetrieveParams);
//        System.out.println("ContractCall result " + callRetrieveResult);
//        var retrievedNumber = new BigInteger(callRetrieveResult.ret);
//        System.out.println("ContractCall result parsed " + retrievedNumber);
//
//        System.out.println("Commit");
//        var commitResult = Evm.Instance.CommitState();
//        System.out.println("Commit result " + commitResult);
    }
}
