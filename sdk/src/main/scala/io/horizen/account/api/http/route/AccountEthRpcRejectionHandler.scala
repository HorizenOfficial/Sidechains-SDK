package io.horizen.account.api.http.route

import akka.http.scaladsl.server.{MalformedRequestContentRejection, RejectionHandler}
import io.horizen.account.api.rpc.request.RpcId
import io.horizen.account.api.rpc.response.RpcResponseError
import io.horizen.account.api.rpc.utils.{RpcCode, RpcError}
import io.horizen.account.serialization.EthJsonMapper
import io.horizen.api.http.SidechainApiResponse

object AccountEthRpcRejectionHandler {

  def rejectionHandler: RejectionHandler =
    RejectionHandler.newBuilder()
      .handle {
        case MalformedRequestContentRejection(msg, _) =>
          SidechainApiResponse(
            EthJsonMapper.serialize(
              new RpcResponseError(
                new RpcId(),
                RpcError.fromCode(RpcCode.ParseError, msg)
              )
            ), hasError = true
          )
      }
      .result()
}
