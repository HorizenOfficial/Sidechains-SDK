package io.horizen.sc2sc;

import io.horizen.utils.BytesUtils;
import org.junit.Test;
import scala.Array;

import java.nio.charset.StandardCharsets;
import java.util.Random;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrossChainMessageSemanticValidatorTest {
    @Test
    public void whenMessageTypeIsNotCorrect_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(-1);

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act
        Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

        // Assert
        String expectedMsg = CrossChainMessageSemanticValidator.MESSAGE_TYPE_ERROR_MESSAGE;
        assertEquals(expectedMsg, exception.getMessage());
    }

    @Test
    public void whenSenderSidechainIdIsNotCorrectSize_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(1);
        when(ccMsg.getSenderSidechain())
                .thenReturn(Array.emptyByteArray())
                .thenReturn("tooShort".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooLongSidechainIdtooLongSidechainIdtooLongSidechainIdtooLongSidechainId".getBytes(StandardCharsets.UTF_8));

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = CrossChainMessageSemanticValidator.SENDER_SIDECHAIN_ID_ERROR_MESSAGE;
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenReceiverSidechainIdIsNotCorrectSize_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(1);
        when(ccMsg.getSenderSidechain()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getReceiverSidechain())
                .thenReturn(Array.emptyByteArray())
                .thenReturn("tooShort".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooLongSidechainIdtooLongSidechainIdtooLongSidechainIdtooLongSidechainId".getBytes(StandardCharsets.UTF_8));

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = CrossChainMessageSemanticValidator.RECEIVER_SIDECHAIN_ID_ERROR_MESSAGE;
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenSenderAddressIsNotCorrectSize_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(1);
        when(ccMsg.getSenderSidechain()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString("b20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be23220fe4de48a0f"));
        when(ccMsg.getSender())
                .thenReturn(Array.emptyByteArray())
                .thenReturn("tooLongSidechainIdtooLongSidechainIdtooLongSidechainIdtooLongSidechainId".getBytes(StandardCharsets.UTF_8));

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = CrossChainMessageSemanticValidator.SENDER_ADDRESS_ERROR_MESSAGE;
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenReceiverAddressIsNotCorrectSize_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(1);
        when(ccMsg.getSenderSidechain()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString("b20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be23220fe4de48a0f"));
        when(ccMsg.getSender()).thenReturn(BytesUtils.fromHexString("20fe4de48a0fb20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be232"));
        when(ccMsg.getReceiver())
                .thenReturn(Array.emptyByteArray())
                .thenReturn("tooLongSidechainIdtooLongSidechainIdtooLongSidechainIdtooLongSidechainId".getBytes(StandardCharsets.UTF_8));

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = CrossChainMessageSemanticValidator.RECEIVER_ADDRESS_ERROR_MESSAGE;
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenPayloadIsNotCorrectSize_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(1);
        when(ccMsg.getSenderSidechain()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString("b20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be23220fe4de48a0f"));
        when(ccMsg.getSender()).thenReturn(BytesUtils.fromHexString("20fe4de48a0fb20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be232"));
        when(ccMsg.getReceiver()).thenReturn(BytesUtils.fromHexString("232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02"));
        when(ccMsg.getPayload())
                .thenReturn(Array.emptyByteArray())
                .thenReturn(getRandomBytes(10001));

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act
        for (int i = 0; i < 2; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = CrossChainMessageSemanticValidator.PAYLOAD_ERROR_MESSAGE;
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenAllCCMsgParamsAreSyntacticallyCorrect_nothingIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(1);
        when(ccMsg.getSenderSidechain()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString("b20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be23220fe4de48a0f"));
        when(ccMsg.getSender()).thenReturn(BytesUtils.fromHexString("20fe4de48a0fb20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be232"));
        when(ccMsg.getReceiver()).thenReturn(BytesUtils.fromHexString("232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02"));
        when(ccMsg.getPayload()).thenReturn(BytesUtils.fromHexString("232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02"));

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act & Assert
        try {
            validator.validateMessage(ccMsg);
        } catch (Exception e) {
            fail("Validator not expected to throw exception");
        }
    }

    @Test
    public void whenMessageHashMixedAddresses_nothingIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(1);
        when(ccMsg.getSenderSidechain()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getReceiverSidechain()).thenReturn(BytesUtils.fromHexString("b20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be23220fe4de48a0f"));
        when(ccMsg.getSender()).thenReturn(BytesUtils.fromHexString("20fe4de48a0fb20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be232"));
        when(ccMsg.getReceiver()).thenReturn(BytesUtils.fromHexString("232eb60e9bf07702d02bf0a84ed4ef02232eb60e"));
        when(ccMsg.getPayload()).thenReturn(BytesUtils.fromHexString("232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02"));

        CrossChainMessageSemanticValidator validator = new CrossChainMessageSemanticValidator();

        // Act & Assert
        try {
            validator.validateMessage(ccMsg);
        } catch (Exception e) {
            fail("Validator not expected to throw exception");
        }
    }

    public byte[] getRandomBytes(int length) {
        byte[] val = new byte[length];
        new Random().nextBytes(val);
        return val;
    }
}