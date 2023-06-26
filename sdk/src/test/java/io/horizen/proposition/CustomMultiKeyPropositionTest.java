package io.horizen.proposition;

import io.horizen.customtypes.CustomMultiKeyProposition;
import io.horizen.secret.PrivateKey25519;
import io.horizen.secret.Secret;
import io.horizen.utils.Ed25519;
import io.horizen.utils.Pair;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import static org.junit.Assert.*;

public class CustomMultiKeyPropositionTest {

    @Test
    public void testProvable(){
        PrivateKey25519 privateKey1 =  generatePrivKey("abc");
        PrivateKey25519 privateKey2 =  generatePrivKey("def");
        PrivateKey25519 privateKey3 =  generatePrivKey("ghi");
        PrivateKey25519 privateKey4 =  generatePrivKey("lmn");

        CustomMultiKeyProposition propToTest = new CustomMultiKeyProposition(privateKey1.publicImage().pubKeyBytes(), privateKey2.publicImage().pubKeyBytes());

        //two secrets compatible
        List<Secret> secrets = new ArrayList();
        secrets.add(privateKey1);
        secrets.add(privateKey2);
        secrets.add(privateKey3);
        ProvableCheckResult result = propToTest.canBeProvedBy(secrets);
        assertTrue(result.canBeProved());
        assertEquals(result.secretsNeeded().size(), 2);
        assertEquals(result.secretsNeeded().get(0), privateKey1);
        assertEquals(result.secretsNeeded().get(1), privateKey2);

        //Only one secret compatible, but we can anyway build the proof
        secrets = new ArrayList();
        secrets.add(privateKey1);
        secrets.add(privateKey3);
        result = propToTest.canBeProvedBy(secrets);
        assertTrue(result.canBeProved());
        assertEquals(result.secretsNeeded().size(), 1);
        assertEquals(result.secretsNeeded().get(0), privateKey1);


        //negative test
        secrets = new ArrayList();
        secrets.add(privateKey4);
        secrets.add(privateKey3);
        result = propToTest.canBeProvedBy(secrets);
        assertFalse(result.canBeProved());
        assertEquals(result.secretsNeeded().size(), 0);
    }

    private PrivateKey25519 generatePrivKey(String seed){
        Pair<byte[], byte[]> keyPair2= Ed25519.createKeyPair(seed.getBytes(StandardCharsets.UTF_8));
        return new PrivateKey25519(keyPair2.getKey(), keyPair2.getValue());
    }

}
