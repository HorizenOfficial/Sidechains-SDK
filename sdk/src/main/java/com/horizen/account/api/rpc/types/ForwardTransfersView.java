package com.horizen.account.api.rpc.types;

import com.horizen.account.utils.MainchainTxCrosschainOutputAddressUtil;
import com.horizen.account.utils.ZenWeiConverter;
import com.horizen.evm.Address;
import com.horizen.transaction.mainchain.ForwardTransfer;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class ForwardTransfersView {
    public final List<ForwardTransferData> forwardTransfers;

    public ForwardTransfersView(List<ForwardTransfer> transactions) {
        forwardTransfers = transactions.stream().map(tx -> {
            var ftOutput = tx.getFtOutput();
            var address = MainchainTxCrosschainOutputAddressUtil.getAccountAddress(ftOutput.propositionBytes());
            var weiValue = ZenWeiConverter.convertZenniesToWei(ftOutput.amount());
            return new ForwardTransferData(address, weiValue);
        }).collect(Collectors.toList());
    }

    private static class ForwardTransferData {
        public final Address to;
        public final BigInteger value;

        public ForwardTransferData(Address to, BigInteger value) {
            this.to = to;
            this.value = value;
        }
    }
}
