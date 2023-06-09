package io.horizen.sc2sc;

import io.horizen.utils.BytesUtils;
import org.junit.Test;
import scala.Array;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrossChainMessageValidatorTest {
    @Test
    public void whenMessageTypeIsNotCorrect_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainMessage ccMsg = mock(CrossChainMessage.class);
        when(ccMsg.getMessageType()).thenReturn(-1);

        CrossChainMessageValidator validator = new CrossChainMessageValidator();

        // Act
        Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

        // Assert
        String expectedMsg = "CrossChain message type cannot be negative";
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

        CrossChainMessageValidator validator = new CrossChainMessageValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Sender sidechain id must be 32 bytes long";
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

        CrossChainMessageValidator validator = new CrossChainMessageValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Receiver sidechain id must be 32 bytes long";
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

        CrossChainMessageValidator validator = new CrossChainMessageValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Sender address length is not correct";
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

        CrossChainMessageValidator validator = new CrossChainMessageValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Receiver address length is not correct";
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

        CrossChainMessageValidator validator = new CrossChainMessageValidator();

        // Act & Assert
        try {
            validator.validateMessage(ccMsg);
        } catch (Exception e) {
            fail("Validator not expected to throw exception");
        }
    }
}