package com.horizen.box;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.Proposition;
import com.horizen.serialization.Views;

/**
 * Just to not farget what is a Box interface
 *
 trait Box[P <: Proposition] extends BytesSerializable {
 val value: Box.Amount
 val proposition: P

 val id: ADKey
 }

 object Box {
 type Amount = Long
 }

 */

@JsonView(Views.Default.class)
@JsonIgnoreProperties({"customFieldsHash", "boxTypeId"})
public interface Box<P extends Proposition>
    extends sparkz.core.transaction.box.Box<P>
{
    @JsonProperty("value")
    @Override
    long value();

    @JsonProperty("proposition")
    @Override
    P proposition();

    @JsonProperty("id")
    @Override
    byte[] id();

    @JsonProperty("nonce")
    long nonce();

    byte[] customFieldsHash();

    @Override
    default byte[] bytes() {
        return serializer().toBytes(this);
    }

    @Override
    BoxSerializer serializer();

    byte boxTypeId();

    @JsonProperty("typeName")
    String typeName();

    @JsonProperty("isCustom")
    Boolean isCustom();
}