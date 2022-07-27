package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.*;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.util.ArrayList;

@JsonView(Views.Default.class)
public class EthereumTransactionView {
    private String type;
    private String nonce;
    private String to;
    private String gas;
    private String value;
    private String input;
    private String maxPriorityFeePerGas;
    private String maxFeePerGas;
    private String gasPrice;
    private ArrayList<Object> accessList;
    private String chainId;
    private String yParity;
    private String r;
    private String s;


    public EthereumTransactionView() {
    }

    public EthereumTransactionView(EthereumTransaction ethTx) {
        type = String.valueOf(ethTx.transactionTypeId());
        nonce = Numeric.toHexStringWithPrefix(ethTx.getNonce());
        to = ethTx.getToAddress();
        gas = Numeric.toHexStringWithPrefix(ethTx.getGasLimit());
        value = Numeric.toHexStringWithPrefix(ethTx.getValue());
        input = Numeric.toHexString(ethTx.getData());
        if (ethTx.isEIP1559()){
            maxPriorityFeePerGas = Numeric.toHexStringWithPrefix(ethTx.getMaxPriorityFeePerGas());
            maxFeePerGas = Numeric.toHexStringWithPrefix(ethTx.getMaxFeePerGas());
        } else {
            gasPrice = Numeric.toHexStringWithPrefix(ethTx.getGasPrice());
        }
        accessList = new ArrayList<>();
        if (ethTx.getChainId() != null) chainId = String.valueOf(ethTx.getChainId());
        yParity = Numeric.toHexString(ethTx.getSignature().getV());
        r = Numeric.toHexString(ethTx.getSignature().getR());
        s = Numeric.toHexString(ethTx.getSignature().getS());
    }

    public String getType() { return this.type; }
    public String getNonce() { return this.nonce; }
    public String getTo() { return this.to; }
    public String getGas() { return this.gas; }
    public String getValue() { return this.value; }
    public String getInput() { return this.input; }
    public String getMaxPriorityFeePerGas() { return this.maxPriorityFeePerGas; }
    public String getMaxFeePerGas() { return this.maxFeePerGas; }
    public String getGasPrice() { return this.gasPrice; }
    public ArrayList<Object> getAccessList() { return this.accessList; }
    public String getChainId() { return this.chainId; }
    public String getyParity() { return this.yParity; }
    public String getR() { return this.r; }
    public String getS() { return this.s; }
}

