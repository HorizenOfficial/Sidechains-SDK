package io.horizen.account.state

import com.sun.jna.platform.win32.WinNT.WELL_KNOWN_SID_TYPE
import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.ForgerStakeStorageV3.{DelegatorList, ForgerList, StakeHistory}
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.evm.{Address, Hash}
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.utils.{BytesUtils, Ed25519}
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite
import sparkz.crypto.hash.Blake2b256

import java.math.BigInteger
import java.util.Random

class ForgerStakeStorageV3Test
  extends JUnitSuite
  with MessageProcessorFixture {


  @Test
  def testAddForger(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS)

      var result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)

      assertTrue(result._2.isEmpty)
      assertEquals(-1, result._1)

      val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
      val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

      val delegator = new Address("0xaaa00001230000000000deadbeefaaaa22222222")
      val epochNumber = 135869

      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val stakeAmount = BigInteger.TEN

      ForgerStakeStorageV3.addForger(view, blockSignerProposition, vrfPublicKey,rewardShare, rewardAddress, epochNumber, delegator, stakeAmount)

      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)

      var listOfForgers = result._2
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result._1)

      val forgerKey = ForgerStakeStorageV3.getForgerKey(blockSignerProposition, vrfPublicKey)
      val delegatorList = DelegatorList(forgerKey)
      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator, delegatorList.getDelegatorAt(view, 0).address())

      val forgerHistory = StakeHistory(forgerKey)
      assertEquals(1, forgerHistory.getSize(view))
      assertEquals(epochNumber, forgerHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, forgerHistory.getCheckpoint(view, 0).stakedAmount)

      val stakeKey = ForgerStakeStorageV3.getStakeKey(blockSignerProposition, vrfPublicKey, delegator)
      val stakeHistory = StakeHistory(stakeKey)
      assertEquals(1, stakeHistory.getSize(view))
      assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)

      val forgerList = ForgerList(new AddressProposition(delegator))
      assertEquals(1, forgerList.getSize(view))
      assertArrayEquals(forgerKey, forgerList.getValue(view, 0))


      //  Try to register twice the same forger. It should fail
      val ex = intercept[ExecutionRevertedException] {
        ForgerStakeStorageV3.addForger(view, blockSignerProposition, vrfPublicKey,rewardShare, rewardAddress, epochNumber, delegator, stakeAmount)
      }
      assertEquals("Forger already registered.", ex.getMessage)

      // Try to register another forger with the same delegator and the same rewardAddress
      val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("4455334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
      val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("445575fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes
      val rewardShare2 = 87
      val stakeAmount2 = ZenWeiConverter.MAX_MONEY_IN_WEI
      val epochNumber2 = 444555444
      ForgerStakeStorageV3.addForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare2, rewardAddress, epochNumber2, delegator, stakeAmount2)

      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      listOfForgers = result._2
      assertEquals(2, listOfForgers.size)
      assertEquals(blockSignerProposition, listOfForgers(0).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey, listOfForgers(0).forgerPublicKeys.vrfPublicKey)

      assertEquals(blockSignerProposition2, listOfForgers(1).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey2, listOfForgers(1).forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers(1).rewardAddress.address())
      assertEquals(rewardShare2, listOfForgers(1).rewardShare)
      assertEquals(-1, result._1)

      // Check that the first forger was not changed
      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator, delegatorList.getDelegatorAt(view, 0).address())

      assertEquals(1, forgerHistory.getSize(view))
      assertEquals(epochNumber, forgerHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, forgerHistory.getCheckpoint(view, 0).stakedAmount)

      assertEquals(1, stakeHistory.getSize(view))
      assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)


      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator, delegatorList.getDelegatorAt(view, 0).address())

      assertEquals(1, forgerHistory.getSize(view))
      assertEquals(epochNumber, forgerHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, forgerHistory.getCheckpoint(view, 0).stakedAmount)

      assertEquals(1, stakeHistory.getSize(view))
      assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)

      // Check second forger
      val forgerKey2 = ForgerStakeStorageV3.getForgerKey(blockSignerProposition2, vrfPublicKey2)
      val delegatorList2 = DelegatorList(forgerKey2)
      assertEquals(1, delegatorList2.getSize(view))
      assertEquals(delegator, delegatorList2.getDelegatorAt(view, 0).address())

      val forgerHistory2 = StakeHistory(forgerKey2)
      assertEquals(1, forgerHistory2.getSize(view))
      assertEquals(epochNumber2, forgerHistory2.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount2, forgerHistory2.getCheckpoint(view, 0).stakedAmount)

      val stakeKey2 = ForgerStakeStorageV3.getStakeKey(blockSignerProposition2, vrfPublicKey2, delegator)
      val stakeHistory2 = StakeHistory(stakeKey2)
      assertEquals(1, stakeHistory2.getSize(view))
      assertEquals(epochNumber2, stakeHistory2.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount2, stakeHistory2.getCheckpoint(view, 0).stakedAmount)


      assertEquals(2, forgerList.getSize(view))
      assertArrayEquals(forgerKey, forgerList.getValue(view, 0))
      assertArrayEquals(forgerKey2, forgerList.getValue(view, 1))

    }
  }


  @Test
  def testGetPagedListOfForgers(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS)

      var result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      assertTrue(result._2.isEmpty)
      assertEquals(-1, result._1)

      assertThrows[IllegalArgumentException] {
        ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 0)
      }

      assertThrows[IllegalArgumentException] {
        ForgerStakeStorageV3.getPagedListOfForgers(view, 1, 10)
      }

      assertThrows[IllegalArgumentException] {
        ForgerStakeStorageV3.getPagedListOfForgers(view, 1, -10)
      }

      assertThrows[IllegalArgumentException] {
        ForgerStakeStorageV3.getPagedListOfForgers(view, -1, 10)
      }

      val numOfForgers = 100
      val listOfExpectedData = (0 until numOfForgers).map{idx =>
        val postfix = f"$idx%03d"
        val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(s"1122334455667788112233445566778811223344556677881122334455667$postfix")) // 32 bytes
        val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(s"d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1$postfix")) // 33 bytes

        val delegator = new Address(s"0xaaa00001230000000000deadbeefaaaa22222$postfix")
        val epochNumber = 135869 + idx

        val rewardAddress = new Address(s"0xaaa0000123000000000011112222aaaa22222$postfix")
        val rewardShare = idx + 1
        val stakeAmount = ZenWeiConverter.convertZenniesToWei(idx + 1)

        ForgerStakeStorageV3.addForger(view, blockSignerProposition, vrfPublicKey,rewardShare, rewardAddress, epochNumber, delegator, stakeAmount)


        val forgerKey = ForgerStakeStorageV3.getForgerKey(blockSignerProposition, vrfPublicKey)
        val delegatorList = DelegatorList(forgerKey)
        assertEquals(1, delegatorList.getSize(view))
        assertEquals(delegator, delegatorList.getDelegatorAt(view, 0).address())

        val forgerHistory = StakeHistory(forgerKey)
        assertEquals(1, forgerHistory.getSize(view))
        assertEquals(epochNumber, forgerHistory.getCheckpoint(view, 0).fromEpochNumber)
        assertEquals(stakeAmount, forgerHistory.getCheckpoint(view, 0).stakedAmount)

        val stakeKey = ForgerStakeStorageV3.getStakeKey(blockSignerProposition, vrfPublicKey, delegator)
        val stakeHistory = StakeHistory(stakeKey)
        assertEquals(1, stakeHistory.getSize(view))
        assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
        assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)

        val forgerList = ForgerList(new AddressProposition(delegator))
        assertEquals(1, forgerList.getSize(view))
        assertArrayEquals(forgerKey, forgerList.getValue(view, 0))

        (blockSignerProposition, vrfPublicKey, rewardAddress, rewardShare)
      }

      val pageSize = 11
      var continue = true
      var listOfResults = Seq.empty[ForgerInfoV3]
      var startPos = 0

      while (continue) {
        val result = ForgerStakeStorageV3.getPagedListOfForgers(view, startPos, pageSize)
        listOfResults = listOfResults ++ result._2
        continue = if (result._1 != -1){
          assertEquals(pageSize, result._2.size)
          true
        }
        else
          false
        startPos = result._1

      }

      assertEquals(listOfExpectedData.size, listOfResults.size)
      (0 until numOfForgers).foreach{idx =>
        val (blockSignerProposition, vrfPublicKey, rewardAddress, rewardShare) = listOfExpectedData(idx)
        val forgerInfo = listOfResults(idx)
        assertEquals(blockSignerProposition, forgerInfo.forgerPublicKeys.blockSignPublicKey)
        assertEquals(vrfPublicKey, forgerInfo.forgerPublicKeys.vrfPublicKey)
        assertEquals(rewardAddress, forgerInfo.rewardAddress.address())
        assertEquals(rewardShare, forgerInfo.rewardShare)
      }

    }
  }

}
