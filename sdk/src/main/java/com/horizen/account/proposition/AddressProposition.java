package com.horizen.account.proposition;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.SparkzEncoding;
import com.horizen.account.secret.PrivateKeySecp256k1;
import com.horizen.account.utils.Account;
import com.horizen.proposition.*;
import com.horizen.serialization.Views;
import com.horizen.utils.BytesUtils;
import org.web3j.crypto.Keys;
import org.web3j.utils.Numeric;
import java.util.Arrays;

@JsonView(Views.Default.class)
public final class AddressProposition extends SparkzEncoding
        implements AbstractSingleSecretProofOfKnowledgeProposition<PrivateKeySecp256k1> {

    @JsonProperty("address")
    private final byte[] address;

    public AddressProposition(byte[] address) {
        if (address.length != Account.ADDRESS_SIZE) {
            throw new IllegalArgumentException(String.format(
                    "Incorrect address length, %d expected, %d found",
                    Account.ADDRESS_SIZE,
                    address.length
            ));
        }

        this.address = Arrays.copyOf(address, Account.ADDRESS_SIZE);
    }

    @Override
    public byte[] pubKeyBytes() {
        return Arrays.copyOf(address, Account.ADDRESS_SIZE);
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

    public byte[] address() {
        return address;
    }

    public String checksumAddress() {
        return Keys.toChecksumAddress(Numeric.toHexString(address()));
    }

    @Override
    public String toString() {
        return String.format("AddressProposition{address=%s}", BytesUtils.toHexString(address));
    }
}

