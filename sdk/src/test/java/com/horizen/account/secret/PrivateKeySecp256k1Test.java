package com.horizen.account.secret;

import com.horizen.account.proposition.AddressProposition;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;

import static com.horizen.secret.SecretsIdsEnum.PrivateKeySecp256k1SecretId;
import static org.junit.Assert.*;

public class PrivateKeySecp256k1Test {
    String privateKeyHex;
    PrivateKeySecp256k1 privateKeySecp256k1;
    AddressProposition addressProposition;

    @Before
    public void BeforeEachTest() {
        // Deserialize private key and create proposition
        // Data taken from trusted external resource
        privateKeyHex = "227dbb8586117d55284e26620bc76534dfbd2394be34cf4a09cb775d593b6f2b";
        privateKeySecp256k1 = PrivateKeySecp256k1Serializer.getSerializer().parseBytes(BytesUtils.fromHexString(privateKeyHex));
        addressProposition = privateKeySecp256k1.publicImage();
    }

    @Test
    public void privateKeySecp256k1Test() {
        // Test 1: Returns correct address proposition from private key
        assertEquals("e16c1623c1aa7d919cd2241d8b36d9e79c1be2a2", BytesUtils.toHexString(privateKeySecp256k1.publicImage().address()));

        // Test 2: Must return true as it owns here created address proposition
        assertTrue(privateKeySecp256k1.owns(addressProposition));

        // Test 3: Returns hash code correctly
        assertEquals(9083292, privateKeySecp256k1.hashCode());

        // Test 4: Returns true as the object is the same
        assertEquals(privateKeySecp256k1, privateKeySecp256k1);

        // Test 5: Returns false as the object is null
        assertNotEquals(privateKeySecp256k1, null);

        // Test 6: Returns false as the object does not contain the same private key
        PrivateKeySecp256k1 anotherPrivateKeySecp256k1 = PrivateKeySecp256k1Creator.getInstance().generateSecret("anotherkey".getBytes(StandardCharsets.UTF_8));
        assertNotEquals(privateKeySecp256k1, anotherPrivateKeySecp256k1);

        // Test 7: Returns true as the object contains the same private key
        PrivateKeySecp256k1 samePrivateKeySecp256k1 = PrivateKeySecp256k1Serializer.getSerializer().parseBytes(BytesUtils.fromHexString(privateKeyHex));
        assertEquals(privateKeySecp256k1, samePrivateKeySecp256k1);

        // Test 8: Returns PrivateKeySecp256k1SecretId correctly
        assertEquals(PrivateKeySecp256k1SecretId.id(), privateKeySecp256k1.secretTypeId());

        // Test 9: Illegal Argument Exception while creating new PrivateKeySecp256k1 expected
        assertThrows(IllegalArgumentException.class, () -> new PrivateKeySecp256k1(new BigInteger("12345678").toByteArray()));
    }
}
