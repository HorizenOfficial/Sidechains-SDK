package io.horizen.account.state

import com.sun.jna.platform.win32.WinNT.WELL_KNOWN_SID_TYPE
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

      ForgerStakeStorageV3.addForger(view, blockSignerProposition, vrfPublicKey,rewardShare, rewardAddress, epochNumber, delegator, BigInteger.TEN)

      result = ForgerStakeStorageV3.getPagedListOfForgers(view, 0, 10)

      var listOfForgers = result._2
      assertEquals(1, listOfForgers.size)
      assertEquals(blockSignerProposition, listOfForgers.head.forgerPublicKeys.blockSignPublicKey)
      assertEquals(vrfPublicKey, listOfForgers.head.forgerPublicKeys.vrfPublicKey)
      assertEquals(rewardAddress, listOfForgers.head.rewardAddress.address())
      assertEquals(rewardShare, listOfForgers.head.rewardShare)
      assertEquals(-1, result._1)

    }
  }

}
