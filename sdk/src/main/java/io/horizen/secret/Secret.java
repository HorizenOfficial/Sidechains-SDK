package io.horizen.secret;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.proof.ProofOfKnowledge;
import io.horizen.proposition.ProofOfKnowledgeProposition;
import io.horizen.json.Views;

@JsonView(Views.Default.class)
@JsonIgnoreProperties({"secretTypeId", "serializer", "sign", "owns", "publicKeyValid"})
public interface Secret
    extends sparkz.core.serialization.BytesSerializable
{
    byte secretTypeId();

    @JsonProperty("publicImage")
    ProofOfKnowledgeProposition publicImage();

    @Override
    @JsonProperty("bytes")
    default byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    SecretSerializer serializer();

    boolean owns(ProofOfKnowledgeProposition proposition);

    ProofOfKnowledge sign(byte[] message);

    @JsonProperty("typeName")
    default String typeName() {
        return this.getClass().getSimpleName();
    }

    @JsonProperty("isCustom")
    default Boolean isCustom() { return true; } // All secrets presume customs until it not defined otherwise

    @JsonProperty("publicKeyValid")
    Boolean isPublicKeyValid();
}
