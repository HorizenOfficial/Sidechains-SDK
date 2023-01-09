package com.horizen.sc2sc;

public interface CrossChainMessage {

    int getMessageType();
    byte[] getSenderSidechain();
    byte[] getSenderAddress(); //we keep it generic because the format may change based on the sidechain type
    byte[] getReceiverSidechain();
    byte[] getReceiverAddress(); //we keep it generic because the format may change based on the sidechain type
    byte[] getPayload();
}
