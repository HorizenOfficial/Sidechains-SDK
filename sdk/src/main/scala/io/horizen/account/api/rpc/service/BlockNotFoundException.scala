package io.horizen.account.api.rpc.service

import io.horizen.account.api.rpc.handler.RpcException
import io.horizen.account.api.rpc.utils.{RpcCode, RpcError}

case class BlockNotFoundException() extends RpcException(RpcError.fromCode(RpcCode.UnknownBlock))
