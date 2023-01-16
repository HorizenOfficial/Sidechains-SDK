package com.horizen.account.state

import com.horizen.account.state.InvalidMessageException.{toAddress, toHex}
import com.horizen.account.utils.Secp256k1
import com.horizen.transaction.exception.TransactionSemanticValidityException
import com.horizen.utils.BytesUtils

import java.math.BigInteger

/**
 * Error message kept very close to geth implementation, see:
 * https://github.com/ethereum/go-ethereum/blob/v1.10.16/core/error.go
 *
 * List of evm-call-message pre-checking errors. All state transition messages will be pre-checked before execution.
 * If any invalidation is detected, the corresponding error should be returned which is defined here.
 *
 *  - If the pre-checking happens in the miner, then the transaction won't be packed.
 *  - If the pre-checking happens in the block processing procedure, then a "BAD BLOCK" error should be emitted
 * */
class InvalidMessageException(message: String) extends TransactionSemanticValidityException(message)

private object InvalidMessageException {
  def toAddress(address: Array[Byte]): String = Secp256k1.checksumAddress(address)

  def toHex(data: Array[Byte]): String = BytesUtils.toHexString(data)
}

/** ErrNonceTooLow is returned if the nonce of a transaction is lower than the one present in the local chain. */
case class NonceTooLowException(address: Array[Byte], txNonce: BigInteger, stateNonce: BigInteger)
  extends InvalidMessageException(s"nonce too low: address ${toAddress(address)}, tx $txNonce, state $stateNonce")

/** ErrNonceTooHigh is returned if the nonce of a transaction is higher than the next one expected based on the local chain. */
case class NonceTooHighException(address: Array[Byte], txNonce: BigInteger, stateNonce: BigInteger)
  extends InvalidMessageException(s"nonce too high: address ${toAddress(address)}, tx $txNonce, state $stateNonce")

/** ErrNonceMax is returned if the nonce of a transaction sender account has maximum allowed value and would become invalid if incremented. */
case class NonceMaxException(address: Array[Byte], stateNonce: BigInteger)
  extends InvalidMessageException(s"nonce has max value: address ${toAddress(address)}, state $stateNonce")

/** ErrGasLimitReached is returned by the gas pool if the amount of gas required by a transaction is higher than what's left in the block. */
case class GasLimitReached() extends InvalidMessageException("gas limit reached")

/** ErrInsufficientFunds is returned if the total cost of executing a transaction is higher than the balance of the user's account. */
case class InsufficientFundsException(address: Array[Byte], have: BigInteger, want: BigInteger)
  extends InvalidMessageException(s"insufficient funds for gas * price + value: address ${toAddress(address)}, have $have, want $want")

/** ErrGasUintOverflow is returned when calculating gas usage. */
case class GasUintOverflowException() extends InvalidMessageException("gas uint64 overflow")

/** ErrIntrinsicGas is returned if the transaction is specified to use less gas than required to start the invocation. */
case class IntrinsicGasException(have: BigInteger, want: BigInteger)
  extends InvalidMessageException(s"intrinsic gas too low: have $have, want $want")

/** ErrFeeCapTooLow is returned if the transaction fee cap is less than the	base fee of the block. */
case class FeeCapTooLowException(address: Array[Byte], maxFeePerGas: BigInteger, baseFee: BigInteger)
  extends InvalidMessageException(s"max fee per gas less than block base fee: address ${toAddress(address)}, maxFeePerGas $maxFeePerGas, baseFee $baseFee")

/** ErrSenderNoEOA is returned if the sender of a transaction is a contract. */
case class SenderNotEoaException(address: Array[Byte], codeHash: Array[Byte])
  extends InvalidMessageException(s"sender not an eoa: address ${toAddress(address)}, codeHash ${toHex(codeHash)}")
