package com.horizen.account.api.rpc.service;

public class EthService extends RpcService {
    @RpcMethod("eth_chainId")
    public Quantity chainId() {
        return new Quantity("0x666");
    }

    @RpcMethod("eth_getBalance")
    public Quantity GetBalance(String address, Quantity blockNumberOrTag) {
        return new Quantity("0x12345678");
    }
}
