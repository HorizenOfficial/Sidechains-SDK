package io.horizen.fork

trait ForkActivation {
  val regtest: Int
  val testnet: Int
  val mainnet: Int

  def get(network: String): Int = network match {
    case "regtest" => regtest
    case "testnet" => testnet
    case "mainnet" => mainnet
    case _ => throw new IllegalArgumentException(s"Unknown network type: $network")
  }
}
