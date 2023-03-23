package io.horizen.account.state

import io.horizen.account.state.receipt.EthereumConsensusDataLog
import io.horizen.account.transaction.EthereumTransaction
import io.horizen.account.utils.BigIntegerUtil

import java.math.BigInteger

// numbers based on geth implementation:
// https://github.com/ethereum/go-ethereum/blob/v1.10.26/params/protocol_params.go
object GasUtil {
  val TxGas: BigInteger = BigInteger.valueOf(21000)
  val TxGasContractCreation: BigInteger = BigInteger.valueOf(53000)
  val TxDataZeroGas: BigInteger = BigInteger.valueOf(4)
  val TxDataNonZeroGasEIP2028: BigInteger = BigInteger.valueOf(16)

  val ColdAccountAccessCostEIP2929: BigInteger = BigInteger.valueOf(2600)
  val WarmStorageReadCostEIP2929: BigInteger = BigInteger.valueOf(100)
  val ColdSloadCostEIP2929: BigInteger = BigInteger.valueOf(2100)

  val SstoreSentryGasEIP2200: BigInteger = BigInteger.valueOf(2300)
  val SstoreSetGasEIP2200: BigInteger = BigInteger.valueOf(20000)
  val SstoreResetGasEIP2200: BigInteger = BigInteger.valueOf(5000)

  val SstoreClearsScheduleRefundEIP3529: BigInteger = BigInteger.valueOf(4800)

  val CopyGas: BigInteger = BigInteger.valueOf(3)

  val LogGas: BigInteger = BigInteger.valueOf(375)
  val LogTopicGas: BigInteger = BigInteger.valueOf(375)
  val LogDataGas: BigInteger = BigInteger.valueOf(8)

  // The Refund Quotient is the cap on how much of the used gas can be refunded. Before EIP-3529,
  // up to half the consumed gas could be refunded. Redefined as 1/5th in EIP-3529
  val RefundQuotientEIP3529: BigInteger = BigInteger.valueOf(5)

  def intrinsicGas(data: Array[Byte], isContractCreation: Boolean): BigInteger = {
    // Set the starting gas for the raw transaction
    var gas = if (isContractCreation) TxGasContractCreation else TxGas

    // Bump the required gas by the amount of transactional data
    if (data != null && data.length > 0) {
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

  def logGas(log: EthereumConsensusDataLog): BigInteger = LogGas
    .add(LogTopicGas.multiply(BigInteger.valueOf(log.topics.length)))
    .add(LogDataGas.multiply(BigInteger.valueOf(log.data.length)))

  def codeCopy(size: Int): BigInteger = {
    // code size in number of 256-bit words (round up division)
    val words = BigInteger.valueOf((size + 31) / 32)
    CopyGas.multiply(words)
  }

  def getTxFeesPerGas(tx: EthereumTransaction, baseFeePerGas: BigInteger): (BigInteger, BigInteger) = {
    if (tx.isEIP1559) {
      val maxFeePerGas = tx.getMaxFeePerGas
      val maxPriorityFeePerGas = tx.getMaxPriorityFeePerGas
      // if the Base Fee plus the Max Priority Fee exceeds the Max Fee, the Max Priority Fee will be reduced
      // in order to maintain the upper bound of the Max Fee.
      val forgerTipPerGas = if (baseFeePerGas.add(maxPriorityFeePerGas).compareTo(maxFeePerGas) > 0) {
        maxFeePerGas.subtract(baseFeePerGas)
      } else {
        maxPriorityFeePerGas
      }
      (baseFeePerGas, forgerTipPerGas)
    } else {
      // Even in legacy transactions the gasPrice has to be greater or equal to the base fee
      (baseFeePerGas, tx.getGasPrice.subtract(baseFeePerGas))
    }
  }
}
