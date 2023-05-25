package io.horizen.sc2sc;

import io.horizen.utils.BytesUtils;
import org.junit.Test;

import java.util.List;

import static org.junit.Assert.*;

public class CrossChainMessageHashTest {
    @Test
    public void creatingACrossChainMessageHashWithWrongByteSize_throwsIllegalArgumentException() {
        // Arrange
        List<byte[]> inputList = List.of(
                "".getBytes(),
                "abcd".getBytes(),
                "TOO_LONG_BYTE_ARRAY___TOO_LONG_BYTE_ARRAY___TOO_LONG_BYTE_ARRAY___TOO_LONG_BYTE_ARRAY___".getBytes()
        );
        String expectedErrorMessage = "The CrossChain message hash must be 32 bytes long";

        // Act & Assert
        for (byte[] b : inputList) {
            IllegalArgumentException e = assertThrows(IllegalArgumentException.class, () -> new CrossChainMessageHash(b));
            assertEquals(expectedErrorMessage, e.getMessage());
        }
    }

    @Test
    public void creatingACrossChainMessageHashWithCorrectByteSize_createsCorrectlyTheObject() {
        // Arrange
        byte[] hash = BytesUtils.fromHexString("c4e21b032c7d49ccf2977d014be09788897fbd988eca41e38a37512323fede78");

        // Act
        CrossChainMessageHash ccMsgHash = new CrossChainMessageHash(hash);

        // Assert
        assertEquals(hash, ccMsgHash.getValue());
    }
}