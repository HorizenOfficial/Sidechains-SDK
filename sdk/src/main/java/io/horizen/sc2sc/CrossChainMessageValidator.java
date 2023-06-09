package io.horizen.sc2sc;

import io.horizen.account.sc2sc.AccountCrossChainMessage;
import io.horizen.utils.Constants;

public final class CrossChainMessageValidator {
    private final static String SENDER_SIDECHAIN_ID_ERROR_MESSAGE = "Sender sidechain id must be 32 bytes long";
    private final static String RECEIVER_SIDECHAIN_ID_ERROR_MESSAGE = "Receiver sidechain id must be 32 bytes long";
    private final static String SENDER_ADDRESS_ERROR_MESSAGE = "Sender address length is not correct";
    private final static String RECEIVER_ADDRESS_ERROR_MESSAGE = "Receiver address length is not correct";

    public void validateMessage(CrossChainMessage msg) {
        validateMsgType(msg.getMessageType());
        validateSidechainId(msg.getSenderSidechain(), SENDER_SIDECHAIN_ID_ERROR_MESSAGE);
        validateSidechainId(msg.getReceiverSidechain(), RECEIVER_SIDECHAIN_ID_ERROR_MESSAGE);
        validateAddress(msg.getSender(), SENDER_ADDRESS_ERROR_MESSAGE);
        validateAddress(msg.getReceiver(), RECEIVER_ADDRESS_ERROR_MESSAGE);
        validatePayloadHash(msg.getPayloadHash());
    }

    public void validateMessage(AccountCrossChainMessage accMsg) {
        validateMsgType(accMsg.messageType());
        validateSidechainId(accMsg.receiverSidechain(), RECEIVER_SIDECHAIN_ID_ERROR_MESSAGE);
        validateAddress(accMsg.sender(), SENDER_ADDRESS_ERROR_MESSAGE);
        validateAddress(accMsg.receiver(), RECEIVER_ADDRESS_ERROR_MESSAGE);
    }

    private void validateMsgType(int msgType) {
        if (msgType < 0) {
            throw new IllegalArgumentException("CrossChain message type cannot be negative");
        }
    }

    private void validateSidechainId(byte[] sidechainId, String exceptionMsg) {
        if (sidechainId.length != Constants.SIDECHAIN_ID_SIZE()) {
            throw new IllegalArgumentException(exceptionMsg);
        }
    }

    private void validateAddress(byte[] address, String exceptionMsg) {
        if (address.length != Constants.SIDECHAIN_ADDRESS_SIZE() && address.length != Constants.ABI_ADDRESS_SIZE()) {
            throw new IllegalArgumentException(exceptionMsg);
        }
    }

    private void validatePayloadHash(byte[] payload) {
        if (payload.length != Constants.Sc2Sc$.MODULE$.PAYLOAD_HASH()) {
            throw new IllegalArgumentException("Payload hash must be 32 bytes long");
        }
    }
}
