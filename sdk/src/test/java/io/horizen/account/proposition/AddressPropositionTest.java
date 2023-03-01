package io.horizen.account.proposition;

import io.horizen.account.secret.PrivateKeySecp256k1;
import io.horizen.account.secret.PrivateKeySecp256k1Creator;
import io.horizen.account.secret.PrivateKeySecp256k1Serializer;
import io.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;

public class AddressPropositionTest {
    PrivateKeySecp256k1 privateKey;
    AddressProposition addressProposition;

    @Before
    public void BeforeEachTest() {
        // Deserialize private key and create proposition
        // Data taken from trusted external resource
        String privateKeyHex = "227dbb8586117d55284e26620bc76534dfbd2394be34cf4a09cb775d593b6f2b";
        privateKey = PrivateKeySecp256k1Serializer.getSerializer().parseBytes(BytesUtils.fromHexString(privateKeyHex));
        addressProposition = privateKey.publicImage();
    }

    @Test
    public void addressPropositionTest() {
        // Test 1: Returns hash code correctly
        assertEquals("Hashcode is different", addressProposition.address().hashCode(), addressProposition.hashCode());

        // Test 2: Returns true as the object is the same
        assertEquals(addressProposition, addressProposition);

        // Test 3: Returns false as the object is null
        assertNotEquals(addressProposition, null);

        // Test 4: Returns false as the object does not contain the same address
        AddressProposition anotherAddressProposition = PrivateKeySecp256k1Creator.getInstance().generateSecret("addressproptest".getBytes(StandardCharsets.UTF_8)).publicImage();
        assertNotEquals(addressProposition, anotherAddressProposition);

        // Test 5: Returns true as the object contains the same address
        assertEquals(addressProposition, privateKey.publicImage());

        // Test 6: IllegalArgumentException while creating new AddressProposition expected
        assertThrows(IllegalArgumentException.class, () -> new AddressProposition(BytesUtils.fromHexString("12345134")));

        // Test 7: Returns address correctly
        assertEquals("0xe16c1623c1aa7d919cd2241d8b36d9e79c1be2a2", addressProposition.address().toString());

        // Test 8: Returns checksum correctly
        assertEquals("0xe16C1623c1AA7D919cd2241d8b36d9E79C1Be2A2", addressProposition.checksumAddress());
    }
}
