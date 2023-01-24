package com.horizen.account.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.utils.Secp256k1;
import com.horizen.evm.utils.Address;
import com.horizen.proposition.PropositionSerializer;
import com.horizen.proposition.SingleSecretProofOfKnowledgeProposition;
import com.horizen.serialization.Views;

import java.util.Objects;

@JsonView(Views.Default.class)
public final class AddressProposition
    implements SingleSecretProofOfKnowledgeProposition<PrivateKeySecp256k1> {

    @JsonProperty("address")
    private final Address address;

    public AddressProposition(Address address) {
        this.address = address;
    }

    public AddressProposition(byte[] address) {
        this(Address.fromBytes(address));
    }

    @Override
    public byte[] pubKeyBytes() {
        return address.toBytes();
    }

    @Override
    public PropositionSerializer serializer() {
        return AddressPropositionSerializer.getSerializer();
    }

    @Override
    public int hashCode() {
        return address.hashCode();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        AddressProposition other = (AddressProposition) o;
        return Objects.equals(address, other.address);
    }

    public Address address() {
        return address;
    }

    public String checksumAddress() {
        return Secp256k1.checksumAddress(address.toBytes());
    }

    @Override
    public String toString() {
        return String.format("AddressProposition{address=%s}", address.toString());
    }
}

