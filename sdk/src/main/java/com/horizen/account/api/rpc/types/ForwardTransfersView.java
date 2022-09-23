package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.utils.MainchainTxCrosschainOutputAddressUtil;
import com.horizen.account.utils.ZenWeiConverter;
import com.horizen.block.MainchainTxForwardTransferCrosschainOutput;
import com.horizen.serialization.Views;
import com.horizen.transaction.mainchain.ForwardTransfer;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.util.List;
import java.util.TreeMap;

@JsonView(Views.Default.class)
public class ForwardTransfersView {
    private final TreeMap<Integer, ForwardTransferData> forwardTransfers = new TreeMap();

    public ForwardTransfersView(List<ForwardTransfer> transactions, boolean noPrefix) {
        for (int i = 0; i < transactions.size(); i++) {
            MainchainTxForwardTransferCrosschainOutput ftOutput = transactions.get(i).getFtOutput();
            var to = "";
            var value = "";
            AddressProposition address = new AddressProposition(
                    MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOutput.propositionBytes()));
            BigInteger weiValue = ZenWeiConverter.convertZenniesToWei(ftOutput.amount());
            if (noPrefix) {
                to = Numeric.toHexStringNoPrefix(address.address());
                value = String.valueOf(weiValue);
            } else {
                to = Numeric.toHexString(address.address());
                value = Numeric.toHexStringWithPrefix(weiValue);
            }
            forwardTransfers.put(i, new ForwardTransferData(to, value));
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
