package io.horizen.account.state

import io.horizen.account.proposition.AddressProposition
import io.horizen.account.state.ForgerStakeStorageV3.{DelegatorList, ForgerList, StakeHistory}
import io.horizen.account.utils.WellKnownAddresses.FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS
import io.horizen.account.utils.ZenWeiConverter
import io.horizen.evm.Address
import io.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import io.horizen.utils.BytesUtils
import org.junit.Assert._
import org.junit.Test
import org.scalatestplus.junit.JUnitSuite

import java.math.BigInteger

class ForgerStakeStorageV3Test
  extends JUnitSuite
  with MessageProcessorFixture {

  val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
  val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

  val blockSignerProposition2 = new PublicKey25519Proposition(BytesUtils.fromHexString("4455334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
  val vrfPublicKey2 = new VrfPublicKey(BytesUtils.fromHexString("445575fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

  val delegator1 = new Address("0xaaa00001230000000000deadbeefaaaa22222222")
  val delegator2 = new Address("0xaaa00001230000000000aaaaaaabbbbb22222222")

  @Test
  def testAddForger(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS)

      var result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)

      assertTrue(result._2.isEmpty)
      assertEquals(-1, result._1)
      val epochNumber = 135869

      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val stakeAmount = BigInteger.TEN

      ForgerStakeStorageV3.addForger(view, blockSignerProposition1, vrfPublicKey1,rewardShare, rewardAddress, epochNumber, delegator1, stakeAmount)

      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)

      var listOfForgers = result._2
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result._1)

      val forgerKey = ForgerStakeStorageV3.getForgerKey(blockSignerProposition1, vrfPublicKey1)
      val delegatorList = DelegatorList(forgerKey)
      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator1, delegatorList.getDelegatorAt(view, 0).address())

      val forgerHistory = StakeHistory(forgerKey)
      assertEquals(1, forgerHistory.getSize(view))
      assertEquals(epochNumber, forgerHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, forgerHistory.getCheckpoint(view, 0).stakedAmount)

      val stakeKey = ForgerStakeStorageV3.getStakeKey(forgerKey, delegator1)
      val stakeHistory = StakeHistory(stakeKey)
      assertEquals(1, stakeHistory.getSize(view))
      assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)

      val forgerList = ForgerList(new AddressProposition(delegator1))
      assertEquals(1, forgerList.getSize(view))
      assertArrayEquals(forgerKey, forgerList.getValue(view, 0))


      //  Try to register twice the same forger. It should fail
      val ex = intercept[ExecutionRevertedException] {
        ForgerStakeStorageV3.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, epochNumber, delegator1, stakeAmount)
      }
      assertEquals("Forger already registered.", ex.getMessage)

      // Try to register another forger with the same delegator and the same rewardAddress
      val rewardShare2 = 87
      val stakeAmount2 = ZenWeiConverter.MAX_MONEY_IN_WEI
      val epochNumber2 = 444555444
      ForgerStakeStorageV3.addForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare2, rewardAddress, epochNumber2, delegator1, stakeAmount2)

      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      listOfForgers = result._2
      assertEquals(2, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)

      assertEquals(blockSignerProposition2, listOfForgers(1).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey2, listOfForgers(1).forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers(1).rewardAddress.address())
      assertEquals(rewardShare2, listOfForgers(1).rewardShare)
      assertEquals(-1, result._1)

      // Check that the first forger was not changed
      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator1, delegatorList.getDelegatorAt(view, 0).address())

      assertEquals(1, forgerHistory.getSize(view))
      assertEquals(epochNumber, forgerHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, forgerHistory.getCheckpoint(view, 0).stakedAmount)

      assertEquals(1, stakeHistory.getSize(view))
      assertEquals(epochNumber, stakeHistory.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount, stakeHistory.getCheckpoint(view, 0).stakedAmount)


      assertEquals(1, delegatorList.getSize(view))
      assertEquals(delegator1, delegatorList.getDelegatorAt(view, 0).address())

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
      assertEquals(delegator1, delegatorList2.getDelegatorAt(view, 0).address())

      val forgerHistory2 = StakeHistory(forgerKey2)
      assertEquals(1, forgerHistory2.getSize(view))
      assertEquals(epochNumber2, forgerHistory2.getCheckpoint(view, 0).fromEpochNumber)
      assertEquals(stakeAmount2, forgerHistory2.getCheckpoint(view, 0).stakedAmount)

      val stakeKey2 = ForgerStakeStorageV3.getStakeKey(forgerKey2, delegator1)
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

      val (nextIdx, forgers) = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      assertTrue(forgers.isEmpty)
      assertEquals(-1, nextIdx)

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
      val listOfExpectedData = (0 until numOfForgers).map {idx =>
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

        val stakeKey = ForgerStakeStorageV3.getStakeKey(forgerKey, delegator)
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
        val (nextIdx, forgers) = ForgerStakeStorageV3.getPagedListOfForgers(view, startPos, pageSize)
        listOfResults = listOfResults ++ forgers
        continue = if (nextIdx != -1){
          assertEquals(pageSize, forgers.size)
          true
        }
        else
          false
        startPos = nextIdx
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

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS)

      // Check that we don't have any forger yet
      var result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      assertTrue(result._2.isEmpty)

      val epochNumber1 = 135869
      val stakeAmount1 = BigInteger.valueOf(300)

      // Add stake to a non-registered forger. it should fail
      val ex = intercept[ExecutionRevertedException] {
        ForgerStakeStorageV3.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)
      }
      assertEquals("Forger doesn't exist.", ex.getMessage)

      // Register the forger and try again adding stakes
      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val initialEpochNumber = 125869
      val initialStakeAmount = BigInteger.TEN
      ForgerStakeStorageV3.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, initialEpochNumber, delegator1, initialStakeAmount)

      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      var listOfForgers = result._2
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result._1)

      var listOfExpectedForger1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil
      var listOfExpectedD1F1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil

      // Add stake using the same delegator
      ForgerStakeStorageV3.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)

      val forger1Key = ForgerStakeStorageV3.getForgerKey(blockSignerProposition1, vrfPublicKey1)
      val forger1DelegatorList = DelegatorList(forger1Key)
      assertEquals(1, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0).address())

      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount1))
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedD1F1Checkpoints.last.stakedAmount.add(stakeAmount1))

      val forger1History = StakeHistory(forger1Key)
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeKey_d1_f1 = ForgerStakeStorageV3.getStakeKey(forger1Key, delegator1)
      val stakeHistory_d1_f1 = StakeHistory(stakeKey_d1_f1)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      val delegator1ForgerList = ForgerList(new AddressProposition(delegator1))
      assertEquals(1, delegator1ForgerList.getSize(view))
      assertArrayEquals(forger1Key, delegator1ForgerList.getValue(view, 0))

      // Add another stake from the same delegator in the same consensus epoch

      val stakeAmount2 = BigInteger.valueOf(1000)
      ForgerStakeStorageV3.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount2)

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
      assertArrayEquals(forger1Key, delegator1ForgerList.getValue(view, 0))

      // Add another stake from the another delegator in the same consensus epoch
      val stakeAmount_2_1 = BigInteger.valueOf(753536)

      ForgerStakeStorageV3.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator2, stakeAmount_2_1)

      //Check delegator list
      assertEquals(2, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0).address())
      assertEquals(delegator2, forger1DelegatorList.getDelegatorAt(view, 1).address())

      // ForgerHistory size should remain the same, but the value of the last checkpoint should change
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints.updated(1, StakeCheckpoint(epochNumber1, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount_2_1)))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeKey_d2_f1 = ForgerStakeStorageV3.getStakeKey(forger1Key, delegator2)
      val stakeHistory_d2_f1 = StakeHistory(stakeKey_d2_f1)
      var listOfExpectedD2F1Checkpoints = StakeCheckpoint(epochNumber1, stakeAmount_2_1) :: Nil
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Check delegator2 forger list
      val delegator2ForgerList = ForgerList(new AddressProposition(delegator2))
      assertEquals(1, delegator2ForgerList.getSize(view))
      assertArrayEquals(forger1Key, delegator1ForgerList.getValue(view, 0))

      // Add another stake from the second delegator in a different consensus epoch
      val stakeAmount_2_2 = BigInteger.valueOf(22356)
      val epochNumber2 = epochNumber1 + 10
      ForgerStakeStorageV3.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber2, delegator2, stakeAmount_2_2)

      //Check delegator list, shouldn't change
      assertEquals(2, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0).address())
      assertEquals(delegator2, forger1DelegatorList.getDelegatorAt(view, 1).address())

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
      assertArrayEquals(forger1Key, delegator1ForgerList.getValue(view, 0))

      // Check delegator2 forger list
      assertEquals(1, delegator2ForgerList.getSize(view))
      assertArrayEquals(forger1Key, delegator2ForgerList.getValue(view, 0))

      // Register another forger with delegator2
      val epochNumber3 = epochNumber2 + 65

      ForgerStakeStorageV3.addForger(view, blockSignerProposition2, vrfPublicKey2, rewardShare, rewardAddress, epochNumber3, delegator2, initialStakeAmount)

      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)

      listOfForgers = result._2
      assertEquals(2, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)

      assertEquals(blockSignerProposition2, listOfForgers(1).forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey2, listOfForgers(1).forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers(1).rewardAddress.address())
      assertEquals(rewardShare, listOfForgers(1).rewardShare)
      assertEquals(-1, result._1)

      // Check delegator2 forger list
      val forger2Key = ForgerStakeStorageV3.getForgerKey(blockSignerProposition2, vrfPublicKey2)
      assertEquals(2, delegator2ForgerList.getSize(view))
      assertArrayEquals(forger1Key, delegator2ForgerList.getValue(view, 0))
      assertArrayEquals(forger2Key, delegator2ForgerList.getValue(view, 1))

      // Check delegator2/forger1 stake history, shouldn't change
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Check delegator2/forger2 stake history
      val stakeKey_d2_f2 = ForgerStakeStorageV3.getStakeKey(forger2Key, delegator2)
      val stakeHistory_d2_f2 = StakeHistory(stakeKey_d2_f2)
      val listOfExpectedD2F2Checkpoints = StakeCheckpoint(epochNumber3, initialStakeAmount) :: Nil
      checkStakeHistory(view, stakeHistory_d2_f2, listOfExpectedD2F2Checkpoints)
    }
  }

  @Test
  def testRemoveStake(): Unit = {
    usingView { view =>

      createSenderAccount(view, BigInteger.TEN, FORGER_STAKE_V3_SMART_CONTRACT_ADDRESS)

      // Check that we don't have any forger yet
      var result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      assertTrue(result._2.isEmpty)


      val blockSignerProposition1 = new PublicKey25519Proposition(BytesUtils.fromHexString("1122334455667788112233445566778811223344556677881122334455667788")) // 32 bytes
      val vrfPublicKey1 = new VrfPublicKey(BytesUtils.fromHexString("d6b775fd4cefc7446236683fdde9d0464bba43cc565fa066b0b3ed1b888b9d1180")) // 33 bytes

      val delegator1 = new Address("0xaaa00001230000000000deadbeefaaaa22222222")
      val epochNumber1 = 135869
      val stakeAmount1 = BigInteger.valueOf(5358869)

      // Remove stake from a non-registered forger, it should fail
      val ex = intercept[ExecutionRevertedException] {
        ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)
      }
      assertEquals("Forger doesn't exist.", ex.getMessage)

      // Register the forger and try again removing stakes
      val rewardAddress = new Address("0xaaa0000123000000000011112222aaaa22222222")
      val rewardShare = 93
      val initialEpochNumber = 125869
      val initialStakeAmount = ZenWeiConverter.MAX_MONEY_IN_WEI

      ForgerStakeStorageV3.addForger(view, blockSignerProposition1, vrfPublicKey1, rewardShare, rewardAddress, initialEpochNumber, delegator1, initialStakeAmount)
      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)
      val listOfForgers = result._2
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition1, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey1, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result._1)

      var listOfExpectedForger1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil
      var listOfExpectedD1F1Checkpoints = StakeCheckpoint(initialEpochNumber, initialStakeAmount) :: Nil

      // Remove stake using the same delegator
      ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount1)

      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(stakeAmount1))
      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber1, listOfExpectedD1F1Checkpoints.last.stakedAmount.subtract(stakeAmount1))

      val forger1Key = ForgerStakeStorageV3.getForgerKey(blockSignerProposition1, vrfPublicKey1)
      val forger1DelegatorList = DelegatorList(forger1Key)
      assertEquals(1, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0).address())

      val forger1History = StakeHistory(forger1Key)
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeKey_d1_f1 = ForgerStakeStorageV3.getStakeKey(forger1Key, delegator1)
      val stakeHistory_d1_f1 = StakeHistory(stakeKey_d1_f1)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      val delegator1ForgerList = ForgerList(new AddressProposition(delegator1))
      assertEquals(1, delegator1ForgerList.getSize(view))
      assertArrayEquals(forger1Key, delegator1ForgerList.getValue(view, 0))

      // Remove another stake from the same delegator in the same consensus epoch

      val stakeAmount2 = BigInteger.valueOf(1000)
      ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator1, stakeAmount2)

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
      assertArrayEquals(forger1Key, delegator1ForgerList.getValue(view, 0))

      // Remove stake from the another delegator. It should fail
      val stakeAmount_2_1 = BigInteger.valueOf(753536)

      assertThrows[ExecutionRevertedException] {
        ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber1, delegator2, stakeAmount_2_1)
      }

      //Add some stake for delegator 2
      val epochNumber2 = epochNumber1 + 10
      val stakeAmount_2_2 = stakeAmount_2_1.multiply(BigInteger.TEN)
      ForgerStakeStorageV3.addStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber2, delegator2, stakeAmount_2_2)

      //Check delegator list
      assertEquals(2, forger1DelegatorList.getSize(view))
      assertEquals(delegator1, forger1DelegatorList.getDelegatorAt(view, 0).address())
      assertEquals(delegator2, forger1DelegatorList.getDelegatorAt(view, 1).address())

      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber2, listOfExpectedForger1Checkpoints.last.stakedAmount.add(stakeAmount_2_2))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      val stakeKey_d2_f1 = ForgerStakeStorageV3.getStakeKey(forger1Key, delegator2)
      val stakeHistory_d2_f1 = StakeHistory(stakeKey_d2_f1)
      var listOfExpectedD2F1Checkpoints = StakeCheckpoint(epochNumber2, stakeAmount_2_2) :: Nil
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Check delegator2 forger list
      val delegator2ForgerList = ForgerList(new AddressProposition(delegator2))
      assertEquals(1, delegator2ForgerList.getSize(view))
      assertArrayEquals(forger1Key, delegator1ForgerList.getValue(view, 0))

      // Check delegator1/forger1 stake, it shouldn't change
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Remove stake from delegator2 from another epoch
      val epochNumber3 = epochNumber2 + 4756
      ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber3, delegator2, stakeAmount_2_1)

      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber3, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(stakeAmount_2_1))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD2F1Checkpoints = listOfExpectedD2F1Checkpoints :+ StakeCheckpoint(epochNumber3, listOfExpectedD2F1Checkpoints.last.stakedAmount.subtract(stakeAmount_2_1))
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

      // Remove stake from delegator1 from same epoch
      val stakeAmount3 = BigInteger.valueOf(1000)
      ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber3, delegator1, stakeAmount3)

      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints.updated(listOfExpectedForger1Checkpoints.size - 1,
        StakeCheckpoint(epochNumber3, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(stakeAmount3)))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber3, listOfExpectedD1F1Checkpoints.last.stakedAmount.subtract(stakeAmount3))
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Try to remove more stake than available. It should fail
      val epochNumber4 = epochNumber3 + 44
      assertThrows[ExecutionRevertedException] {
        ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber4, delegator1, listOfExpectedD1F1Checkpoints.last.stakedAmount.add(BigInteger.ONE))
      }

      // Try to remove all delegator1 stake. History should remain available
      ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber4, delegator1, listOfExpectedD1F1Checkpoints.last.stakedAmount)
      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber4, listOfExpectedForger1Checkpoints.last.stakedAmount.subtract(listOfExpectedD1F1Checkpoints.last.stakedAmount))
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD1F1Checkpoints = listOfExpectedD1F1Checkpoints :+ StakeCheckpoint(epochNumber4, BigInteger.ZERO)
      checkStakeHistory(view, stakeHistory_d1_f1, listOfExpectedD1F1Checkpoints)

      // Try to remove all delegator2 stake. History should remain available
      val epochNumber5 = epochNumber4 + 12
      ForgerStakeStorageV3.removeStake(view, blockSignerProposition1, vrfPublicKey1, epochNumber5, delegator2, listOfExpectedD2F1Checkpoints.last.stakedAmount)
      // Check ForgerHistory
      listOfExpectedForger1Checkpoints = listOfExpectedForger1Checkpoints :+ StakeCheckpoint(epochNumber5, BigInteger.ZERO)
      checkStakeHistory(view, forger1History, listOfExpectedForger1Checkpoints)

      listOfExpectedD2F1Checkpoints = listOfExpectedD2F1Checkpoints :+ StakeCheckpoint(epochNumber5, BigInteger.ZERO)
      checkStakeHistory(view, stakeHistory_d2_f1, listOfExpectedD2F1Checkpoints)

    }
  }

  def checkStakeHistory(view: BaseAccountStateView, history: StakeHistory, expectedCheckpoints: Seq[StakeCheckpoint]): Unit = {
    assertEquals(expectedCheckpoints.size, history.getSize(view))
    expectedCheckpoints.indices.foreach { idx =>
      assertEquals(expectedCheckpoints(idx), history.getCheckpoint(view, idx))
    }

  }


}
