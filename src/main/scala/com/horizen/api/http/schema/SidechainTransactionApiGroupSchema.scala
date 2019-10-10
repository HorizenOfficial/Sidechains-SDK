package com.horizen.api.http.schema

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views
import com.horizen.transaction.Transaction

/**
  * Set of classes representing input and output format of all REST Api requests about block operations.
  *
  * Naming convection used (in camel case).
  * - For REST request: 'Req' + resource path + method
  * - For REST response: 'Resp' + resource path + method
  *
  * Example
  * We have an Api with group name 'myGroup' and two resources path 'path_1' and 'path_2'.
  * The full uri path will be:
  *
  * 1) http://host:port/myGroup/path_1
  * 2) http://host:port/myGroup/path_2
  *
  * Classes implemented will be (assumed the HTTP method used is POST for all resources):
  * 1)
  *     1.1) class ReqPath_1Post
  *     1.2) class RespPath_1Post
  * 2)
  *     2.1) class ReqPath_2Post
  *     2.2) class RespPath_2Post
  *
  * Note:
  * In case of requests/responses with the same inputs/outputs format, an unique class will be implemented, without following the above naming convection.
  */

object TransactionRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  case class ReqAllTransactionsPost(format: Option[Boolean])

  @JsonView(Array(classOf[Views.Default]))
  case class RespAllTransactionsPost(transactions: Option[List[Transaction]], transactionIds: Option[List[String]])

  @JsonView(Array(classOf[Views.Default]))
  case class ReqFindByIdPost(transactionId: String, blockHash: Option[String], transactionIndex: Option[Boolean], format: Option[Boolean])

  @JsonView(Array(classOf[Views.Default]))
  case class TransactionDTO(transaction: Option[Transaction], transactionBytes: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  case class ReqDecodeTransactionBytesPost(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  case class RespDecodeTransactionBytesPost(transaction: Transaction)

  @JsonView(Array(classOf[Views.Default]))
  case class TransactionInput(boxId: String)

  @JsonView(Array(classOf[Views.Default]))
  case class TransactionOutput(publicKey: String, value: Long)

  @JsonView(Array(classOf[Views.Default]))
  case class ReqCreateRegularTransactionPost(transactionInputs: List[TransactionInput],
                                             transactionOutputs: List[TransactionOutput],
                                             format: Option[Boolean]) {
    require(transactionInputs.nonEmpty, "Empty inputs list")
    require(transactionOutputs.nonEmpty, "Empty outputs list")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class ReqCreateRegularTransactionSimplifiedPost(transactionOutputs: List[TransactionOutput],
                                                       fee: Long,
                                                       format: Option[Boolean]) {
    require(transactionOutputs.nonEmpty, "Empty outputs list")
    require(fee >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class ReqSendCoinsToAddressPost(outputs: List[TransactionOutput], fee: Option[Long]) {
    require(outputs.nonEmpty, "Empty outputs list")
    require(fee.getOrElse(0L) >= 0, "Negative fee. Fee must be >= 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class ReqSendTransactionPost(transactionBytes: String)

  @JsonView(Array(classOf[Views.Default]))
  case class TransactionIdDTO(transactionId: String)

}