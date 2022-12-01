package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.receipt.EthereumReceipt;
import com.horizen.account.transaction.EthereumTransaction;
import com.horizen.account.utils.Account;
import com.horizen.serialization.Views;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.ArrayList;

@JsonView(Views.Default.class)
public class EthereumTransactionView {
    public final String blockHash;
    public final String blockNumber;
    public final String from;
    public final String hash;
    public final String transactionIndex;
    public final String type;
    public final String nonce;
    public final String to;
    public final String gas;
    public final String value;
    public final String input;
    public final String maxPriorityFeePerGas;
    public final String maxFeePerGas;
    public final String gasPrice;
    public final ArrayList<Object> accessList;
    public final String chainId;
    public final String v;
    public final String r;
    public final String s;

    public EthereumTransactionView(EthereumReceipt receipt, EthereumTransaction ethTx, BigInteger baseFee) {
        type = Numeric.prependHexPrefix((Integer.toHexString(ethTx.version())));
        nonce = Numeric.encodeQuantity(ethTx.getNonce());
        to = Numeric.cleanHexPrefix(ethTx.getToAddressString()).length() != 2 * Account.ADDRESS_SIZE ? null : ethTx.getToAddressString();
        gas = Numeric.encodeQuantity(ethTx.getGasLimit());
        value = Numeric.encodeQuantity(ethTx.getValue());
        input = Numeric.toHexString(ethTx.getData());
        gasPrice = Numeric.encodeQuantity(ethTx.getEffectiveGasPrice(baseFee));
        if (ethTx.isEIP1559()) {
            maxPriorityFeePerGas = Numeric.encodeQuantity(ethTx.getMaxPriorityFeePerGas());
            maxFeePerGas = Numeric.encodeQuantity(ethTx.getMaxFeePerGas());
            // calculate effective gas price
        } else {
            maxPriorityFeePerGas = null;
            maxFeePerGas = null;
        }
        chainId = ethTx.getChainId() == null ? null : Numeric.encodeQuantity(BigInteger.valueOf(ethTx.getChainId()));
        v = (ethTx.getSignature() != null) ? Numeric.toHexString(ethTx.getSignature().getV()) : null;
        r = (ethTx.getSignature() != null) ? Numeric.toHexString(ethTx.getSignature().getR()) : null;
        s = (ethTx.getSignature() != null) ? Numeric.toHexString(ethTx.getSignature().getS()) : null;
        blockHash = Numeric.toHexString(receipt.blockHash());
        blockNumber = Numeric.encodeQuantity(BigInteger.valueOf(receipt.blockNumber()));
        from = (ethTx.getFrom() != null) ? Numeric.toHexString(ethTx.getFrom().address()) : null;
        hash = Numeric.toHexString(receipt.transactionHash());
        transactionIndex = Numeric.encodeQuantity(BigInteger.valueOf(receipt.transactionIndex()));
        accessList = null;
    }
}

