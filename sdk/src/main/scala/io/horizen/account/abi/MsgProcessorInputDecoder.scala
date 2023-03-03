package io.horizen.account.abi

import io.horizen.account.state.ExecutionRevertedException

import scala.util.{Failure, Success, Try}

trait MsgProcessorInputDecoder[A] extends ABIDecoder[A] {

  abstract override def decode(abiEncodedData: Array[Byte]): A = {
    Try {
      // We must trap any exception arising in case of wrong data that can not be decoded according to the types
      // and return the execution reverted exception
      super.decode(abiEncodedData)
    } match {
      case Success(decodedBytes) => decodedBytes
      case Failure(ex) =>
        throw new ExecutionRevertedException("Could not decode input params: " + ex.getMessage)
    }
  }
}
