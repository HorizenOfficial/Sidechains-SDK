package com.horizen.account.state

import io.horizen.evm.Address

trait EvmMessageProcessorTestBase extends MessageProcessorFixture {
  val emptyAddress: Address = new Address("0x00000000000000000000000000000000FFFFFF02")
  val eoaAddress: Address = new Address("0x00000000000000000000000000000000FFFFFF03")
  val contractAddress: Address = new Address("0x00000000000000000000000000000000FFFFFF04")
}
