package io.horizen.account.utils

import io.horizen.evm.Address
import io.horizen.utils.BytesUtils

object MainchainTxCrosschainOutputAddressUtil {

  /**
   * We must get 20 bytes out of 32 with the proper padding and byte order. MC prepends a padding of "0 bytes" (if
   * needed) in the ccout address up to the length of 32 bytes. After reversing the bytes, the padding is trailed to the
   * correct 20 bytes proposition.
   *
   * @param inputAddress address from the CrosschainOutput
   * @return
   */
  def getAccountAddress(inputAddress: Array[Byte]): Address = {
    require(inputAddress.length == 32, s"byte array length ${inputAddress.length} != 32")
    new Address(BytesUtils.reverseBytes(inputAddress.take(Address.LENGTH)))
  }
}
