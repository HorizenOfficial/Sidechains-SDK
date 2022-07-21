package com.horizen.account.abi.util;


import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Bytes20;
import org.web3j.abi.datatypes.generated.Uint256;

public final class WithdrawalRequest extends StaticStruct {


    public final Bytes20 addr;
    public final Uint256 amount;

    public WithdrawalRequest(Bytes20 addr , Uint256 amount ) {
        super(addr,amount);
        this.addr = addr;
        this.amount = amount;

    }

}

