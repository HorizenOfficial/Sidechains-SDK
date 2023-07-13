package io.horizen.account.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.account.secret.PrivateKeySecp256k1;
import io.horizen.account.utils.Secp256k1;
import io.horizen.evm.Address;
import io.horizen.json.Views;
import io.horizen.proposition.PropositionSerializer;
import io.horizen.proposition.SingleSecretProofOfKnowledgeProposition;
import io.horizen.utils.BytesUtils;

import java.util.Arrays;

@JsonView(Views.Default.class)
public final class AddressProposition implements SingleSecretProofOfKnowledgeProposition<PrivateKeySecp256k1> {

    public static final int LENGTH = 20;
    public static final AddressProposition ZERO = new AddressProposition(Address.ZERO);

    @JsonProperty("address")
    private final byte[] address;

    public AddressProposition(byte[] address) {
        if (address.length != LENGTH) {
            throw new IllegalArgumentException(String.format(
                "Incorrect address length, %d expected, %d found",
                LENGTH,
                address.length
            ));
        }

        this.address = Arrays.copyOf(address, LENGTH);
    }

    public AddressProposition(Address address) {
        this(address.toBytes());
    }

    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(address, LENGTH);
    }

    @Override
    public PropositionSerializer serializer() {
        return AddressPropositionSerializer.getSerializer();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(address);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof AddressProposition)) return false;
        if (obj == this) return true;
        var other = (AddressProposition) obj;
        return Arrays.equals(address, other.address);
    }

    public Address address() {
        return new Address(address);
    }

    public String checksumAddress() {
        return Secp256k1.checksumAddress(address);
    }

    @Override
    public String toString() {
        return String.format("AddressProposition{address=%s}", BytesUtils.toHexString(address));
    }
}

