package io.horizen.account.api.rpc.service

import com.fasterxml.jackson.databind.JsonNode
import io.horizen.account.api.rpc.handler.{RpcException, RpcHandler}
import io.horizen.account.api.rpc.request.{RpcId, RpcRequest}
import io.horizen.account.api.rpc.response.RpcResponseError
import io.horizen.account.api.rpc.utils.{RpcCode, RpcError}
import io.horizen.account.serialization.EthJsonMapper
import sparkz.util.SparkzLogging

import scala.jdk.CollectionConverters.asScalaIteratorConverter
import scala.util.{Failure, Success, Try}


case class RpcProcessor(val rpcHandler: RpcHandler) extends SparkzLogging {

  def processEthRpc(body: JsonNode): String = {
    val requests = if (body.isArray && !body.isEmpty) {
      // if the input json is an array a batch rpc request will be handled
      // the single rpc request will retrieve from the input json and they will be processed by rpcHandler
      // the position of the elements in the output will reflect their position in the input request
      body.iterator().asScala.toArray
    } else {
      // if the input json is not an array a single rpc request will be handled
      Array(body)
    }

    val responses = requests.map(json => Try.apply(new RpcRequest(json)).map(rpcHandler.apply) match {
      case Success(value) => value
      case Failure(exception: RpcException) => new RpcResponseError(new RpcId(), exception.error);
      case Failure(exception) =>
        log.trace(s"internal error on RPC call: $exception")
        new RpcResponseError(new RpcId(), RpcError.fromCode(RpcCode.InvalidRequest));
    })

    val json = if (responses.length > 1) {
      EthJsonMapper.serialize(responses)
    } else {
      EthJsonMapper.serialize(responses.head)
    }

    log.trace(s"RPC message response << $json")
    json
  }

}
