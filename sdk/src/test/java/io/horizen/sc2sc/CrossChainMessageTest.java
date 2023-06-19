package io.horizen.sc2sc;

import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

public class CrossChainMessageTest {

    private CrossChainProtocolVersion version;
    private int messageType;
    private byte[] senderSidechain;
    private byte[] sender;
    private byte[] receiverSidechain;
    private byte[] receiver;
    private byte[] payload;

    private byte[] randomHash;

    @Before
    public void before(){
        version = CrossChainProtocolVersion.VERSION_1;
        messageType = 1;
        senderSidechain = getRandomBytes(32);
        sender = getRandomBytes(20);
        receiverSidechain = getRandomBytes(32);
        receiver = getRandomBytes(32);
        payload = "TestPayLoad".getBytes();

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
                payload
        );
        byte[] val = cm.bytes();
        CrossChainMessage cm2 =  CrossChainMessageSerializer.getSerializer().parseBytes(val);
        assertEquals(cm2.getProtocolVersion(), version);
        assertEquals(cm2.getMessageType(), messageType);
        assertArrayEquals(cm.getSenderSidechain(), senderSidechain);
        assertArrayEquals(cm.getReceiverSidechain(), receiverSidechain);
        assertArrayEquals(cm.getSender(), sender);
        assertArrayEquals(cm.getReceiver(), receiver);
        assertArrayEquals(cm.getPayload(), payload);
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
