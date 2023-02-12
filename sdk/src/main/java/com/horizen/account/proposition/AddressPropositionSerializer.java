package com.horizen.account.proposition;

import com.horizen.proposition.PropositionSerializer;
import com.horizen.utils.Checker;
import sparkz.util.serialization.Reader;
import sparkz.util.serialization.Writer;

public final class AddressPropositionSerializer
        implements PropositionSerializer<AddressProposition> {
    private static final AddressPropositionSerializer serializer;

    static {
        serializer = new AddressPropositionSerializer();
    }

    private AddressPropositionSerializer() {
        super();
    }

    public static AddressPropositionSerializer getSerializer() {
        return serializer;
    }

    @Override
    public void serialize(AddressProposition proposition, Writer writer) {
        writer.putBytes(proposition.pubKeyBytes());
    }

    @Override
    public AddressProposition parse(Reader reader) {
        byte[] address = Checker.readBytes(reader, AddressProposition.LENGTH, "Address Proposition");
        return new AddressProposition(address);
    }
}
