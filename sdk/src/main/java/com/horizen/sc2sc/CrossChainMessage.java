package com.horizen.sc2sc;

import com.fasterxml.jackson.annotation.JsonView;
import com.horizen.proposition.Proposition;
import com.horizen.serialization.Views;

@JsonView(Views.Default.class)
public interface CrossChainMessage {

    CrossChainProtocolVersion getProtocolVersion(); //version of the protocol for future extensions
    int getMessageType();
    byte[] getSenderSidechain();
    Proposition getSender();
    byte[] getReceiverSidechain();
    Proposition getReceiver();
    byte[] getPayload();
}
