package com.horizen.account.state

case class Account(nonce: Long,
                   balance: Long,
                   codeHash: Array[Byte],
                   storageRoot: Array[Byte])
