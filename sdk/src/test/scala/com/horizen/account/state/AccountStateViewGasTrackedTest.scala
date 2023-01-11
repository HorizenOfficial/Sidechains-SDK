package com.horizen.account.state

import com.horizen.account.utils.BigIntegerUtil
import org.junit.Assert.assertEquals
import org.junit._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger

class AccountStateViewGasTrackedTest extends JUnitSuite with MessageProcessorFixture with TableDrivenPropertyChecks {
  @Test
  def testUpdateAccountStorageGasAndRefund(): Unit = {
    // all operations below are performed on the same storage key in the same account
    val key = hashNull

    /**
     * Test cases from EIP-3529 document:
     * https://github.com/ethereum/EIPs/blob/master/EIPS/eip-3529.md#with-reduced-refunds
     */
    val testCases = Table(
      ("Original value", "Update sequence", "Expected used gas", "Expected refund"),
      (0, Seq(0, 0), 212, 0),
      (0, Seq(0, 1), 20112, 0),
      (0, Seq(1, 0), 20112, 19900),
      (0, Seq(1, 2), 20112, 0),
      (0, Seq(1, 1), 20112, 0),
      (1, Seq(0, 0), 3012, 4800),
      (1, Seq(0, 1), 3012, 2800),
      (1, Seq(0, 2), 3012, 0),
      (1, Seq(2, 0), 3012, 4800),
      (1, Seq(2, 3), 3012, 0),
      (1, Seq(2, 1), 3012, 2800),
      (1, Seq(2, 2), 3012, 0),
      (1, Seq(1, 0), 3012, 4800),
      (1, Seq(1, 2), 3012, 0),
      (1, Seq(1, 1), 212, 0),
      (0, Seq(1, 0, 1), 40118, 19900),
      (1, Seq(0, 1, 0), 5918, 7600)
    )

    forAll(testCases) { (original, sequence, expectedUsedGas, expectedRefund) =>
      usingView { view =>
        // prevent the account from being "empty" and pruned on commit
        view.increaseNonce(origin)
        // setup gas tracking
        val gas = new GasPool(1000000)
        val gasView = view.getGasTrackedView(gas).asInstanceOf[AccountStateViewGasTracked]
        // push state into the "original" slot
        gasView.updateAccountStorage(origin, key, BigIntegerUtil.toUint256Bytes(original))
        gasView.finalizeChanges()
        // make sure the original value was properly committed
        assertEquals("failed to set original value", gasView.getAccountStorage(origin, key).last, original)
        // return gas used for setup
        gas.addGas(gas.getUsedGas)
        // write values in the given sequence
        for (value <- sequence) {
          gasView.updateAccountStorage(origin, key, BigIntegerUtil.toUint256Bytes(value))
        }

        /**
         * Note: Within the EVM, prior to the SSTORE opcode, two PUSH opcodes have to be used to prepare the stack for
         * the call. These consume 3 gas each, resulting in an additional 6 gas used for each value in the sequence. We
         * don't consume gas on a instruction level, but we would like to use the exact expected gas values from the
         * EIP, hence the little correction here.
         */
        val pushGas = sequence.length * 6
        assertEquals("should have expected gas use", expectedUsedGas - pushGas, gas.getUsedGas.intValueExact())
        assertEquals("should have expected refunds", expectedRefund, view.getRefund.intValueExact())
      }
    }
  }

  @Test
  def testBalanceGas(): Unit = {
    usingView { view =>
      // setup gas tracking
      val gas = new GasPool(1000000)
      val gasView = view.getGasTrackedView(gas)
      gasView.addBalance(origin, BigInteger.ONE)
      assertEquals("unexpected gas usage for cold account access", 2600, gas.getUsedGas.intValueExact())
      gasView.addBalance(origin, BigInteger.ONE)
      assertEquals("unexpected gas usage for warm account access", 2700, gas.getUsedGas.intValueExact())
    }
  }
}
