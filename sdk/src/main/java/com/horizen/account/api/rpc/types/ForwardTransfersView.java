package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.utils.MainchainTxCrosschainOutputAddressUtil;
import com.horizen.account.utils.ZenWeiConverter;
import com.horizen.serialization.Views;
import com.horizen.transaction.mainchain.ForwardTransfer;
import org.web3j.utils.Numeric;

import java.util.ArrayList;
import java.util.List;

@JsonView(Views.Default.class)
public class ForwardTransfersView {
    private final List<ForwardTransferData> forwardTransfers = new ArrayList<>();

    public ForwardTransfersView(List<ForwardTransfer> transactions, boolean noPrefix) {
        for (ForwardTransfer transaction : transactions) {
            var ftOutput = transaction.getFtOutput();
            var to = "";
            var value = "";
            var address = new AddressProposition(
                    MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOutput.propositionBytes()));
            var weiValue = ZenWeiConverter.convertZenniesToWei(ftOutput.amount());
            if (noPrefix) {
                to = Numeric.toHexStringNoPrefix(address.address());
                value = String.valueOf(weiValue);
            } else {
                to = Numeric.toHexString(address.address());
                value = Numeric.toHexStringWithPrefix(weiValue);
            }
            forwardTransfers.add(new ForwardTransferData(to, value));
        }
    }

    public List<ForwardTransferData> getForwardTransfers() {
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
