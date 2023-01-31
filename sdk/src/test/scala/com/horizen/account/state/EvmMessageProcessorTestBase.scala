package com.horizen.account.state

import com.horizen.evm.utils.Address

trait EvmMessageProcessorTestBase extends MessageProcessorFixture {
  val emptyAddress: Address = Address.fromHex("0x00000000000000000000000000000000FFFFFF02")
  val eoaAddress: Address = Address.fromHex("0x00000000000000000000000000000000FFFFFF03")
  val contractAddress: Address = Address.fromHex("0x00000000000000000000000000000000FFFFFF04")
}
