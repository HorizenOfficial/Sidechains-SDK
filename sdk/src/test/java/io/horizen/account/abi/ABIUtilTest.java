package io.horizen.account.abi;

import io.horizen.account.state.ExecutionFailedException;
import org.junit.Assert;
import org.junit.Test;

public class ABIUtilTest {
    @Test
    public void testGetArgumentsFromData() throws ExecutionFailedException {
        // Test 1: valid data
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6};
        byte[] expected = new byte[]{5, 6};
        Assert.assertArrayEquals(expected, ABIUtil.getArgumentsFromData(data));

        // too short data
        byte[] invalidData = new byte[]{1, 2};
        Assert.assertThrows("Test2: Exception expected", ExecutionFailedException.class, () -> ABIUtil.getArgumentsFromData(invalidData));
    }

    @Test
    public void testGetFunctionSignature() throws ExecutionFailedException {
        // Test 1: valid data
        byte[] data = new byte[]{1, 2, 3, 4, 5, 6};
        String expected = "01020304";
        Assert.assertEquals(expected, ABIUtil.getFunctionSignature(data));

        // too short data
        byte[] invalidData = new byte[]{1, 2};
        Assert.assertThrows("Test2: Exception expected", ExecutionFailedException.class, () -> ABIUtil.getArgumentsFromData(invalidData));
    }

    @Test
    public void testGetABIMethodId() {
        // https://docs.soliditylang.org/en/v0.8.13/abi-spec.html#examples
        String methodSig = "baz(uint32,bool)";
        String expected = "cdcd77c0";
        Assert.assertEquals(expected, ABIUtil.getABIMethodId(methodSig));
    }
}
