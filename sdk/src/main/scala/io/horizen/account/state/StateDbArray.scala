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

  private def updateSize(view: BaseAccountStateView, newSize: Int): Unit = {
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

  private def removeLast(view: BaseAccountStateView): (Array[Byte], Int) = {
    val size: Int = getSize(view)
    val lastElemIndex: Int = size - 1
    val key = getElemKey(lastElemIndex)
    val value = view.getAccountStorage(account, key)
    updateSize(view, size - 1)
    view.removeAccountStorage(account, key)
    (value, lastElemIndex)
  }

  def removeAndRearrange(view: BaseAccountStateView, index: Int): Array[Byte] = {
    // Remove last elem from the array and put its value to the position left empty, so there aren't gaps in the array
    val (lastElemValue, lastElemIndex) = removeLast(view)
    require(index <= lastElemIndex, "Index out of range")
    if (index != lastElemIndex)
      updateValue(view, index, lastElemValue)
    lastElemValue
  }

  def updateValue(view: BaseAccountStateView, index: Int, newValue: Array[Byte]): Unit = {
    val key = getElemKey(index)
    view.updateAccountStorage(account, key, newValue)
  }

  def getValue(view: BaseAccountStateView, index: Int): Array[Byte] = {
    val key = getElemKey(index)
    val value = view.getAccountStorage(account, key)
    value
  }

  private def getElemKey(index: Int): Array[Byte] = {
    calculateKey(Bytes.concat(baseArrayKey, Ints.toByteArray(index)))
  }

}
