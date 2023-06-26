package io.horizen.account.proposition;

import io.horizen.proposition.PropositionSerializer;
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
        byte[] address = reader.getBytes(AddressProposition.LENGTH);
        return new AddressProposition(address);
    }
}
