package io.horizen.account.utils

import io.horizen.evm.Address

object WellKnownAddresses {

  // native smart contract address
  val WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS: Address = new Address("0x0000000000000000000011111111111111111111")
  val FORGER_STAKE_SMART_CONTRACT_ADDRESS: Address = new Address("0x0000000000000000000022222222222222222222")
  val CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS: Address = new Address("0x0000000000000000000044444444444444444444")
  val MC_ADDR_OWNERSHIP_SMART_CONTRACT_ADDRESS: Address = new Address("0x0000000000000000000088888888888888888888")

  // this is used for intercepting Forward Transfers from Mainchain to forger pool
  val FORGER_POOL_RECIPIENT_ADDRESS: Address = new Address("0x0000000000000000000033333333333333333333")
  // TODO: this address should be checked in the eoa msg processor too, in case anyone wanted to send funds to theforger pool.
  //       As an alternative one could check in the forging phase: if this address has balance then move it to the forger pool

}
