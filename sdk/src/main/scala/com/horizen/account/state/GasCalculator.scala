package com.horizen.account.state

import com.horizen.account.utils.BigIntegerUtil

import java.math.BigInteger

object GasCalculator {
  val TxGasContractCreation: BigInteger = BigInteger.valueOf(53000)
  val TxGas: BigInteger = BigInteger.valueOf(21000)
  val TxDataNonZeroGasFrontier: BigInteger = BigInteger.valueOf(68)
  val TxDataNonZeroGasEIP2028: BigInteger = BigInteger.valueOf(16)
  val TxDataZeroGas: BigInteger = BigInteger.valueOf(4)

  def intrinsicGas(data: Array[Byte], isContractCreation: Boolean): BigInteger = {
    // Set the starting gas for the raw transaction
    var gas = if (isContractCreation) TxGasContractCreation else TxGas

    // Bump the required gas by the amount of transactional data
    if (data.length > 0) {
      // TODO: configurable or not?
      val isEIP2028 = true
      val nonZeroGas = if (isEIP2028) TxDataNonZeroGasEIP2028 else TxDataNonZeroGasFrontier

      // Zero and non-zero bytes are priced differently
      val nonZeroElements = data.count(_.signum != 0)
      val zeroElements = data.length - nonZeroElements

      gas = gas.add(nonZeroGas.multiply(BigInteger.valueOf(nonZeroElements)))
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
