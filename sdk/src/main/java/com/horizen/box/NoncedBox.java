package com.horizen.box;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.horizen.proposition.Proposition;

public interface NoncedBox<P extends Proposition> extends Box<P>
{
    @JsonProperty("nonce")
    long nonce();
}
