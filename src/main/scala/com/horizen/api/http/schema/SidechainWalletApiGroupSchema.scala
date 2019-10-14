package com.horizen.api.http.schema

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.box.Box
import com.horizen.proposition.Proposition
import com.horizen.serialization.Views

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

object WalletRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  case class ReqAllBoxesPost(boxTypeClass: Option[String], excludeBoxIds: Option[Seq[String]])

  @JsonView(Array(classOf[Views.Default]))
  case class RespAllBoxesPost(boxes: List[Box[Proposition]])

  @JsonView(Array(classOf[Views.Default]))
  private[api] case class ReqBalancePost(boxType: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  case class RespBalancePost(balance: Long)

  @JsonView(Array(classOf[Views.Default]))
  case class RespCreateSecretPost(proposition: Proposition)

  @JsonView(Array(classOf[Views.Default]))
  case class ReqAllPropositionsPost(proptype: Option[String])

  @JsonView(Array(classOf[Views.Default]))
  case class RespAllPublicKeysPost(propositions: Seq[Proposition])

}