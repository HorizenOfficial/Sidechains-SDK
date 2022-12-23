package com.horizen.sc2sc;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.serialization.Views;
import sparkz.core.serialization.BytesSerializable;

@JsonView(Views.Default.class)
public interface CrossChainMessage  extends BytesSerializable {

    CrossChainProtocolVersion getProtocolVersion(); //version of the protocol for future extensions
    int getMessageType();
    byte[] getSenderSidechain();
    byte[] getSender(); //sender proposition, we keep it generic because we could have different formats depending on the sidechain
    byte[] getReceiverSidechain();
    byte[] getReceiver(); //receiver proposition, we keep it generic because we could have different formats depending on the sidechain
    byte[] getPayload();
}
