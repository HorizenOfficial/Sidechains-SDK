package com.horizen.account.utils

import com.horizen.evm.utils.Address

object WellKnownAddresses {

  // native smart contract address
  val WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS: Address = new Address("0x0000000000000000000011111111111111111111")
  val FORGER_STAKE_SMART_CONTRACT_ADDRESS: Address = new Address("0x0000000000000000000022222222222222222222")
  val CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS: Address = new Address("0x0000000000000000000044444444444444444444")

}
