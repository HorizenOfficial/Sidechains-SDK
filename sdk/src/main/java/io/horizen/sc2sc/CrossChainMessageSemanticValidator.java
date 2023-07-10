package io.horizen.sc2sc;

import io.horizen.account.proposition.AddressProposition;
import io.horizen.account.sc2sc.AccountCrossChainMessage;
import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.utils.Constants;

public final class CrossChainMessageSemanticValidator {
    public final static String MESSAGE_TYPE_ERROR_MESSAGE = "CrossChain message type cannot be negative";
    public final static String SENDER_SIDECHAIN_ID_ERROR_MESSAGE = "Sender sidechain id must be 32 bytes long";
    public final static String RECEIVER_SIDECHAIN_ID_ERROR_MESSAGE = "Receiver sidechain id must be 32 bytes long";
    public final static String SENDER_ADDRESS_ERROR_MESSAGE = "Sender address length is not correct";
    public final static String RECEIVER_ADDRESS_ERROR_MESSAGE = "Receiver address length is not correct";
    public final static String PAYLOAD_ERROR_MESSAGE = "Payload hash must be 32 bytes long";

    public void validateMessage(CrossChainMessage msg) {
        validateMsgType(msg.getMessageType());
        validateSidechainId(msg.getSenderSidechain(), SENDER_SIDECHAIN_ID_ERROR_MESSAGE);
        validateSidechainId(msg.getReceiverSidechain(), RECEIVER_SIDECHAIN_ID_ERROR_MESSAGE);
        validateAddress(msg.getSender(), SENDER_ADDRESS_ERROR_MESSAGE);
        validateAddress(msg.getReceiver(), RECEIVER_ADDRESS_ERROR_MESSAGE);
        validatePayload(msg.getPayload());
    }

    public void validateMessage(AccountCrossChainMessage accMsg) {
        validateMsgType(accMsg.messageType());
        validateSidechainId(accMsg.receiverSidechain(), RECEIVER_SIDECHAIN_ID_ERROR_MESSAGE);
        validateAddress(accMsg.sender(), SENDER_ADDRESS_ERROR_MESSAGE);
        validateAddress(accMsg.receiver(), RECEIVER_ADDRESS_ERROR_MESSAGE);
    }

    private void validateMsgType(int msgType) {
        if (msgType < 0) {
            throw new IllegalArgumentException(MESSAGE_TYPE_ERROR_MESSAGE);
        }
    }

    private void validateSidechainId(byte[] sidechainId, String exceptionMsg) {
        if (sidechainId.length != Constants.SIDECHAIN_ID_SIZE()) {
            throw new IllegalArgumentException(exceptionMsg);
        }
    }

    private void validateAddress(byte[] address, String exceptionMsg) {
        if (address.length != PublicKey25519Proposition.getLength() && address.length != AddressProposition.LENGTH) {
            throw new IllegalArgumentException(exceptionMsg);
        }
    }

    private void validatePayload(byte[] payload) {
        if (payload.length != Constants.Sc2Sc$.MODULE$.PAYLOAD()) {
            throw new IllegalArgumentException(PAYLOAD_ERROR_MESSAGE);
        }
    }
}
