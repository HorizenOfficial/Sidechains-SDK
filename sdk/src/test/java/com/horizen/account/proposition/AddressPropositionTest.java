package com.horizen.account.proposition;

import com.horizen.account.proof.SignatureSecp256k1;
import com.horizen.account.utils.Account;
import com.horizen.account.utils.Secp256k1;
import com.horizen.evm.utils.Address;
import com.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import org.web3j.crypto.ECKeyPair;
import org.web3j.crypto.Keys;
import org.web3j.crypto.Sign;
import org.web3j.utils.Numeric;

import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.InvalidAlgorithmParameterException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.util.Arrays;

import static org.junit.Assert.*;
import static org.junit.Assert.assertFalse;

public class AddressPropositionTest {
    AddressProposition addressProposition;
    byte[] address;
    ECKeyPair pair;
    byte[] privateKey;

    @Before
    public void BeforeEachTest() throws InvalidAlgorithmParameterException, NoSuchAlgorithmException, NoSuchProviderException {
        // Create a key pair and create proposition
        privateKey = Arrays.copyOf(new BigInteger("4164020894437499837987386266872312011773540175446618547506457344").toByteArray(), Secp256k1.PRIVATE_KEY_SIZE);
        pair = ECKeyPair.create(privateKey);
        address = Arrays.copyOf(BytesUtils.fromHexString(Keys.getAddress(pair)), Account.ADDRESS_SIZE);
        addressProposition = new AddressProposition(address);
    }

    @Test
    public void addressPropositionTest() {
        // Test 1: Returns hash code correctly
        assertEquals(-355916771, addressProposition.hashCode());

        // Test 2: Returns true as the object is the same
        assertTrue(addressProposition.equals(addressProposition));

        // Test 3: Returns false as the object is null
        assertFalse(addressProposition.equals(null));

        // Test 4: Returns false as the object is not an instance of AddressProposition
        String payload = "This is string to sign";
        var message = payload.getBytes(StandardCharsets.UTF_8);
        var signatureData = Sign.signMessage(message, pair, true);
        assertFalse(addressProposition.equals(new SignatureSecp256k1(signatureData.getV(), signatureData.getR(), signatureData.getS())));

        // Test 5: Returns false as the object does not contain the same address
        AddressProposition anotherAddressProposition = new AddressProposition(Arrays.copyOf(new BigInteger("1234567890437499837987386266872312011773540175446618547506457344").toByteArray(), Address.LENGTH));
        assertFalse(addressProposition.equals(anotherAddressProposition));

        // Test 6: Returns true as the object contains the same address
        anotherAddressProposition = new AddressProposition(address);
        assertTrue(addressProposition.equals(anotherAddressProposition));

        // Test 7: Nullpointer Exception while creating new AddressProposition expected
        assertThrows(NullPointerException.class, () -> new AddressProposition(null));

        // Test 8: Nullpointer Exception while creating new AddressProposition expected
        assertThrows(IllegalArgumentException.class, () -> new AddressProposition(Numeric.hexStringToByteArray("12345134")));

        // Test 9: Returns checksum correctly
        assertEquals("0xd51C15351339F694Fe234E12A036746721afB506", addressProposition.checksumAddress());
    }
}
