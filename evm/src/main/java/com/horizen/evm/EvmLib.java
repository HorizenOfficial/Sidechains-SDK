package com.horizen.evm;

import com.sun.jna.Library;
import com.sun.jna.Pointer;

public interface EvmLib extends Library {
    void Free(Pointer ptr);

    Evm.InteropResult Initialize(String path);

    Evm.InteropResult SetStateRoot(String stateRoot);

    Evm.StateRootResult GetIntermediateStateRoot();

    Evm.StateRootResult CommitState();

    Evm.ContractCreateResult ContractCreate(Evm.ContractCreateParams params);

    Evm.ContractCallResult ContractCall(Evm.ContractCallParams params);

    Evm.InteropResult SetBalance(Evm.BalanceParams params);

    Evm.InteropResult AddBalance(Evm.BalanceParams params);

    Evm.InteropResult SubBalance(Evm.BalanceParams params);
}
