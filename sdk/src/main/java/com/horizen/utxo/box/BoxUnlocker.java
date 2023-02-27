package com.horizen.utxo.box;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.horizen.proof.Proof;
import com.horizen.proposition.Proposition;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public interface BoxUnlocker<P extends Proposition>
{
    @JsonProperty("closedBoxId")
    byte[] closedBoxId();

    @JsonProperty("boxKey")
    Proof<P> boxKey();
}
