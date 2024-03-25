package io.horizen.account.state.nativescdata.forgerstakev2

import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.ForgerStakeStorageV2.getPagedForgersStakesByForger
import io.horizen.account.state._
import io.horizen.account.state.nativescdata.forgerstakev2.StakeStorage.{BaseStakeHistory, DelegatorKey, DelegatorList, DelegatorListOfForgerKeys, ForgerKey, ForgerStakeHistory, StakeHistory}
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.evm.Address
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger
import scala.language.implicitConversions

class StakeStorageTest
  extends JUnitSuite
  with MessageProcessorFixture {

  val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
  val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes
  val forger1Key: ForgerKey = ForgerKey(blockSignerProposition1, vrfPublicKey1)

  val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("4455334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
  val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("445575fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes
  val forger2Key: ForgerKey = ForgerKey(blockSignerProposition2, vrfPublicKey2)

  val delegator1 = new Address("0xaaa00001230000000000deadbeefaaaa22222222")
  val delegator2 = new Address("0xaaa00001230000000000aaaaaaabbbbb22222222")

  implicit def addressToChecksumAddress(t: Address): DelegatorKey = DelegatorKey(t)

  @Test
  def testAddForger(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)

      var result = StakeStorage.getPagedListOfForgers(view, 0, 10)

      assertTrue(result.forgers.isEmpty)
      assertEquals(-1, result.nextStartPos)
      val epochNumber = 135869

      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val stakeAmount = BigInteger.TEN

      assertTrue(StakeStorage.getForger(view, blockSignerProposition1, vrfPublicKey1).isEmpty)

      StakeStorage.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, epochNumber, delegator1, stakeAmount)

      result = StakeStorage.getPagedListOfForgers(view, 0, 10)

      var listOfForgers = result.forgers
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result.nextStartPos)

      assertEquals(listOfForgers.head, StakeStorage.getForger(view, blockSignerProposition1, vrfPublicKey1).get)

      val delegatorList = DelegatorList(forger1Key)
      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator1, delegatorList.getDelegatorAt(view, 0))


      val forger1History = ForgerStakeHistory(forger1Key)
      assertEquals(1, forger1History.getSize(view))
      assertEquals(epochNumber, forger1History.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, forger1History.getCheckpoint(view, 0).stakedAmount)
      assertEquals(stakeAmount, forger1History.getLatestAmount(view))

      val stakeHistory = StakeHistory(forger1Key, delegator1)
      assertEquals(1, stakeHistory.getSize(view))
      assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)
      assertEquals(stakeAmount, stakeHistory.getLatestAmount(view))

      val forgerList = DelegatorListOfForgerKeys(delegator1)
      assertEquals(1, forgerList.getSize(view))
      assertEquals(forger1Key, forgerList.getForgerKey(view, 0))


      //  Try to register twice the same forger. It should fail
      val ex = intercept[ExecutionRevertedException] {
        StakeStorage.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, epochNumber, delegator1, stakeAmount)
      }
      assertEquals("Forger already registered.", ex.getMessage)

      // Try to register another forger with the same delegator and the same rewardAddress
      val rewardShare2 = 87
      val stakeAmount2 = ZenWeiConverter.MAX_MONEY_IN_WEI
      val epochNumber2 = 444555444
      StakeStorage.addForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare2, rewardAddress, epochNumber2, delegator1, stakeAmount2)

      result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      listOfForgers = result.forgers
      assertEquals(2, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)

      assertEquals(blockSignerProposition2, listOfForgers(1).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey2, listOfForgers(1).forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers(1).rewardAddress.address())
      assertEquals(rewardShare2, listOfForgers(1).rewardShare)
      assertEquals(-1, result.nextStartPos)

      // Check that the first forger was not changed
      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator1, delegatorList.getDelegatorAt(view, 0))

      assertEquals(1, forger1History.getSize(view))
      assertEquals(epochNumber, forger1History.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, forger1History.getCheckpoint(view, 0).stakedAmount)
      assertEquals(stakeAmount, forger1History.getLatestAmount(view))

      assertEquals(1, stakeHistory.getSize(view))
      assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)
      assertEquals(stakeAmount, stakeHistory.getLatestAmount(view))

      // Check second forger
      val delegatorList2 = DelegatorList(forger2Key)
      assertEquals(1, delegatorList2.getSize(view))
      assertEquals(delegator1, delegatorList2.getDelegatorAt(view, 0))

      val forgerHistory2 = ForgerStakeHistory(forger2Key)
      assertEquals(1, forgerHistory2.getSize(view))
      assertEquals(epochNumber2, forgerHistory2.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount2, forgerHistory2.getCheckpoint(view, 0).stakedAmount)
      assertEquals(stakeAmount2, forgerHistory2.getLatestAmount(view))

      val stakeHistory2 = StakeHistory(forger2Key, delegator1)
      assertEquals(1, stakeHistory2.getSize(view))
      assertEquals(epochNumber2, stakeHistory2.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount2, stakeHistory2.getCheckpoint(view, 0).stakedAmount)
      assertEquals(stakeAmount2, stakeHistory2.getLatestAmount(view))

      assertEquals(2, forgerList.getSize(view))
      assertEquals(forger1Key, forgerList.getForgerKey(view, 0))
      assertEquals(forger2Key, forgerList.getForgerKey(view, 1))

      // Add a forger without reward address
      val blockSignerProposition3 = new PublicKey25519Proposition(BytesUtils.fromHexString("3333334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
      val vrfPublicKey3 = new VrfPublicKey(BytesUtils.fromHexString("333375fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes
      StakeStorage.addForger(view, blockSignerProposition3, vrfPublicKey3, 0, Address.ZERO, epochNumber2, delegator1, stakeAmount2)

      result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      listOfForgers = result.forgers
      assertEquals(3, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)

      assertEquals(blockSignerProposition2, listOfForgers(1).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey2, listOfForgers(1).forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers(1).rewardAddress.address())
      assertEquals(rewardShare2, listOfForgers(1).rewardShare)

      assertEquals(blockSignerProposition3, listOfForgers(2).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey3, listOfForgers(2).forgerPublicKeys.vrfPublicKey)
      assertEquals(Address.ZERO, listOfForgers(2).rewardAddress.address())
      assertEquals(0, listOfForgers(2).rewardShare)

      assertEquals(-1, result.nextStartPos)
    }
  }


  @Test
  def testGetPagedListOfForgers(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)

      val result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      assertTrue(result.forgers.isEmpty)
      assertEquals(-1, result.nextStartPos)

      assertThrows[IllegalArgumentException] {
        StakeStorage.getPagedListOfForgers(view, 0, 0)
      }

      assertThrows[IllegalArgumentException] {
        StakeStorage.getPagedListOfForgers(view, 1, 10)
      }

      assertThrows[IllegalArgumentException] {
        StakeStorage.getPagedListOfForgers(view, 1, -10)
      }

      assertThrows[IllegalArgumentException] {
        StakeStorage.getPagedListOfForgers(view, -1, 10)
      }

      val numOfForgers = 100
      val listOfExpectedData = (0 until numOfForgers).map {idx =>
        val postfix = f"$idx%03d"
        val blockSignerProposition = new PublicKey25519Proposition(BytesUtils.fromHexString(s"1122334455667788112233445566778811223344556677881122334455667$postfix")) // 32 bytes
        val vrfPublicKey = new VrfPublicKey(BytesUtils.fromHexString(s"d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1$postfix")) // 33 bytes

        val delegator = new Address(s"0xaaa00001230000000000deadbeefaaaa22222$postfix")
        val epochNumber = 135869 + idx

        val rewardAddress = new Address(s"0xaaa0000123000000000011112222aaaa22222$postfix")
        val rewardShare = idx + 1
        val stakeAmount = ZenWeiConverter.convertZenniesToWei(idx + 1)

        StakeStorage.addForger(view, blockSignerProposition, vrfPublicKey,rewardShare, rewardAddress, epochNumber, delegator, stakeAmount)


        val forgerKey = ForgerKey(blockSignerProposition, vrfPublicKey)
        val delegatorList = DelegatorList(forgerKey)
        assertEquals(1, delegatorList.getSize(view))
        assertEquals(delegator, delegatorList.getDelegatorAt(view, 0))

        val forgerHistory = ForgerStakeHistory(forgerKey)
        assertEquals(1, forgerHistory.getSize(view))
        assertEquals(epochNumber, forgerHistory.getCheckpoint(view, 0).fromEpochNumber)
        assertEquals(stakeAmount, forgerHistory.getCheckpoint(view, 0).stakedAmount)
        assertEquals(stakeAmount, forgerHistory.getLatestAmount(view))

        val stakeHistory = StakeHistory(forgerKey, delegator)
        assertEquals(1, stakeHistory.getSize(view))
        assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
        assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)
        assertEquals(stakeAmount, stakeHistory.getLatestAmount(view))

        val forgerList = DelegatorListOfForgerKeys(delegator)
        assertEquals(1, forgerList.getSize(view))
        assertEquals(forgerKey, forgerList.getForgerKey(view, 0))

        (blockSignerProposition, vrfPublicKey, rewardAddress, rewardShare)
      }

      val pageSize = 11
      var continue = true
      var listOfResults = Seq.empty[ForgerDetails]
      var startPos = 0

      while (continue) {
        val result = StakeStorage.getPagedListOfForgers(view, startPos, pageSize)
        listOfResults = listOfResults ++ result.forgers
        continue = if (result.nextStartPos != -1){
          assertEquals(pageSize, result.forgers.size)
          true
        }
        else
          false
        startPos = result.nextStartPos
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

  @Test
  def testAddStake(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)

      // Check that we don't have any forger yet
      var result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      assertTrue(result.forgers.isEmpty)

      val epochNumber1 = 135869
      val stakeAmount1 = BigInteger.valueOf(300)

      // Add stake to a non-registered forger. it should fail
      var ex = intercept[ExecutionRevertedException] {
        StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)
      }
      assertEquals("Forger doesn't exist.", ex.getMessage)

      // Register the forger and try again adding stakes
      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val initialEpochNumber = 125869
      val initialStakeAmount = BigInteger.TEN
      StakeStorage.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, initialEpochNumber, delegator1, initialStakeAmount)

      result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      var listOfForgers = result.forgers
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result.nextStartPos)

      var listOfExpectedForger1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil
      var listOfExpectedD1F1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil

      // Add stake using the same delegator
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)

      val forger1DelegatorList = DelegatorList(forger1Key)
      assertEquals(1, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0))

      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount1))
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedD1F1Checkpoints.last.stakedAmount.add(stakeAmount1))

      val forger1History = ForgerStakeHistory(forger1Key)
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeHistory_d1_f1 = StakeHistory(forger1Key, delegator1)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      val delegator1ForgerList = DelegatorListOfForgerKeys(delegator1)
      assertEquals(1, delegator1ForgerList.getSize(view))
      assertEquals(forger1Key, delegator1ForgerList.getForgerKey(view, 0))

      // Add another stake from the same delegator in the same consensus epoch

      val stakeAmount2 = BigInteger.valueOf(1000)
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount2)

      // delegator list shouldn't change
      assertEquals(1, forger1DelegatorList.getSize(view))

      // ForgerHistory size should remain the same, but the value of the last checkpoint should change
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints.updated(1, StakeCheckpoint(epochNumber1,
        listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount2)))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      // StakeHistory size should remain the same, but the value of the last checkpoint should change
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints.updated(1, StakeCheckpoint(epochNumber1,
        listOfExpectedD1F1Checkpoints.last.stakedAmount.add(stakeAmount2)))
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // forger list of first delegator shouldn't change
      assertEquals(1, delegator1ForgerList.getSize(view))
      assertEquals(forger1Key, delegator1ForgerList.getForgerKey(view, 0))

      // Add another stake from the another delegator in the same consensus epoch
      val stakeAmount_2_1 = BigInteger.valueOf(753536)

      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator2, stakeAmount_2_1)

      //Check delegator list
      assertEquals(2, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0))
      assertEquals(delegator2, forger1DelegatorList.getDelegatorAt(view, 1))

      // ForgerHistory size should remain the same, but the value of the last checkpoint should change
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints.updated(1, StakeCheckpoint(epochNumber1, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount_2_1)))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeHistory_d2_f1 = StakeHistory(forger1Key, delegator2)
      var listOfExpectedD2F1Checkpoints = StakeCheckpoint(epochNumber1, stakeAmount_2_1) :: Nil
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Check delegator2 forger list
      val delegator2ForgerList = DelegatorListOfForgerKeys(delegator2)
      assertEquals(1, delegator2ForgerList.getSize(view))
      assertEquals(forger1Key, delegator1ForgerList.getForgerKey(view, 0))

      // Add another stake from the second delegator in a different consensus epoch
      val stakeAmount_2_2 = BigInteger.valueOf(22356)
      val epochNumber2 = epochNumber1 + 10
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber2, delegator2, stakeAmount_2_2)

      //Check delegator list, shouldn't change
      assertEquals(2, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0))
      assertEquals(delegator2, forger1DelegatorList.getDelegatorAt(view, 1))

      // Check ForgerHistory, we should have 3 checkpoints
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber2, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount_2_2))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      // Check delegator1 stake history, shouldn't change
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Check delegator2 stake history, we should have 2 checkpoints
      listOfExpectedD2F1Checkpoints = listOfExpectedD2F1Checkpoints :+ StakeCheckpoint(epochNumber2, listOfExpectedD2F1Checkpoints.last.stakedAmount.add(stakeAmount_2_2))
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Check delegator1 forger list
      assertEquals(1, delegator1ForgerList.getSize(view))
      assertEquals(forger1Key, delegator1ForgerList.getForgerKey(view, 0))

      // Check delegator2 forger list
      assertEquals(1, delegator2ForgerList.getSize(view))
      assertEquals(forger1Key, delegator2ForgerList.getForgerKey(view, 0))

      // Register another forger with delegator2
      val epochNumber3 = epochNumber2 + 65

      StakeStorage.addForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare, rewardAddress, epochNumber3, delegator2, initialStakeAmount)

      result = StakeStorage.getPagedListOfForgers(view, 0, 10)

      listOfForgers = result.forgers
      assertEquals(2, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)

      assertEquals(blockSignerProposition2, listOfForgers(1).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey2, listOfForgers(1).forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers(1).rewardAddress.address())
      assertEquals(rewardShare, listOfForgers(1).rewardShare)
      assertEquals(-1, result.nextStartPos)

      // Check delegator2 forger list
      assertEquals(2, delegator2ForgerList.getSize(view))
      assertEquals(forger1Key, delegator2ForgerList.getForgerKey(view, 0))
      assertEquals(forger2Key, delegator2ForgerList.getForgerKey(view, 1))

      // Check delegator2/forger1 stake history, shouldn't change
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Check delegator2/forger2 stake history
      val stakeHistory_d2_f2 = StakeHistory(forger2Key, delegator2)
      val listOfExpectedD2F2Checkpoints = StakeCheckpoint(epochNumber3, initialStakeAmount) :: Nil
      checkStakeHistory(view, stakeHistory_d2_f2, listOfExpectedD2F2Checkpoints)

      // Add stake using a delegator address all upper case. It should be treated as the same delegator.

      val epochNumber4 = epochNumber3 + 10
      val DELEGATOR1 = new Address("0x" + delegator1.toStringNoPrefix.toUpperCase)
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber4, DELEGATOR1, stakeAmount1)

      assertEquals(2, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0))
      assertEquals(delegator2, forger1DelegatorList.getDelegatorAt(view, 1))

      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber4, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount1))
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber4, listOfExpectedD1F1Checkpoints.last.stakedAmount.add(stakeAmount1))

      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      assertArrayEquals(delegator1ForgerList.keySeed, DelegatorListOfForgerKeys(DELEGATOR1).keySeed)

      // Test with epoch before the last one. It should fail.
      val badEpoch = epochNumber2 - 10
      ex = intercept[ExecutionRevertedException] {
        StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, badEpoch, delegator2, stakeAmount_2_2)
      }
      assertEquals(s"Epoch is in the past: epoch $badEpoch, last epoch: $epochNumber4", ex.getMessage)

    }
  }

  @Test
  def testRemoveStake(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)

      // Check that we don't have any forger yet
      var result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      assertTrue(result.forgers.isEmpty)

      val epochNumber1 = 135869
      val stakeAmount1 = BigInteger.valueOf(5358869)

      // Remove stake from a non-registered forger, it should fail
      var ex = intercept[ExecutionRevertedException] {
        StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)
      }
      assertEquals("Forger doesn't exist.", ex.getMessage)

      // Register the forger and try again removing stakes
      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val initialEpochNumber = 125869
      val initialStakeAmount = ZenWeiConverter.MAX_MONEY_IN_WEI

      StakeStorage.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, initialEpochNumber, delegator1, initialStakeAmount)
      result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      val listOfForgers = result.forgers
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result.nextStartPos)

      var listOfExpectedForger1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil
      var listOfExpectedD1F1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil

      // Remove stake using the same delegator
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)

      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(stakeAmount1))
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedD1F1Checkpoints.last.stakedAmount.subtract(stakeAmount1))

      val forger1DelegatorList = DelegatorList(forger1Key)
      assertEquals(1, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0))

      val forger1History = ForgerStakeHistory(forger1Key)
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeHistory_d1_f1 = StakeHistory(forger1Key, delegator1)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      val delegator1ForgerList = DelegatorListOfForgerKeys(delegator1)
      assertEquals(1, delegator1ForgerList.getSize(view))
      assertEquals(forger1Key, delegator1ForgerList.getForgerKey(view, 0))

      // Remove another stake from the same delegator in the same consensus epoch, using a delegator address all upper case
      // It should be treated as the same delegator.

      val DELEGATOR1 = new Address("0x" + delegator1.toStringNoPrefix.toUpperCase)
      val stakeAmount2 = BigInteger.valueOf(1000)
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, DELEGATOR1, stakeAmount2)

      // delegator list shouldn't change
      assertEquals(1, forger1DelegatorList.getSize(view))

      // ForgerHistory size should remain the same, but the value of the last checkpoint should change
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints.updated(1, StakeCheckpoint(epochNumber1, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(stakeAmount2)))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      // StakeHistory size should remain the same, but the value of the last checkpoint should change
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints.updated(1, StakeCheckpoint(epochNumber1, listOfExpectedD1F1Checkpoints.last.stakedAmount.subtract(stakeAmount2)))
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // forger list of first delegator shouldn't change
      assertEquals(1, delegator1ForgerList.getSize(view))
      assertEquals(forger1Key, delegator1ForgerList.getForgerKey(view, 0))

      // Remove stake from the another delegator. It should fail
      val stakeAmount_2_1 = BigInteger.valueOf(753536)

      assertThrows[ExecutionRevertedException] {
        StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator2, stakeAmount_2_1)
      }

      //Add some stake for delegator 2
      val epochNumber2 = epochNumber1 + 10
      val stakeAmount_2_2 = stakeAmount_2_1.multiply(BigInteger.TEN)
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber2, delegator2, stakeAmount_2_2)

      //Check delegator list
      assertEquals(2, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0))
      assertEquals(delegator2, forger1DelegatorList.getDelegatorAt(view, 1))

      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber2, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount_2_2))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeHistory_d2_f1 = StakeHistory(forger1Key, delegator2)
      var listOfExpectedD2F1Checkpoints = StakeCheckpoint(epochNumber2, stakeAmount_2_2) :: Nil
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Check delegator2 forger list
      val delegator2ForgerList = DelegatorListOfForgerKeys(delegator2)
      assertEquals(1, delegator2ForgerList.getSize(view))
      assertEquals(forger1Key, delegator1ForgerList.getForgerKey(view, 0))

      // Check delegator1/forger1 stake, it shouldn't change
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Remove stake from delegator2 from another epoch
      val epochNumber3 = epochNumber2 + 4756
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber3, delegator2, stakeAmount_2_1)

      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber3, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(stakeAmount_2_1))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD2F1Checkpoints = listOfExpectedD2F1Checkpoints :+ StakeCheckpoint(epochNumber3, listOfExpectedD2F1Checkpoints.last.stakedAmount.subtract(stakeAmount_2_1))
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Remove stake from delegator1 from same epoch
      val stakeAmount3 = BigInteger.valueOf(1000)
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber3, delegator1, stakeAmount3)

      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints.updated(listOfExpectedForger1Checkpoints.size - 1,
        StakeCheckpoint(epochNumber3, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(stakeAmount3)))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber3, listOfExpectedD1F1Checkpoints.last.stakedAmount.subtract(stakeAmount3))
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Try to remove stake with epoch before the last one. It should fail.
      val badEpoch = epochNumber3 - 10
      ex = intercept[ExecutionRevertedException] {
        StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, badEpoch, delegator2, stakeAmount_2_1)
      }
      assertEquals(s"Epoch is in the past: epoch $badEpoch, last epoch: $epochNumber3", ex.getMessage)

      // Try to remove more stake than available. It should fail
      val epochNumber4 = epochNumber3 + 44
      assertThrows[ExecutionRevertedException] {
        StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber4, delegator1, listOfExpectedD1F1Checkpoints.last.stakedAmount.add(BigInteger.ONE))
      }

      // Try to remove all delegator1 stake. History should remain available
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber4, delegator1, listOfExpectedD1F1Checkpoints.last.stakedAmount)
      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber4, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(listOfExpectedD1F1Checkpoints.last.stakedAmount))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber4, BigInteger.ZERO)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Try to remove all delegator2 stake. History should remain available
      val epochNumber5 = epochNumber4 + 12
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber5, delegator2, listOfExpectedD2F1Checkpoints.last.stakedAmount)
      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber5, BigInteger.ZERO)
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD2F1Checkpoints = listOfExpectedD2F1Checkpoints :+ StakeCheckpoint(epochNumber5, BigInteger.ZERO)
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

    }
  }

  @Test
  def testDuplicateCheckpoints(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)

      // Register the forger and try again removing stakes
      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val initialEpochNumber = 125869
      val initialStakeAmount = BigInteger.valueOf(5358869)

      StakeStorage.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, initialEpochNumber, delegator1, initialStakeAmount)

      // Remove and then add again the same amount in the same epoch of the registration. Check everything works

      val stakeAmount1 = BigInteger.valueOf(5358869)

      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, initialEpochNumber, delegator1, stakeAmount1)
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, initialEpochNumber, delegator1, stakeAmount1)

      var listOfExpectedForger1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil
      var listOfExpectedD1F1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil

      val forger1History = ForgerStakeHistory(forger1Key)
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeHistory_d1_f1 = StakeHistory(forger1Key, delegator1)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Let's do the same but in another epoch. The stake history should not becomes bigger

      val epochNumber1 = 135869
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)

      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Add again another stake in the same epoch. Check that we have a new checkpoint
      val stakeAmount2 = BigInteger.valueOf(5555555)
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount2)

      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber1, initialStakeAmount.add(stakeAmount2))
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber1, initialStakeAmount.add(stakeAmount2))

      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

    }
  }


  @Test
  def testGetAllForgerStakes(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)

      var listOfStakes = StakeStorage.getAllForgerStakes(view)
      assertTrue(listOfStakes.isEmpty)

      val rewardAddress = new Address(s"0xaaa0000123000000000011112222aaaa22222111")
      val rewardShare = 90
      var epochNumber = 135869
      val stakeAmount1 = BigInteger.valueOf(10000000000L)
      StakeStorage.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, epochNumber, delegator1, stakeAmount1)
      var listOfExpectedData = ForgerStakeData(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1), new AddressProposition(delegator1), stakeAmount1) :: Nil

      listOfStakes = StakeStorage.getAllForgerStakes(view)
      assertEquals(listOfExpectedData, listOfStakes)

      epochNumber += 10

      val stakeAmount2 = BigInteger.valueOf(20000000000L)
      StakeStorage.addForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare, rewardAddress, epochNumber, delegator1, stakeAmount2)
      listOfExpectedData = listOfExpectedData :+ ForgerStakeData(ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2), new AddressProposition(delegator1), stakeAmount2)

      listOfStakes = StakeStorage.getAllForgerStakes(view)
      assertEquals(listOfExpectedData, listOfStakes)

      epochNumber += 10
      StakeStorage.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber, delegator1, stakeAmount1)
      listOfExpectedData = listOfExpectedData.updated(0, ForgerStakeData(ForgerPublicKeys(blockSignerProposition1, vrfPublicKey1), new AddressProposition(delegator1), stakeAmount1.add(stakeAmount1)))

      listOfStakes = StakeStorage.getAllForgerStakes(view)
      assertEquals(listOfExpectedData, listOfStakes)

      epochNumber += 10
      StakeStorage.addStake(view, blockSignerProposition2, vrfPublicKey2, epochNumber, delegator2, stakeAmount1)
      listOfExpectedData = listOfExpectedData :+ ForgerStakeData(ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2), new AddressProposition(delegator2), stakeAmount1)
      val delegator3 = new Address("0xaaabbbb1230000000000aaaaaaabbbbb22222333")
      StakeStorage.addStake(view, blockSignerProposition2, vrfPublicKey2, epochNumber, delegator3, stakeAmount2)
      listOfExpectedData = listOfExpectedData :+ ForgerStakeData(ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2), new AddressProposition(delegator3), stakeAmount2)

      listOfStakes = StakeStorage.getAllForgerStakes(view)
      assertEquals(listOfExpectedData, listOfStakes)

      //  Remove all forger2/delegator3 stakes. forger2/delegator3 stake shouldn't be in the resulting list
      epochNumber += 10
      StakeStorage.removeStake(view, blockSignerProposition2, vrfPublicKey2, epochNumber, delegator3, stakeAmount2)
      listOfExpectedData = listOfExpectedData.slice(0, listOfExpectedData.size - 1)
      listOfStakes = StakeStorage.getAllForgerStakes(view)
      assertEquals(listOfExpectedData, listOfStakes)


      //  Remove all forger1 stakes. forger1 shouldn't be in the resulting list
      epochNumber += 10
      StakeStorage.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber, delegator1, stakeAmount1.add(stakeAmount1))
      listOfExpectedData = listOfExpectedData.slice(1, listOfExpectedData.size)
      listOfStakes = StakeStorage.getAllForgerStakes(view)
      assertEquals(listOfExpectedData, listOfStakes)

      // TODO - Go on with test
      val qqq = getPagedForgersStakesByForger(view, ForgerPublicKeys(blockSignerProposition2, vrfPublicKey2), 0, 5)
      qqq.toString()
    }
  }


  @Test
  def testUpdateForger(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V2_SMART_CONTRACT_ADDRESS)
      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93

      //  Try to update a non existing forger. It should fail
      var ex = intercept[ExecutionRevertedException] {
        StakeStorage.updateForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress)
      }
      assertEquals("Forger doesn't exist.", ex.getMessage)

      // Try to update a forger that didn't specify rewardAddress and rewardShare during registration. It should work
      val epochNumber = 135869
      val stakeAmount = BigInteger.valueOf(20000000000L)
      StakeStorage.addForger(view, blockSignerProposition1, vrfPublicKey1, 0, Address.ZERO, epochNumber, delegator1, stakeAmount)
      var result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      var listOfForgers = result.forgers
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(Address.ZERO, listOfForgers.head.rewardAddress.address())
      assertEquals(0, listOfForgers.head.rewardShare)

      // Change the reward address and share
      StakeStorage.updateForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress)
      result = StakeStorage.getPagedListOfForgers(view, 0, 10)
      listOfForgers = result.forgers
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)

      // Try to change again rewardAddress and rewardShare. it should fail.
      val rewardAddress2 = new Address("0xaaa0000123000000000011112222aaaa2222aaa2")
      val rewardShare2 = 23

      ex = intercept[ExecutionRevertedException] {
        StakeStorage.updateForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare2, rewardAddress2)
      }
      assertEquals("Forger has already set reward share and reward address.", ex.getMessage)

      // Try to update a forger that didn't specify rewardAddress and rewardShare during registration. It should work
      StakeStorage.addForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare2, rewardAddress2, epochNumber, delegator2, stakeAmount)

      // Change the reward address and share
      ex = intercept[ExecutionRevertedException] {
        StakeStorage.updateForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare, rewardAddress)
      }
      assertEquals("Forger has already set reward share and reward address.", ex.getMessage)


    }
  }



  def checkStakeHistory(view: BaseAccountStateView, history: BaseStakeHistory, expectedCheckpoints: Seq[StakeCheckpoint]): Unit = {
    assertEquals(expectedCheckpoints.size, history.getSize(view))
    expectedCheckpoints.indices.foreach { idx =>
      assertEquals(expectedCheckpoints(idx), history.getCheckpoint(view, idx))
    }
    expectedCheckpoints.lastOption.foreach(checkpoint =>  assertEquals(checkpoint.stakedAmount, history.getLatestAmount(view)))
  }


}
