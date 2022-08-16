package com.horizen.account.state

case class OutOfGasException() extends ExecutionFailedException("out of gas");
