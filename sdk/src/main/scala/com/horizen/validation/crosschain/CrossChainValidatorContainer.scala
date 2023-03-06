package com.horizen.validation.crosschain

import com.horizen.sc2sc.Sc2ScConfigurator
import com.horizen.validation.crosschain.CrossChainValidatorKey.{CrossChainValidatorKey, ReceiverValidator, SenderValidator}
import com.horizen.validation.crosschain.receiver.{AbstractCrossChainRedeemMessageValidator, CrossChainRedeemMessageBodyToValidate}
import com.horizen.validation.crosschain.sender.{AbstractCrossChainMessageValidator, CrossChainMessageBodyToValidate}

import scala.util.{Failure, Success, Try}

class CrossChainValidatorContainer(
                                    validators: Map[CrossChainValidatorKey, CrossChainValidator[_]],
                                    sc2ScConfigurator: Sc2ScConfigurator
                                  ) {
  def validateSenderCrossChain(objectToValidate: CrossChainMessageBodyToValidate): Unit =
    if (sc2ScConfigurator.canSendMessages) {
      Try(validators(SenderValidator)) match {
        case Success(validator) =>
          validator.asInstanceOf[AbstractCrossChainMessageValidator].validate(objectToValidate)
        case Failure(exception) => throw new IllegalArgumentException("No validator for cross chain message", exception)
      }
    }

  def validateReceivingCrossChain(objectToValidate: CrossChainRedeemMessageBodyToValidate): Unit =
    if (sc2ScConfigurator.canReceiveMessages) {
      Try(validators(ReceiverValidator)) match {
        case Success(validator) =>
          validator.asInstanceOf[AbstractCrossChainRedeemMessageValidator].validate(objectToValidate)
        case _ => throw new IllegalArgumentException("No validator for cross chain redeem message")
      }
    }
}

object CrossChainValidatorKey extends Enumeration {
  type CrossChainValidatorKey = Value
  val SenderValidator, ReceiverValidator = Value
}