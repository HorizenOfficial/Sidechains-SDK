package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.utils.Address;
import com.horizen.serialization.Views;
import com.horizen.transaction.mainchain.ForwardTransfer;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.*;

@JsonView(Views.Default.class)
public class ForwardTransfersView {
    private final TreeMap<Integer, ForwardTransferData> forwardTransfers = new TreeMap();
    public ForwardTransfersView(List<ForwardTransfer> transactions) {
        transactions.forEach(txOutput -> forwardTransfers.put(forwardTransfers.size(), new ForwardTransferData(Numeric.toHexString(Arrays.copyOf(txOutput.getFtOutput().propositionBytes(), Address.LENGTH)),
                Numeric.toHexStringWithPrefix(BigInteger.valueOf(txOutput.getFtOutput().amount())))));
    }

    public TreeMap<Integer, ForwardTransferData> getForwardTransfers() {
        return this.forwardTransfers;
    }

    @JsonView(Views.Default.class)
    private static class ForwardTransferData {
        String to;
        String value;

        public ForwardTransferData(String to, String value) {
            this.to = to;
            this.value = value;
        }

        public String getValue() {
            return this.value;
        }

        public String getTo() {
            return this.to;
        }
    }
}
