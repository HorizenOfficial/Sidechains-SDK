package io.horizen.utxo.box;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import io.horizen.proof.Proof;
import io.horizen.proposition.Proposition;
import io.horizen.json.Views;

@JsonView(Views.Default.class)
public interface BoxUnlocker<P extends Proposition>
{
    @JsonProperty("closedBoxId")
    byte[] closedBoxId();

    @JsonProperty("boxKey")
    Proof<P> boxKey();
}
