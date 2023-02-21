package com.horizen.account.api.rpc.types;

import com.horizen.account.chain.AccountFeePaymentsInfo;
import io.horizen.evm.Address;
import scala.collection.JavaConverters;

import java.math.BigInteger;
import java.util.List;
import java.util.stream.Collectors;

public class FeePaymentsView {
    public final List<FeePaymentData> payments;

    public FeePaymentsView(AccountFeePaymentsInfo info) {
        payments = JavaConverters
            .seqAsJavaList(info.payments())
            .stream()
            .map(payment -> new FeePaymentData(payment.address().address(), payment.value()))
            .collect(Collectors.toList());
    }

    private static class FeePaymentData {
        public final Address address;
        public final BigInteger value;

        public FeePaymentData(Address address, BigInteger value) {
            this.address = address;
            this.value = value;
        }
    }
}
