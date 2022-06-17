package com.horizen.account.api.rpc.service;

import com.horizen.account.api.rpc.utils.Data;
import com.horizen.account.api.rpc.utils.Quantity;
import com.horizen.account.node.AccountNodeView;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;

import java.util.ArrayList;

public class EthService extends RpcService {
    public final AccountNodeView nodeView;

    public EthService(AccountNodeView view){
        nodeView = view;
    }

    @RpcMethod("eth_getBlockByNumber")
    public EthBlock.Block getBlockByNumber(Quantity tag, Boolean rtnTxObj) {
        return new EthBlock.Block("0x1", "0x56",
                "0x57", "0x58", "0x59",
                "0x0", "0x0",
                "0x0", "0x0", "0",
                "0", "0", "0",
                "0", "0",
                "1", "3000", "2000",
                "22", new ArrayList<EthBlock.TransactionResult>(), new ArrayList<String>(), new ArrayList<String>());
    }

    @RpcMethod("eth_call")
    public Data call(RawTransaction transaction, Quantity tag) {
        return new Data(new byte[0]);
    }

    @RpcMethod("eth_blockNumber")
    public Quantity blockNumber() {
        return new Quantity("0x42");
    }

    @RpcMethod("eth_chainId")
    public Quantity chainId() {
        return new Quantity("0x1337");
    }

    @RpcMethod("eth_getBalance")
    public Quantity GetBalance(String address, Quantity blockNumberOrTag) {
        return new Quantity("0x12345678");
    }

    @RpcMethod("eth_getTransactionCount")
    public Quantity getTransactionCount(Data address, Quantity tag) {
        return new Quantity("0x5");
    }

    @RpcMethod("net_version")
    public String version() {
        return "1";
    }

    @RpcMethod("eth_estimateGas")
    public Quantity estimateGas(RawTransaction transaction, Quantity tag) {
        return new Quantity("0x1");
    }

    @RpcMethod("eth_gasPrice")
    public Quantity gasPrice() {
        return new Quantity("0x20");
    }

    @RpcMethod("eth_getTransactionByHash")
    public Transaction getTransactionByHash(Data transactionHash) {
        return new Transaction();
    }

    @RpcMethod("eth_getTransactionReceipt")
    public TransactionReceipt getTransactionReceipt() {
        return new TransactionReceipt();
    }
}
