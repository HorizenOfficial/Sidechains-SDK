package com.horizen.evm.library;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface LibEvmInterface extends Library {
    void Free(Pointer ptr);

    LibEvm.InteropResult Initialize(String path);

    <R> LibEvm.InteropResult<R> Invoke(String method, LibEvm.InvokeParams args);

//    Evm.HandleResult OpenState(String stateRootHex);
//
//    Evm.InteropResult CloseState(int handle);
//
//    Evm.StateRootResult GetIntermediateStateRoot(int handle);
//
//    Evm.StateRootResult CommitState();
//
//    Evm.ContractCreateResult ContractCreate(Evm.ContractCreateParams params);
//
//    Evm.ContractCallResult ContractCall(Evm.ContractCallParams params);
//
//    Evm.InteropResult SetBalance(Evm.BalanceParams params);
//
//    Evm.InteropResult AddBalance(Evm.BalanceParams params);
//
//    Evm.InteropResult SubBalance(Evm.BalanceParams params);
}
