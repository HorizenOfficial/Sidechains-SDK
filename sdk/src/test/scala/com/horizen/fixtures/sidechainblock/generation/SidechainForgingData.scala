package com.horizen.fixtures.sidechainblock.generation

import java.util.Random

import com.horizen.box.ForgerBox
import com.horizen.box.data.ForgerBoxData
import com.horizen.consensus._
import com.horizen.proof.VrfProof
import com.horizen.proposition.VrfPublicKey
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator, VrfKeyGenerator, VrfSecretKey}
import com.horizen.vrf.VrfOutput


case class SidechainForgingData(key: PrivateKey25519, forgingStakeInfo: ForgingStakeInfo, vrfSecret: VrfSecretKey) {
  /**
   * @return VrfProof in case if can be forger
   */
  def canBeForger(vrfMessage: VrfMessage, totalStake: Long, additionalCheck: Boolean => Boolean): Option[(VrfProof, VrfOutput)] = {
    val vrfProofAndHash = vrfSecret.prove(vrfMessage)
    val vrfProof = vrfProofAndHash.getKey
    val vrfOutput = vrfProofAndHash.getValue

    val checker = (stakeCheck _).tupled.andThen(additionalCheck)
    Some((vrfProof, vrfOutput)).filter{case (vrfProof, vrfOutput) => checker(vrfOutput, totalStake)}
  }

  private def stakeCheck(vrfOutput: VrfOutput, totalStake: Long): Boolean = {
    vrfProofCheckAgainstStake(vrfOutput, forgingStakeInfo.stakeAmount, totalStake)
  }

  val forgerId: Array[Byte] = forgingStakeInfo.hash

  override def toString: String = {
    s"id - ${key.hashCode()}, value - ${forgingStakeInfo.stakeAmount}"
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: SidechainForgingData => {
        val keyEquals = this.key.equals(that.key)
        val forgerBoxEquals = this.forgingStakeInfo.equals(that.forgingStakeInfo)
        val vrfSecretEquals = this.vrfSecret.equals(that.vrfSecret)

        keyEquals && forgerBoxEquals && vrfSecretEquals
      }
      case _ =>
        false
    }
  }
}

object SidechainForgingData {
  def generate(rnd: Random, value: Long): SidechainForgingData = {
    val key: PrivateKey25519 = PrivateKey25519Creator.getInstance().generateSecret(rnd.nextLong().toString.getBytes)
    val vrfSecretKey = VrfKeyGenerator.getInstance().generateSecret(rnd.nextLong().toString.getBytes())
    val vrfPublicKey: VrfPublicKey = vrfSecretKey.publicImage()
    val forgerBox = new ForgerBoxData(key.publicImage(), value, key.publicImage(), vrfPublicKey).getBox(rnd.nextLong())
    val forgingStakeInfo = ForgingStakeInfo(forgerBox.blockSignProposition(), forgerBox.vrfPubKey(), forgerBox.value())
    SidechainForgingData(key, forgingStakeInfo, vrfSecretKey)
  }
}

