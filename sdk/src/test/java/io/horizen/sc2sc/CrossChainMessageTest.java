package io.horizen.sc2sc;

import io.horizen.utils.Constants;
import org.junit.Before;
import org.junit.Test;

import java.util.List;
import java.util.Random;

import static org.junit.Assert.*;

public class CrossChainMessageTest {

    private CrossChainProtocolVersion version;
    private int messageType;
    private byte[] senderSidechain;
    private byte[] sender;
    private byte[] receiverSidechain;
    private byte[] receiver;
    private byte[] payloadHash;

    private byte[] randomHash;

    @Before
    public void before(){
        version = CrossChainProtocolVersion.VERSION_1;
        messageType = 1;
        senderSidechain = getRandomBytes(Constants.SIDECHAIN_ID_SIZE());
        sender = getRandomBytes(Constants.ABI_ADDRESS_SIZE());
        receiverSidechain = getRandomBytes(Constants.SIDECHAIN_ID_SIZE());
        receiver = getRandomBytes(Constants.SIDECHAIN_ADDRESS_SIZE());
        payloadHash = getRandomBytes(Constants.Sc2Sc$.MODULE$.PAYLOAD_HASH());

        randomHash = getRandomBytes(32);
    }

    @Test
    public void serializeTest(){
        CrossChainMessage cm = new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                sender,
                receiverSidechain,
                receiver,
                payloadHash
        );
        byte[] val = cm.bytes();
        CrossChainMessage cm2 =  CrossChainMessageSerializer.getSerializer().parseBytes(val);
        assertEquals(cm2.getProtocolVersion(), version);
        assertEquals(cm2.getMessageType(), messageType);
        assertArrayEquals(cm.getSenderSidechain(), senderSidechain);
        assertArrayEquals(cm.getReceiverSidechain(), receiverSidechain);
        assertArrayEquals(cm.getSender(), sender);
        assertArrayEquals(cm.getReceiver(), receiver);
        assertArrayEquals(cm.getPayloadHash(), payloadHash);
    }

    @Test
    public void whenCrossChainMessageHasASyntacticallyWrongParameter_IllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                -1,
                senderSidechain,
                sender,
                receiverSidechain,
                receiver,
                payloadHash
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                getRandomBytes(5),
                sender,
                receiverSidechain,
                receiver,
                payloadHash
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                getRandomBytes(5),
                receiverSidechain,
                receiver,
                payloadHash
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                sender,
                getRandomBytes(5),
                receiver,
                payloadHash
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                sender,
                receiverSidechain,
                getRandomBytes(5),
                payloadHash
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                sender,
                receiverSidechain,
                receiver,
                getRandomBytes(5)
        ));
    }

    @Test
    public void serializeHashTest(){
        CrossChainMessageHash cm = new CrossChainMessageHash(randomHash);
        byte[] val = cm.bytes();
        CrossChainMessageHash cm2 =  CrossChainMessageHashSerializer.getSerializer().parseBytes(val);
        assertArrayEquals(cm.bytes(), cm2.bytes());
    }

    public byte[] getRandomBytes(int length){
        byte[] val = new byte[length];
        new Random().nextBytes(val);
        return val;
    }
}
