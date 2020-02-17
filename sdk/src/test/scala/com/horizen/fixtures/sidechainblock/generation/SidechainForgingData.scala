package com.horizen.fixtures.sidechainblock.generation

import java.util.Random

import com.horizen.box.ForgerBox
import com.horizen.consensus.hashToStakePercent
import com.horizen.secret.{PrivateKey25519, PrivateKey25519Creator}
import com.horizen.vrf.{VRFKeyGenerator, VRFProof, VRFSecretKey}


case class SidechainForgingData(key: PrivateKey25519, forgerBox: ForgerBox, vrfSecret: VRFSecretKey) {
  /**
   * @return VrfProof in case if can be forger
   */
  def canBeForger(vrfMessage: Array[Byte], totalStake: Long, additionalCheck: Boolean => Boolean): Option[VRFProof] = {
    val checker = (stakeCheck _).tupled.andThen(additionalCheck)
    Some(vrfSecret.prove(vrfMessage)).filter(checker(_, totalStake))
  }

  private def stakeCheck(proof: VRFProof, totalStake: Long): Boolean = {
    val requiredPercentage: Double = hashToStakePercent(proof.proofToVRFHash())
    val actualPercentage: Double = forgerBox.value().toDouble / totalStake

    //println(s"For ${key.hashCode()} with value ${forgerBox.value()} and Vrf ${forgerBox.vrfPubKey().key.hashCode()}: required % ${requiredPercentage}, actual % ${actualPercentage}")

    requiredPercentage <= actualPercentage
  }

  val forgerId: Array[Byte] = forgerBox.id()

  override def toString: String = {
    s"id - ${key.hashCode()}, value - ${forgerBox.value()}"
  }

  override def equals(obj: Any): Boolean = {
    obj match {
      case that: SidechainForgingData => {
        val keyEquals = this.key.equals(that.key)
        val forgerBoxEquals = this.forgerBox.equals(that.forgerBox)
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
    val (vrfSecretKey, vrfPublicKey) = VRFKeyGenerator.generate(rnd.nextLong().toString.getBytes())
    val forgerBox = new ForgerBox(key.publicImage(), rnd.nextLong(), value, key.publicImage(), vrfPublicKey)

    SidechainForgingData(key, forgerBox, vrfSecretKey)
  }
}

