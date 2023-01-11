package com.horizen.sc2sc;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.Proposition;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public interface CrossChainMessage {

    CrossChainProtocolVersion getProtocolVersion(); //version of the protocol for future extensions
    int getMessageType();
    byte[] getSenderSidechain();
    byte[] getSender(); //sender proposition, we keep it generic because we could have different formats depending on the sidechain
    byte[] getReceiverSidechain();
    byte[] getReceiver(); //receiver proposition, we keep it generic because we could have different formats depending on the sidechain
    byte[] getPayload();
}
