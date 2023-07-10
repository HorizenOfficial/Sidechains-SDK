package io.horizen.sc2sc;

import io.horizen.account.proposition.AddressProposition;
import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.utils.Constants;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.*;

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
        senderSidechain = getRandomBytes(Constants.SIDECHAIN_ID_SIZE());
        sender = getRandomBytes(PublicKey25519Proposition.getLength());
        receiverSidechain = getRandomBytes(Constants.SIDECHAIN_ID_SIZE());
        receiver = getRandomBytes(AddressProposition.LENGTH);
        payload = getRandomBytes(Constants.Sc2Sc$.MODULE$.PAYLOAD());

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
    public void whenCrossChainMessageHasASyntacticallyWrongParameter_IllegalArgumentExceptionIsThrown() {
        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                -1,
                senderSidechain,
                sender,
                receiverSidechain,
                receiver,
            payload
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                getRandomBytes(5),
                sender,
                receiverSidechain,
                receiver,
            payload
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                getRandomBytes(5),
                receiverSidechain,
                receiver,
            payload
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                sender,
                getRandomBytes(5),
                receiver,
            payload
        ));

        assertThrows(IllegalArgumentException.class, () -> new CrossChainMessage(
                version,
                messageType,
                senderSidechain,
                sender,
                receiverSidechain,
                getRandomBytes(5),
            payload
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
