package com.horizen.api.http.schema

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.block.SidechainBlock
import com.horizen.serialization.Views

/**
  * Set of classes representing input and output format of all REST Api requests about block operations.
  *
  * Naming convection used (in camel case).
  * - For REST request: 'Req' + resource path + method
  * - For REST response: 'Resp' + resource path + method
  *
  * Example
  *  We have an Api with group name 'myGroup' and two resources path 'path_1' and 'path_2'.
  *  The full uri path will be:
  *
  *  1) http://host:port/myGroup/path_1
  *  2) http://host:port/myGroup/path_2
  *
  *  Classes implemented will be (assumed the HTTP method used is POST for all resources):
  *  1)
  *     1.1) class ReqPath_1Post
  *     1.2) class RespPath_1Post
  *  2)
  *     2.1) class ReqPath_2Post
  *     2.2) class RespPath_2Post
  *
  * Note:
  * In case of requests/responses with the same inputs/outputs format, an unique class will be implemented, without following the above naming convection.
  */

object BlockRestScheme {

  @JsonView(Array(classOf[Views.Default]))
  case class ReqFindByIdPost(blockId: String) {
    require(blockId.length == 64, s"Invalid id $blockId. Id length must be 64")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespFindByIdPost(blockHex: String, block : SidechainBlock)

  @JsonView(Array(classOf[Views.Default]))
  case class ReqLastIdsPost(number: Int){
    require(number > 0, s"Invalid number $number. Number must be > 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespLastIdsPost(lastBlockIds: Seq[String])

  @JsonView(Array(classOf[Views.Default]))
  case class ReqFindIdByHeightPost(height: Int){
    require(height > 0, s"Invalid height $height. Height must be > 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespFindIdByHeightPost(blockId: String)

  @JsonView(Array(classOf[Views.Default]))
  case class RespBestPost(block: SidechainBlock, height : Int)

  @JsonView(Array(classOf[Views.Default]))
  case class RespTemplatePost(block: SidechainBlock, blockHex : String)

  @JsonView(Array(classOf[Views.Default]))
  case class ReqSubmitPost(blockHex: String) {
    require(blockHex.nonEmpty, s"Invalid hex data $blockHex. String must be not empty")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespSubmitPost(blockId: String)

  @JsonView(Array(classOf[Views.Default]))
  case class ReqGeneratePost(number: Int){
    require(number > 0, s"Invalid number $number. Number must be > 0")
  }

  @JsonView(Array(classOf[Views.Default]))
  case class RespGeneratePost(blockIds : Seq[String])

}