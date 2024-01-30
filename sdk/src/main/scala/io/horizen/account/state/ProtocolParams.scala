package io.horizen.account.state

// numbers based on geth implementation:
// https://github.com/ethereum/go-ethereum/blob/v1.10.26/params/protocol_params.go
object ProtocolParams {
  val MaxCodeSize: Int = 24576   // Maximum bytecode to permit for a contract
  val MaxInitCodeSize: Int = 2 * MaxCodeSize // Maximum initcode to permit in a creation transaction and create instructions

}