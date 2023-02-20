package com.horizen.account.state

import com.horizen.account.receipt.EthereumConsensusDataLog
import com.horizen.account.utils.BigIntegerUtil
import com.horizen.evm.utils.Hash
import org.junit.Assert.assertEquals
import org.junit._
import org.scalatest.prop.TableDrivenPropertyChecks
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger

class StateDbAccountStateViewGasTrackedTest
    extends JUnitSuite
      with MessageProcessorFixture
      with TableDrivenPropertyChecks {
  @Test
  def testAccountStorageEIP3529GasAndRefunds(): Unit = {

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

    // all operations below are performed on the same storage key in the same account
    val key = Hash.ZERO.toBytes

    forAll(testCases) { (original, sequence, expectedUsedGas, expectedRefund) =>
      usingView { view =>
        // prevent the account from being "empty" and pruned on commit
        view.increaseNonce(origin)
        // setup gas tracking
        val gas = new GasPool(1000000)
        val gasView = view.getGasTrackedView(gas).asInstanceOf[StateDbAccountStateViewGasTracked]
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
         * don't consume gas on an instruction level, but we would like to test against the exact expected gas values
         * from the EIP, hence the little correction here.
         */
        val pushGas = sequence.length * 6
        assertEquals("should have expected gas use", expectedUsedGas - pushGas, gas.getUsedGas.intValueExact())
        assertEquals("should have expected refunds", expectedRefund, view.getRefund.intValueExact())
      }
    }
  }

  @Test
  def testAccountAccess(): Unit = {

    val (slot1, slot2) = (randomHash, randomHash)

    val testCases = Table[Int, Seq[BaseAccountStateView => Unit]](
      ("Expected used gas", "Access sequence"),
      // cold account access
      (2600, Seq(_.accountExists(origin))),
      (2600, Seq(_.isEoaAccount(origin))),
      (2600, Seq(_.isSmartContractAccount(origin))),
      (2600, Seq(_.getNonce(origin))),
      (2600, Seq(_.getBalance(origin))),
      (2600, Seq(_.getCodeHash(origin))),
      (2600, Seq(_.getCode(origin))),
      (2600, Seq(_.addBalance(origin, BigInteger.ONE))),
      // warm account access
      (2700, Seq(_.getNonce(origin), _.getNonce(origin))),
      (2700, Seq(_.getNonce(origin), _.getBalance(origin))),
      (2700, Seq(_.getNonce(origin), _.getCodeHash(origin))),
      (2700, Seq(_.getNonce(origin), _.getCode(origin))),
      (2700, Seq(_.addBalance(origin, BigInteger.TEN), _.subBalance(origin, BigInteger.ONE))),
      // increasing the nonce contains two accesses: read value, increment, write value
      (2700, Seq(_.increaseNonce(origin))),
      (2900, Seq(_.increaseNonce(origin), _.increaseNonce(origin))),
      // cold and warm account storage slot access
      (2100, Seq(_.getAccountStorage(origin, slot1))),
      (2100, Seq(_.getAccountStorage(origin, slot2))),
      (2200, Seq(_.getAccountStorage(origin, slot1), _.getAccountStorage(origin, slot1))),
      (4200, Seq(_.getAccountStorage(origin, slot1), _.getAccountStorage(origin, slot2))),
      (
        4300,
        Seq(_.getAccountStorage(origin, slot1), _.getAccountStorage(origin, slot2), _.getAccountStorage(origin, slot1))
      ),
      (4700, Seq(_.getNonce(origin), _.getAccountStorage(origin, slot1))),
      (4800, Seq(_.getNonce(origin), _.getAccountStorage(origin, slot1), _.getAccountStorage(origin, slot1))),
      (2300, Seq(_.getAccountStorage(origin, slot1), _.getAccountStorage(origin, slot1), _.getNonce(origin))),
      // account storage modification
      (24800, Seq(_.increaseNonce(origin), _.updateAccountStorage(origin, slot1, randomHash))),
      (4900, Seq(_.increaseNonce(origin), _.removeAccountStorage(origin, slot1))),
      (
        24900,
        Seq(
          _.increaseNonce(origin),
          _.updateAccountStorage(origin, slot1, randomHash),
          _.removeAccountStorage(origin, slot1)
        )
      ),
    )

    forAll(testCases) { (expectedGas, sequence) =>
      usingView { view =>
        // setup gas tracking
        val gas = new GasPool(1000000)
        val gasView = view.getGasTrackedView(gas)
        // execute access sequence
        sequence.foreach(_(gasView))
        // verify gas usage
        assertEquals("unexpected gas usage for state access", expectedGas, gas.getUsedGas.intValueExact())
      }
    }
  }

  private def randomLog(topics: Int, data: Int): EthereumConsensusDataLog = {
    EthereumConsensusDataLog(randomAddress, Array.fill(topics)(new Hash(randomHash)), randomBytes(data))
  }

  @Test
  def testLogGas(): Unit = {
    val testCases = Table(
      ("Expected used gas", "Evm Log"),
      (375, randomLog(0, 0)),
      (750, randomLog(1, 0)),
      (1125, randomLog(2, 0)),
      (1500, randomLog(3, 0)),
      (1875, randomLog(4, 0)),
      (8567, randomLog(0, 1024)),
      (886, randomLog(1, 17)),
      (1557, randomLog(2, 54)),
      (2732, randomLog(3, 154)),
      (1883, randomLog(4, 1))
    )

    forAll(testCases) { (expectedGas, log) =>
      usingView { view =>
        // setup gas tracking
        val gas = new GasPool(1000000)
        val gasView = view.getGasTrackedView(gas)
        // emit log and verify gas usage
        gasView.addLog(log)
        assertEquals("unexpected gas usage for one event log", expectedGas, gas.getUsedGas.intValueExact())
        // gas usage of multiple logs (even if identical) should increase linearly
        gasView.addLog(log)
        assertEquals("unexpected gas usage for two event logs", expectedGas * 2, gas.getUsedGas.intValueExact())
        gasView.addLog(log)
        assertEquals("unexpected gas usage for three event logs", expectedGas * 3, gas.getUsedGas.intValueExact())
      }
    }
  }
}
