package com.horizen.account.utils

import com.horizen.utils.BytesUtils


object MainchainTxCrosschainOutputAddressUtil {
  // we must get 20 bytes out of 32 with the proper padding and byte order
  // MC prepends a padding of "0 bytes" (if needed) in the ccout address up to the length of 32 bytes.
  // After reversing the bytes, the padding is trailed to the correct 20 bytes proposition
  def getAccountAddress(inputAddress: Array[Byte]): Array[Byte] = {
    require(inputAddress.length == 32)
    BytesUtils.reverseBytes(inputAddress.take(com.horizen.account.utils.Account.ADDRESS_SIZE))
  }
}
