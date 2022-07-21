package com.horizen.account.abi.util;


import org.web3j.abi.datatypes.Address;
import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Bytes1;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;

public final class AccountForgingStakeInfo extends StaticStruct {


    public final Bytes32 stakeId;
    public final Uint256 amount;
    public final Address ownerAddr;
    public final Bytes32 blockSignPublicKey;
    public final Bytes32 vrfFirst32Bytes;
    public final Bytes1 vrfLastByte;


    public AccountForgingStakeInfo(Bytes32 stakeId, Uint256 amount, Address ownerAddr, Bytes32 blockSignPublicKey, Bytes32 vrfFirst32Bytes, Bytes1 vrfLastByte) {
        super(stakeId,amount, ownerAddr, blockSignPublicKey, vrfFirst32Bytes, vrfLastByte);
        this.stakeId = stakeId;
        this.amount = amount;
        this.ownerAddr = ownerAddr;
        this.blockSignPublicKey = blockSignPublicKey;
        this.vrfFirst32Bytes = vrfFirst32Bytes;
        this.vrfLastByte = vrfLastByte;

    }


}

