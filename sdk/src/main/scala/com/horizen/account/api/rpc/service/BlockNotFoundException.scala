package com.horizen.account.api.rpc.service

import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.utils.{RpcCode, RpcError}

case class BlockNotFoundException() extends RpcException(RpcError.fromCode(RpcCode.UnknownBlock))
