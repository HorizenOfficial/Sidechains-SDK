package com.horizen.account.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.ScorexEncoding;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.utils.Secp256k1;
import com.horizen.proposition.ProofOfKnowledgeProposition;
import com.horizen.proposition.PropositionSerializer;
import com.horizen.serialization.Views;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;

import java.util.Arrays;

@JsonView(Views.Default.class)
public final class PublicKeySecp256k1Proposition extends ScorexEncoding
    implements ProofOfKnowledgeProposition<PrivateKeySecp256k1> {

    @JsonProperty("publicKey")
    private final byte[] publicKey;

    public PublicKeySecp256k1Proposition(byte[] publicKey) {
        if (publicKey.length != Secp256k1.PUBLIC_KEY_SIZE) {
            throw new IllegalArgumentException(String.format(
                "Incorrect publicKey length, %d expected, %d found",
                Secp256k1.PUBLIC_KEY_SIZE,
                publicKey.length
            ));
        }

        this.publicKey = Arrays.copyOf(publicKey, Secp256k1.PUBLIC_KEY_SIZE);
    }

    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(publicKey, Secp256k1.PUBLIC_KEY_SIZE);
    }

    @Override
    public PropositionSerializer serializer() {
        return PublicKeySecp256k1PropositionSerializer.getSerializer();
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(publicKey);
    }

    @Override
    public boolean equals(Object obj) {
        if (obj == null) return false;
        if (!(obj instanceof PublicKeySecp256k1Proposition)) return false;
        if (obj == this) return true;
        var other = (PublicKeySecp256k1Proposition) obj;
        return Arrays.equals(publicKey, other.publicKey);
    }

    public String address() {
        return Keys.getAddress(Numeric.toBigInt(publicKey));
    }

    public String checksumAddress() {
        return Keys.toChecksumAddress(address());
    }

    @Override
    public String toString() {
        return String.format("PublicKeySecp256k1Proposition{pubKeyBytes=%s}", Numeric.toHexString(publicKey));
    }
}

