package io.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import io.horizen.account.state.WithdrawalMsgProcessor.calculateKey
import io.horizen.account.utils.BigIntegerUtil
import io.horizen.evm.Address
import sparkz.crypto.hash.Blake2b256

import java.math.BigInteger

class StateDbArray(val account: Address, val keySeed: Array[Byte]) {
  protected val baseArrayKey: Array[Byte] = Blake2b256.hash(keySeed)

  def getSize(view: BaseAccountStateView): Int = {
    val size = new BigInteger(1, view.getAccountStorage(account, baseArrayKey)).intValueExact()
    size
  }

  protected def updateSize(view: BaseAccountStateView, newSize: Int): Unit = {
    val paddedSize = BigIntegerUtil.toUint256Bytes(BigInteger.valueOf(newSize))
    view.updateAccountStorage(account, baseArrayKey, paddedSize)
  }

  def append(view: BaseAccountStateView, value: Array[Byte]): Int = {
    val numOfElem: Int = getSize(view)
    val key = getElemKey(numOfElem)
    view.updateAccountStorage(account, key, value)
    updateSize(view, numOfElem + 1)
    numOfElem
  }

  def removeAndRearrange(view: BaseAccountStateView, index: Int): Array[Byte] = {
    // Remove item at position "index" and move the last element of the array to that place, so there aren't gaps in
    // the array
    require(index  >=  0, "Index cannot be negative")

    val size: Int = getSize(view)
    require(index < size, "Index out of range")

    val lastElemIndex: Int = size - 1
    val lastElemKey = getElemKey(lastElemIndex)
    val lastElemValue = view.getAccountStorage(account, lastElemKey)
    view.removeAccountStorage(account, lastElemKey)
    updateSize(view, size - 1)

    if (index != lastElemIndex)
      updateValue(view, index, lastElemValue)
    lastElemValue
  }

  def updateValue(view: BaseAccountStateView, index: Int, newValue: Array[Byte]): Unit = {
    require(index >= 0, "Index cannot be negative")

    val size: Int = getSize(view)
    require(index < size, "Index out of range")

    val key = getElemKey(index)
    view.updateAccountStorage(account, key, newValue)
  }

  def getValue(view: BaseAccountStateView, index: Int): Array[Byte] = {
    require(index  >=  0, "Index cannot be negative")
    val key = getElemKey(index)
    val value = view.getAccountStorage(account, key)
    value
  }

  private def getElemKey(index: Int): Array[Byte] = {
    calculateKey(Bytes.concat(baseArrayKey, Ints.toByteArray(index)))
  }

}
