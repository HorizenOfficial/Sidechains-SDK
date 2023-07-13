package io.horizen.utxo.box;

import io.horizen.proposition.PublicKey25519Proposition;
import io.horizen.sc2sc.CrossChainProtocolVersion;
import io.horizen.utils.Ed25519;
import io.horizen.utils.Pair;
import io.horizen.utxo.fixtures.BoxFixtureClass;
import org.junit.Before;
import org.junit.Test;

import java.util.Random;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotEquals;

public class CrossChainMessageBoxTest extends BoxFixtureClass {

    PublicKey25519Proposition proposition;
    CrossChainProtocolVersion protocolVersion;
    int messageTYpe = 1;
    long nonce;
    long value;
    String payload;
    byte[] sidechainId;
    byte[] receivingSidechain;
    byte[] receivingSidechain2;

    @Before
    public void setUp() {
        byte[] anotherSeed = "testseed".getBytes();
        Pair<byte[], byte[]> keyPair = Ed25519.createKeyPair(anotherSeed);
        proposition = new PublicKey25519Proposition(keyPair.getValue());
        protocolVersion = CrossChainProtocolVersion.VERSION_1;
        nonce = 12345;
        value = 0;
        payload = "testPayload";
        sidechainId = new byte[32];
        new Random().nextBytes(sidechainId);
        receivingSidechain = new byte[16];
        new Random().nextBytes(receivingSidechain);
        receivingSidechain2 = new byte[16];
        new Random().nextBytes(receivingSidechain2);
    }

    @Test
    public void creationTest() {
        CrossChainMessageBox box = getCrossMessageBox(proposition, protocolVersion,
                messageTYpe, sidechainId, receivingSidechain, payload.getBytes(), nonce);
        assertEquals("CrossChainMessageBox creation: proposition is wrong", proposition, box.proposition());
        assertEquals("CrossChainMessageBox creation: nonce is wrong", box.nonce(), nonce);
        assertEquals("CrossChainMessageBox creation: value is wrong", box.value(), value);
    }

    @Test
    public void serializationTest() {
        CrossChainMessageBox box = getCrossMessageBox(proposition, protocolVersion,
                messageTYpe, sidechainId, receivingSidechain, payload.getBytes(), nonce);
        byte[] bytes = box.serializer().toBytes(box);

        CrossChainMessageBox box2 = (CrossChainMessageBox) box.serializer().parseBytesTry(bytes).get();
        assertEquals("Boxes expected to be equal", box, box2);

        assertEquals("CrossChainMessageBox deserialize error: proposition is wrong", proposition, box2.proposition());
        assertEquals("CrossChainMessageBox deserialize error: nonce is wrong", box2.nonce(), nonce);
        assertEquals("CrossChainMessageBox deserialize error: value is wrong", box2.value(), value);
    }

    @Test
    public void differentIdTest() {
        CrossChainMessageBox box = getCrossMessageBox(proposition, protocolVersion,
                messageTYpe, sidechainId, receivingSidechain, payload.getBytes(), nonce);
        CrossChainMessageBox box2 = getCrossMessageBox(proposition, protocolVersion,
                messageTYpe, sidechainId, receivingSidechain2, payload.getBytes(), nonce);
        assertNotEquals("CrossChainMessageBox id should change with different input data", box.id(), box2.id());
    }
}
