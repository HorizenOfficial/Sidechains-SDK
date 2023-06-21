package io.horizen.sc2sc;

import io.horizen.utils.BytesUtils;
import org.junit.Test;

import java.nio.charset.StandardCharsets;

import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class CrossChainRedeemMessageSemanticValidatorTest {
    @Test
    public void whenCertificateDataHashIsNotCorrect_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainRedeemMessage ccMsg = mock(CrossChainRedeemMessage.class);
        when(ccMsg.getCertificateDataHash())
                .thenReturn("".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooShort".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooLongCertificateDataHashtooLongCertificateDataHashtooLongCertificateDataHash".getBytes(StandardCharsets.UTF_8));

        CrossChainRedeemMessageSemanticValidator validator = new CrossChainRedeemMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Certificate data hash must be 32 bytes long";
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenNextCertificateDataHashIsNotCorrect_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainRedeemMessage ccMsg = mock(CrossChainRedeemMessage.class);
        when(ccMsg.getCertificateDataHash()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getNextCertificateDataHash())
                .thenReturn("".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooShort".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooLongCertificateDataHashtooLongCertificateDataHashtooLongCertificateDataHash".getBytes(StandardCharsets.UTF_8));

        CrossChainRedeemMessageSemanticValidator validator = new CrossChainRedeemMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Certificate data hash must be 32 bytes long";
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenScCommitmentTreeRootIsNotCorrect_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainRedeemMessage ccMsg = mock(CrossChainRedeemMessage.class);
        when(ccMsg.getCertificateDataHash()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getNextCertificateDataHash()).thenReturn(BytesUtils.fromHexString("b20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be23220fe4de48a0f"));
        when(ccMsg.getScCommitmentTreeRoot())
                .thenReturn("".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooShort".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooLongCertificateDataHashtooLongCertificateDataHashtooLongCertificateDataHash".getBytes(StandardCharsets.UTF_8));

        CrossChainRedeemMessageSemanticValidator validator = new CrossChainRedeemMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Sidechain commitment tree root must be 32 bytes long";
            assertEquals(expectedMsg, exception.getMessage());
        }
    }

    @Test
    public void whenNextScCommitmentTreeRootIsNotCorrect_IllegalArgumentExceptionIsThrown() {
        // Arrange
        CrossChainRedeemMessage ccMsg = mock(CrossChainRedeemMessage.class);
        when(ccMsg.getCertificateDataHash()).thenReturn(BytesUtils.fromHexString("f0a84ed4ef02232eb60e9bf07702d02bf0a84ed4ef02232eb60e9bf07702d02b"));
        when(ccMsg.getNextCertificateDataHash()).thenReturn(BytesUtils.fromHexString("b20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be23220fe4de48a0f"));
        when(ccMsg.getScCommitmentTreeRoot()).thenReturn(BytesUtils.fromHexString("20fe4de48a0fb20d20770fb9e06be23220fe4de48a0fb20d20770fb9e06be232"));
        when(ccMsg.getNextScCommitmentTreeRoot())
                .thenReturn("".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooShort".getBytes(StandardCharsets.UTF_8))
                .thenReturn("tooLongCertificateDataHashtooLongCertificateDataHashtooLongCertificateDataHash".getBytes(StandardCharsets.UTF_8));

        CrossChainRedeemMessageSemanticValidator validator = new CrossChainRedeemMessageSemanticValidator();

        // Act
        for (int i = 0; i < 3; i++) {
            Exception exception = assertThrows(IllegalArgumentException.class, () -> validator.validateMessage(ccMsg));

            // Assert
            String expectedMsg = "Sidechain commitment tree root must be 32 bytes long";
            assertEquals(expectedMsg, exception.getMessage());
        }
    }
}