package io.horizen.account.state;

import org.web3j.abi.datatypes.StaticStruct;
import org.web3j.abi.datatypes.generated.Bytes1;
import org.web3j.abi.datatypes.generated.Bytes32;
import org.web3j.abi.datatypes.generated.Uint256;

import java.math.BigInteger;

public class AccountForgingStakeInfoABI extends StaticStruct {
    public byte[] stakeId;

    public BigInteger amount;
    public String owner;
    public byte[] pubKey;
    public byte[] vrf1;
    public byte[] vrf2;

    public AccountForgingStakeInfoABI(byte[] stakeId, BigInteger amount, String owner, byte[] pubKey, byte[] vrf1, byte[] vrf2 ) {
        super(
                new Bytes32(stakeId),
                new Uint256(amount),
                new org.web3j.abi.datatypes.Address(owner),
                new Bytes32(pubKey),
                new Bytes32(vrf1),
                new Bytes32(vrf2)
                );
        this.stakeId = stakeId;
        this.amount = amount;
        this.owner = owner;
        this.pubKey = pubKey;
        this.vrf1 = vrf1;
        this.vrf2 = vrf2;
    }

    public AccountForgingStakeInfoABI(Bytes32 stakeId, Uint256 amount, org.web3j.abi.datatypes.Address owner, Bytes32 pubKey, Bytes32 vrf1, Bytes1 vrf2 ) {
        super(stakeId, amount, owner, pubKey, vrf1, vrf2);
        this.stakeId = stakeId.getValue();
        this.amount = amount.getValue();
        this.owner = owner.getValue();
        this.pubKey = pubKey.getValue();
        this.vrf1 = vrf1.getValue();
        this.vrf2 = vrf2.getValue();
    }

}
