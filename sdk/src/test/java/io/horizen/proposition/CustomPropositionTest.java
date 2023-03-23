package io.horizen.proposition;

import io.horizen.customtypes.CustomPrivateKey;
import io.horizen.secret.PrivateKey25519;
import io.horizen.secret.Secret;
import io.horizen.utils.Ed25519;
import io.horizen.utils.Pair;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import static org.junit.Assert.*;

public class CustomPropositionTest {

    @Test
    public void testProvable(){
        CustomPrivateKey customPrivate1 = generateCustomKey();

        Pair<byte[], byte[]> keyPair2= Ed25519.createKeyPair("abc".getBytes(StandardCharsets.UTF_8));
        PrivateKey25519 privateKey2 =  new PrivateKey25519(keyPair2.getKey(), keyPair2.getValue());

        List<Secret> secrets = new ArrayList();
        secrets.add(customPrivate1);
        secrets.add(privateKey2);

        ProvableCheckResult result = customPrivate1.publicImage().canBeProvedBy(secrets);
        assertTrue(result.canBeProved());
        assertEquals(result.secretsNeeded().size(), 1);
        assertEquals(result.secretsNeeded().get(0), customPrivate1);

        //negative test
        CustomPrivateKey customPrivate2 = generateCustomKey();
        result = customPrivate2.publicImage().canBeProvedBy(secrets);
        assertFalse(result.canBeProved());
        assertEquals(result.secretsNeeded().size(), 0);
    }

    private CustomPrivateKey generateCustomKey(){
        byte[] pkeyBytes = new byte[CustomPrivateKey.PRIVATE_KEY_LENGTH];
        new Random().nextBytes(pkeyBytes);
        byte[] pubBytes = new byte[CustomPrivateKey.PUBLIC_KEY_LENGTH];
        new Random().nextBytes(pubBytes);
        return new CustomPrivateKey(pkeyBytes,pubBytes);
    }

}
