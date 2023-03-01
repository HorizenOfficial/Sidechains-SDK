package io.horizen.account.utils;

import io.horizen.account.proposition.AddressProposition;
import io.horizen.utils.BytesUtils;
import org.junit.Before;
import org.junit.Test;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.FileReader;
import java.io.FileWriter;


import static org.junit.Assert.*;

public class MainchainTxCrosschainOutputAddressUtilTest {

    AddressProposition addressProposition;

    @Before
    public void setUp() {
        byte[] mcAddress = BytesUtils.fromHexString("aaaa22334455667700112233445566770011223344556677001122334455bbbb");
        addressProposition = new AddressProposition(
                MainchainTxCrosschainOutputAddressUtil.getAccountAddress(mcAddress));
        // Set `true` and run if you want to update regression data.
        if (false) {
            try {

                BufferedWriter out = new BufferedWriter(new FileWriter(
                        "src/test/resources/mc_ccout_evm_address_hex"));
                out.write(addressProposition.address().toStringNoPrefix());
                out.close();
            } catch (Throwable e) {
                fail(e.toString());
            }
        }
    }

    @Test
    public void regressionTest() {
        byte[] bytes;

        try {
            ClassLoader classLoader = getClass().getClassLoader();
            FileReader file = new FileReader(classLoader.getResource("mc_ccout_evm_address_hex").getFile());
            bytes = BytesUtils.fromHexString(new BufferedReader(file).readLine());
        }
        catch (Exception e) {
            fail(e.toString());
            return;
        }

        AddressProposition parsedAddressProposition = new AddressProposition(bytes);
        assertEquals("AddressProposition is different to the origin.", addressProposition, parsedAddressProposition);
    }

    @Test
    public void shortArrayFails() {
        byte[] mcAddress = BytesUtils.fromHexString("aaaa22334455667700112233445566770011223344556677001122334455bb");
        try {
            addressProposition = new AddressProposition(
                    MainchainTxCrosschainOutputAddressUtil.getAccountAddress(mcAddress));
            fail("Expected a failure");
        } catch (Throwable t) {
            // expected
            System.out.println("As expected: " + t);
        }
    }

    @Test
    public void longArrayFails() {
        byte[] mcAddress = BytesUtils.fromHexString("aaaa22334455667700112233445566770011223344556677001122334455bbbbcc");
        try {
            addressProposition = new AddressProposition(
                    MainchainTxCrosschainOutputAddressUtil.getAccountAddress(mcAddress));
            fail("Expected a failure");
        } catch (Throwable t) {
            // expected
            System.out.println("As expected: " + t);
        }
    }


    @Test
    public void compatibleArrayIsOk() {
        // account address is 20 out of 32 byes, therefore we can have an arbitrary padding
        byte[] mcAddress = BytesUtils.fromHexString("aaaa223344556677001122334455667700112233000000000000000000000000");

        var compAddressProposition = new AddressProposition(
                MainchainTxCrosschainOutputAddressUtil.getAccountAddress(mcAddress));
        assertEquals(compAddressProposition, addressProposition);
    }


    @Test
    public void differentArrayFails() {
        byte[] mcAddress = BytesUtils.fromHexString("bbaa223344556677001122334455667700112233000000000000000000000000");

        var compAddressProposition = new AddressProposition(
                MainchainTxCrosschainOutputAddressUtil.getAccountAddress(mcAddress));
        assertNotEquals(compAddressProposition, addressProposition);
    }
}
