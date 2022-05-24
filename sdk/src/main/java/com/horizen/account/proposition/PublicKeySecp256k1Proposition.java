package com.horizen.account.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.ScorexEncoding;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PropositionSerializer;
import com.horizen.serialization.Views;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@JsonView(Views.Default.class)
public final class PublicKeySecp256k1Proposition extends ScorexEncoding
    implements ProofOfKnowledgeProposition<PrivateKeySecp256k1> {

    @JsonProperty("address")
    private final byte[] address;

    public PublicKeySecp256k1Proposition(byte[] address) {
        if (address.length != Keys.ADDRESS_LENGTH_IN_HEX/2) {
            throw new IllegalArgumentException(String.format(
                "Incorrect address length, %d expected, %d found",
                Keys.ADDRESS_LENGTH_IN_HEX/2,
                address.length
            ));
        }

        this.address = Arrays.copyOf(address, Keys.ADDRESS_LENGTH_IN_HEX/2);
    }

    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(address, Keys.ADDRESS_LENGTH_IN_HEX/2);
    }

    @Override
    public PropositionSerializer serializer() {
        return PublicKeySecp256k1PropositionSerializer.getSerializer();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(address);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof PublicKeySecp256k1Proposition)) return false;
        if (obj == this) return true;
        var other = (PublicKeySecp256k1Proposition) obj;
        return Arrays.equals(address, other.address);
    }

    public byte[] address() {
        return address;
    }

    public String checksumAddress() {
        return Keys.toChecksumAddress(Numeric.toHexString(address()));
    }

    @Override
    public String toString() {
        return String.format("PublicKeySecp256k1Proposition{address=%s}", Numeric.toHexString(address));
    }
}

