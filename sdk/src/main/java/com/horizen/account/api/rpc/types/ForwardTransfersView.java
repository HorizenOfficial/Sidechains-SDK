package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.evm.utils.Address;
import com.horizen.serialization.Views;
import com.horizen.transaction.mainchain.ForwardTransfer;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.Arrays;
import java.util.List;
import java.util.TreeMap;

@JsonView(Views.Default.class)
public class ForwardTransfersView {
    private final TreeMap<Integer, ForwardTransferData> forwardTransfers = new TreeMap();

    public ForwardTransfersView(List<ForwardTransfer> transactions, boolean noPrefix) {
        if (transactions.size() > 0) {
            if (noPrefix)
                transactions.forEach(txOutput -> forwardTransfers.put(forwardTransfers.size(),
                        new ForwardTransferData(Numeric.toHexStringNoPrefix(Arrays.copyOf(txOutput.getFtOutput().propositionBytes(), Address.LENGTH)),
                                String.valueOf(txOutput.getFtOutput().amount()))));
            else
                transactions.forEach(txOutput -> forwardTransfers.put(forwardTransfers.size(),
                        new ForwardTransferData(Numeric.toHexString(Arrays.copyOf(txOutput.getFtOutput().propositionBytes(), Address.LENGTH)),
                                Numeric.toHexStringWithPrefix(BigInteger.valueOf(txOutput.getFtOutput().amount())))));
        }
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
