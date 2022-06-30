package com.horizen.account.api.rpc.service;

import com.horizen.SidechainSettings;
import com.horizen.account.api.rpc.utils.Data;
import com.horizen.account.api.rpc.utils.Quantity;
import com.horizen.account.api.rpc.utils.ResponseObject;
import com.horizen.account.block.AccountBlock;
import com.horizen.account.node.AccountNodeView;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.transaction.AccountTransaction;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.params.NetworkParams;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import org.web3j.crypto.RawTransaction;
import org.web3j.protocol.core.methods.response.EthBlock;
import org.web3j.protocol.core.methods.response.Transaction;
import org.web3j.protocol.core.methods.response.TransactionReceipt;
import org.web3j.utils.Numeric;

import java.io.IOException;
import java.math.BigInteger;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Optional;

public class EthService extends RpcService {
    public final AccountNodeView nodeView;
    public final NetworkParams networkParams;
    public final SidechainSettings sidechainSettings;

    public EthService(AccountNodeView view, NetworkParams params, SidechainSettings settings){
        nodeView = view;
        networkParams = params;
        sidechainSettings = settings;
    }

    @RpcMethod("eth_getBlockByNumber")
    public EthBlock.Block getBlockByNumber(Quantity tag, Boolean rtnTxObj) {
        //TODO: Implement getting block information
        nodeView.getNodeHistory().getBlockById(tag.getValue());
        return new EthBlock.Block("0x1", "0x56",
                "0x57", "0x58", "0x59",
                "0x0", "0x0",
                "0x0", "0x0", "0",
                "0", "0", "0",
                "0", "0",
                "1", "3000", "2000",
                "22", new ArrayList<EthBlock.TransactionResult>(), new ArrayList<String>(), new ArrayList<String>(), "");
    }

    @RpcMethod("eth_call")
    public Data call(RawTransaction transaction, Quantity tag) {
        // Executes a new message call immediately without creating a transaction on the block chain
        // TODO: add direct evm execution
        return new Data(new byte[0]);
    }

    @RpcMethod("eth_blockNumber")
    public Quantity blockNumber() {
        return new Quantity(String.valueOf(getBestBlock().header().id()));
    }

    @RpcMethod("eth_chainId")
    public Quantity chainId() {
        return new Quantity(Numeric.toHexStringWithPrefix(BigInteger.valueOf(networkParams.chainId())));
    }

    @RpcMethod("eth_getBalance")
    public Quantity GetBalance(String address, Quantity blockNumberOrTag) {
        return new Quantity(getBalance(address, blockNumberOrTag));
    }

    @RpcMethod("eth_getTransactionCount")
    public Quantity getTransactionCount(Data address, Quantity tag) {
        // return nonce of address
        // TODO: We need a function to return the transaction count of given address
        return new Quantity("0x5");
    }

    @RpcMethod("net_version")
    public String version() {
        return sidechainSettings.genesisData().mcNetwork();
    }

    @RpcMethod("eth_estimateGas")
    public Quantity estimateGas(RawTransaction transaction, Quantity tag) {
        // TODO: We need an estimateGas function to execute EVM and get the gasUsed
        return new Quantity("0x1");
    }

    @RpcMethod("eth_gasPrice")
    public Quantity gasPrice() {
        // TODO: Get the real gasPrice later
        return new Quantity("0x20");
    }

    @RpcMethod("eth_getTransactionByHash")
    public ResponseObject getTransactionByHash(Data transactionHash) throws IOException {
        // TODO: Add a function to get transaction by hash instead of id
        //var tx = getTransaction(transactionHash);
        //if (tx.isEmpty()) return null;
        //var ethTx = getEthereumTransaction(tx);

        //return new ResponseObject(getTxObject(transactionHash, ethTx, tx));
        return new ResponseObject(getTxObject(transactionHash, null, null));
    }

    private Transaction getTxObject(Data transactionHash, EthereumTransaction ethTx, Optional<AccountTransaction<Proposition, Proof<Proposition>>> tx){
        return new Transaction("0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                null, // TODO: creates contract hash
                "0x0",
                "0x0",
                "0x0",
                "0x0",
                0,
                "0x0",
                "0x0",
                "0x0",
                Collections.emptyList() // TODO: access list
        );/*
        return new Transaction(Numeric.toHexString(transactionHash.getValue()),
                Numeric.toHexStringWithPrefix(tx.get().getNonce()),
                String.valueOf(getBestBlock().hashCode()),
                blockNumber().getValue(),
                chainId().getValue(),
                String.valueOf(tx.get().id()),
                Numeric.toHexString(getFrom(tx).address()),
                Numeric.toHexString(getTo(tx).address()),
                Numeric.toHexStringWithPrefix(tx.get().getValue()),
                Numeric.toHexStringWithPrefix(tx.get().getGasLimit()),
                Numeric.toHexStringWithPrefix(tx.get().getGasPrice()),
                Numeric.toHexString(ethTx.getData()),
                null, // TODO: creates contract hash
                Numeric.toHexString(getFrom(tx).pubKeyBytes()),
                Numeric.toHexString(ethTx.getData()),
                Numeric.toHexString(ethTx.getSignature().getR()),
                Numeric.toHexString(ethTx.getSignature().getS()),
                getEthereumTransaction(tx).getSignature().getV()[0],
                String.valueOf(getEthereumTransaction(tx).version()),
                Numeric.toHexStringWithPrefix(ethTx.getMaxFeePerGas()),
                Numeric.toHexStringWithPrefix(ethTx.getMaxPriorityFeePerGas()),
                Collections.emptyList() // TODO: access list
        );*/
    }

    @RpcMethod("eth_getTransactionReceipt")
    public TransactionReceipt getTransactionReceipt(Data transactionHash) {
        // TODO: Receipts will be supported later on
        return new TransactionReceipt(Numeric.toHexString(transactionHash.getValue()),
                "0x0", // TODO: get transaction index
                "0x0", // get block hash
                "0x0", // get block number
                "0x0", // get cumulative gas used
                "0x0", // get gas used
                "null", // return contract address if one is created
                "null", // return root
                "0x1", // return status
                "0x0", // add from
                "0x0", // add to
                Collections.emptyList(), // return logs
                "0x0", // return logsBloom
                "0x0", // insert revert reason
                "0x0", // insert tx type
                "0x0"  // insert effective gas price
        );
    }

    private String getBalance(String address, Quantity tag){
        // TODO: Add blockNumberOrTag handling
        var balance = nodeView.getNodeState().getBalance(Numeric.hexStringToByteArray(address));
        if (balance.isFailure())
            return "0x0";
        return Numeric.toHexStringWithPrefix(balance.get());
    }
    private Optional<AccountTransaction<Proposition, Proof<Proposition>>> getTransaction(Data transactionHash){
        var transactionId = Numeric.toHexString(transactionHash.getValue());
        return nodeView.getNodeHistory().searchTransactionInsideBlockchain(transactionId);
    }

    private EthereumTransaction getEthereumTransaction(Optional<AccountTransaction<Proposition, Proof<Proposition>>> tx){
        return (EthereumTransaction) tx.get().getSignature();
    }

    private AddressProposition getFrom(Optional<AccountTransaction<Proposition, Proof<Proposition>>> tx){
        return (AddressProposition) tx.get().getFrom();
    }

    private AddressProposition getTo(Optional<AccountTransaction<Proposition, Proof<Proposition>>> tx){
        return (AddressProposition) tx.get().getTo();
    }

    private AccountBlock getBestBlock(){
        return nodeView.getNodeHistory().getBestBlock();
    }
}
