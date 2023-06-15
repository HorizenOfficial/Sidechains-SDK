package io.horizen.utxo.box;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.json.Views;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;

@JsonView(Views.Default.class)
public interface BoxUnlocker<P extends Proposition>
{
    @JsonProperty("closedBoxId")
    byte[] closedBoxId();

    @JsonProperty("boxKey")
    Proof<P> boxKey();
}
