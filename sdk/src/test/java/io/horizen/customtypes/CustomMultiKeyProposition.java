package io.horizen.customtypes;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.google.common.primitives.Bytes;
import io.horizen.json.Views;
import io.horizen.proposition.ProofOfKnowledgeProposition;
import io.horizen.proposition.PropositionSerializer;
import io.horizen.proposition.ProvableCheckResult;
import io.horizen.proposition.ProvableCheckResultImpl;
import io.horizen.secret.PrivateKey25519;
import io.horizen.secret.Secret;
import io.horizen.utils.BytesUtils;
import io.horizen.utils.Ed25519;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;


/**
 * A custom proposition composed by two PublicKey25519.
 * Can be unlocked by just one private key, associated to any of the two public keys above.
 */
@JsonView(Views.Default.class)
public class CustomMultiKeyProposition implements ProofOfKnowledgeProposition<PrivateKey25519> {
    public static final int SINGLE_PUBLIC_KEY_LENGTH = Ed25519.publicKeyLength();

    @JsonProperty("publicKeyA")
    private byte[] pubKeyABytes;

    @JsonProperty("publicKeyB")
    private byte[] pubKeyBBytes;

    public CustomMultiKeyProposition(byte[] pubKeyABytes, byte[] pubKeyBBytes) {
        if(pubKeyABytes.length != SINGLE_PUBLIC_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKeyA length, %d expected, %d found", SINGLE_PUBLIC_KEY_LENGTH, pubKeyABytes.length));

        this.pubKeyABytes = Arrays.copyOf(pubKeyABytes, SINGLE_PUBLIC_KEY_LENGTH);

        if(pubKeyBBytes.length != SINGLE_PUBLIC_KEY_LENGTH)
            throw new IllegalArgumentException(String.format("Incorrect pubKeyB length, %d expected, %d found", SINGLE_PUBLIC_KEY_LENGTH, pubKeyBBytes.length));

        this.pubKeyBBytes = Arrays.copyOf(pubKeyBBytes, SINGLE_PUBLIC_KEY_LENGTH);
    }

    @Override
    public PropositionSerializer serializer() {
        return CustomMultiKeyPropositionSerializer.getSerializer();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        CustomMultiKeyProposition that = (CustomMultiKeyProposition) o;
        return Arrays.equals(pubKeyABytes, that.pubKeyABytes) && Arrays.equals(pubKeyBBytes, that.pubKeyBBytes);

    }

    @Override
    public int hashCode() {
        return Arrays.hashCode( Bytes.concat(pubKeyABytes, pubKeyBBytes));
    }

    public static int getLength() {
        return SINGLE_PUBLIC_KEY_LENGTH*2;
    }

    @Override
    public String toString() {
        return "CustomMultiKeyProposition{" +
                "pubKeyABytes=" + BytesUtils.toHexString(pubKeyABytes) +
                "pubKeyBBytes=" + BytesUtils.toHexString(pubKeyBBytes) +
                '}';
    }

    @Override
    public byte[] pubKeyBytes() {
        return  Bytes.concat(
                Arrays.copyOf(pubKeyABytes, SINGLE_PUBLIC_KEY_LENGTH),
                Arrays.copyOf(pubKeyBBytes, SINGLE_PUBLIC_KEY_LENGTH)
        );
    }

    @Override
    public ProvableCheckResult<PrivateKey25519> canBeProvedBy(List<Secret> secrectList) {
        List<PrivateKey25519> validKeys = new ArrayList<>();
        for (Secret s : secrectList){
            if (s instanceof PrivateKey25519
                    &&
                    (Arrays.equals(s.publicImage().pubKeyBytes(), this.pubKeyABytes) ||
                        Arrays.equals(s.publicImage().pubKeyBytes(), this.pubKeyBBytes))){
                validKeys.add((PrivateKey25519)s);
            }
        }
        if (validKeys.size()>0){
            return new ProvableCheckResultImpl<>(true, validKeys);
        }else{
            return new ProvableCheckResultImpl<>(false);
        }
    }

}
