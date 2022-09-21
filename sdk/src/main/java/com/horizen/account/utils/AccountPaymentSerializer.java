package com.horizen.account.utils;

import com.horizen.account.proposition.AddressProposition;
import com.horizen.account.proposition.AddressPropositionSerializer;
import scorex.core.serialization.ScorexSerializer;
import scorex.util.serialization.Reader;
import scorex.util.serialization.Writer;

import java.math.BigInteger;

public final class AccountPaymentSerializer
        implements ScorexSerializer<AccountPayment> {

    private final static AccountPaymentSerializer serializer;

    static {
        serializer = new AccountPaymentSerializer();
    }

    private AccountPaymentSerializer() {
        super();
    }

    public static AccountPaymentSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(AccountPayment payment, Writer w) {
        AddressPropositionSerializer.getSerializer().serialize(payment.address(), w);
        w.putInt(payment.value().toByteArray().length);
        w.putBytes(payment.value().toByteArray());
    }

     @Override
    public AccountPayment parse(Reader r) {
         AddressProposition address = AddressPropositionSerializer.getSerializer().parse(r);
         int valueLength = r.getInt();
         BigInteger value = new BigInteger(r.getBytes(valueLength));
         return new AccountPayment(address, value);
    }

}
