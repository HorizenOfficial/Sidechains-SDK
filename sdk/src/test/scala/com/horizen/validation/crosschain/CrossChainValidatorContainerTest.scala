package com.horizen.validation.crosschain;

import com.horizen.sc2sc.Sc2ScConfigurator
import com.horizen.validation.crosschain.CrossChainValidatorKey.CrossChainValidatorKey
import com.horizen.validation.crosschain.receiver.CrossChainRedeemMessageBodyToValidate
import com.horizen.validation.crosschain.sender.CrossChainMessageBodyToValidate
import org.junit.Assert.{assertEquals, fail}
import org.junit.Test
import org.mockito.Mockito.{never, spy, times, verify}
import org.scalatest.Assertions.intercept
import org.scalatestplus.mockito.MockitoSugar.mock;

class CrossChainValidatorContainerTest {
    @Test
    def ifCannotSendMessages_validatorDoesNothing(): Unit = {
        // Arrange
        val sc2ScConfigurator = Sc2ScConfigurator(canSendMessages = false, canReceiveMessages = false)
        val sc2ScConfiguratorSpy = spy(sc2ScConfigurator)
        val validatorsMap = mock[Map[CrossChainValidatorKey, CrossChainValidator[_]]]
        val senderObjectToValidate = mock[CrossChainMessageBodyToValidate]
        val validatorContainer = new CrossChainValidatorContainer(validatorsMap, sc2ScConfiguratorSpy)

        // Act & Assert
        try {
            validatorContainer.validateSenderCrossChain(senderObjectToValidate)
        } catch {
            case _: Throwable => fail("Unexpected exception")
        }

        // Assert
        verify(sc2ScConfiguratorSpy, times(1)).canSendMessages
        verify(sc2ScConfiguratorSpy, never()).canReceiveMessages
    }

    @Test
    def ifCannotReceiveMessages_validatorDoesNothing(): Unit = {
        // Arrange
        val sc2ScConfigurator = Sc2ScConfigurator(canSendMessages = false, canReceiveMessages = false)
        val sc2ScConfiguratorSpy = spy(sc2ScConfigurator)
        val validatorsMap = mock[Map[CrossChainValidatorKey, CrossChainValidator[_]]]
        val receiverObjectToValidate = mock[CrossChainRedeemMessageBodyToValidate]
        val validatorContainer = new CrossChainValidatorContainer(validatorsMap, sc2ScConfiguratorSpy)

        // Act
        try {
            validatorContainer.validateReceivingCrossChain(receiverObjectToValidate)
        } catch {
            case _: Throwable => fail("Unexpected exception")
        }

        // Assert
        verify(sc2ScConfiguratorSpy, never()).canSendMessages
        verify(sc2ScConfiguratorSpy, times(1)).canReceiveMessages
    }

    @Test
    def ifCanSendMessageAndItsValidatorIsMissing_throwAnIllegalArgumentException(): Unit = {
        // Arrange
        val sc2ScConfigurator = Sc2ScConfigurator(canSendMessages = true, canReceiveMessages = true)
        val senderObjectToValidate = mock[CrossChainMessageBodyToValidate]
        val validatorContainer = new CrossChainValidatorContainer(Map.empty, sc2ScConfigurator)

        // Act
        val exception = intercept[IllegalArgumentException] {
            validatorContainer.validateSenderCrossChain(senderObjectToValidate)
        }

        // Assert
        val expectedMessage = "No validator for cross chain message"
        assertEquals(exception.getMessage, expectedMessage)
    }

    @Test
    def ifCanReceiveMessageAndItsValidatorIsMissing_throwAnIllegalArgumentException(): Unit = {
        // Arrange
        val sc2ScConfigurator = Sc2ScConfigurator(canSendMessages = true, canReceiveMessages = true)
        val receiverObjectToValidate = mock[CrossChainRedeemMessageBodyToValidate]
        val validatorContainer = new CrossChainValidatorContainer(Map.empty, sc2ScConfigurator)

        // Act
        val exception = intercept[IllegalArgumentException] {
            validatorContainer.validateReceivingCrossChain(receiverObjectToValidate)
        }

        // Assert
        val expectedMessage = "No validator for cross chain redeem message"
        assertEquals(exception.getMessage, expectedMessage)
    }
}