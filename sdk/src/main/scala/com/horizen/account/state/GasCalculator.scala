package com.horizen.account.state

import com.horizen.account.utils.BigIntegerUtil

import java.math.BigInteger

object GasCalculator {
  val TxGas: BigInteger = BigInteger.valueOf(21000)
  val TxGasContractCreation: BigInteger = BigInteger.valueOf(53000)
  val TxDataZeroGas: BigInteger = BigInteger.valueOf(4)
  val TxDataNonZeroGasEIP2028: BigInteger = BigInteger.valueOf(16)

  val GasTBD: BigInteger = BigInteger.ZERO

  val CallGasEIP150: BigInteger = BigInteger.valueOf(700)
  val BalanceGasEIP1884: BigInteger = BigInteger.valueOf(700)
  val ExtcodeHashGasEIP1884: BigInteger = BigInteger.valueOf(700)

  val LogGas: BigInteger = BigInteger.valueOf(375)
  val LogTopicGas: BigInteger = BigInteger.valueOf(375)

  val GasTBD: BigInteger = BigInteger.ZERO

  // The Refund Quotient is the cap on how much of the used gas can be refunded. Before EIP-3529,
  // up to half the consumed gas could be refunded. Redefined as 1/5th in EIP-3529
  val RefundQuotient: BigInteger = BigInteger.valueOf(2)
  val RefundQuotientEIP3529: BigInteger = BigInteger.valueOf(5)

  def intrinsicGas(data: Array[Byte], isContractCreation: Boolean): BigInteger = {
    // Set the starting gas for the raw transaction
    var gas = if (isContractCreation) TxGasContractCreation else TxGas

    // Bump the required gas by the amount of transactional data
    if (data.length > 0) {
      // Zero and non-zero bytes are priced differently
      val nonZeroElements = data.count(_.signum != 0)
      val zeroElements = data.length - nonZeroElements
      gas = gas.add(TxDataNonZeroGasEIP2028.multiply(BigInteger.valueOf(nonZeroElements)))
      gas = gas.add(TxDataZeroGas.multiply(BigInteger.valueOf(zeroElements)))
      if (!BigIntegerUtil.isUint64(gas)) throw GasUintOverflowException()
    }

    // TODO: if we support accessList in a transaction we need this too
//    if accessList != nil {
//      gas += uint64(len(accessList)) * params.TxAccessListAddressGas
//      gas += uint64(accessList.StorageKeys()) * params.TxAccessListStorageKeyGas
//    }

    gas
  }
}
