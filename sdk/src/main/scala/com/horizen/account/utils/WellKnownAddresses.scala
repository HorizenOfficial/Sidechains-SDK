package com.horizen.account.utils

import com.horizen.utils.BytesUtils

object WellKnownAddresses {

  // fake smart contract address
  val WITHDRAWAL_REQ_SMART_CONTRACT_ADDRESS_BYTES: Array[Byte] = BytesUtils.fromHexString("0000000000000000000011111111111111111111")
  val FORGER_STAKE_SMART_CONTRACT_ADDRESS_BYTES: Array[Byte] = BytesUtils.fromHexString("0000000000000000000022222222222222222222")

  // used to burn coins
  val NULL_ADDRESS_BYTES: Array[Byte] = BytesUtils.fromHexString("0000000000000000000000000000000000000000")

}
