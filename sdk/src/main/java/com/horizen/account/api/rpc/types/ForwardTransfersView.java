package com.horizen.account.api.rpc.types;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.utils.MainchainTxCrosschainOutputAddressUtil;
import com.horizen.account.utils.ZenWeiConverter;
import com.horizen.serialization.Views;
import com.horizen.transaction.mainchain.ForwardTransfer;
import org.web3j.utils.Numeric;

import java.util.List;
import java.util.stream.Collectors;

@JsonView(Views.Default.class)
public class ForwardTransfersView {
    private final List<ForwardTransferData> forwardTransfers;

    public ForwardTransfersView(List<ForwardTransfer> transactions, boolean isHttpResponse) {
        forwardTransfers = transactions.stream().map(tx -> {
            var ftOutput = tx.getFtOutput();
            var address = MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOutput.propositionBytes());
            var weiValue = ZenWeiConverter.convertZenniesToWei(ftOutput.amount());
            return new ForwardTransferData(
                isHttpResponse ? address.toStringNoPrefix() : address.toString(),
                isHttpResponse ? String.valueOf(weiValue) : Numeric.toHexStringWithPrefix(weiValue)
            );
        }).collect(Collectors.toList());
    }

    public List<ForwardTransferData> getForwardTransfers() {
        return this.forwardTransfers;
    }

    @JsonView(Views.Default.class)
    private static class ForwardTransferData {
        public final String to;
        public final String value;

        public ForwardTransferData(String to, String value) {
            this.to = to;
            this.value = value;
        }
    }
}
