package io.horizen.utxo.fixtures

import com.google.common.primitives.Longs
import io.horizen.SidechainTypes
import io.horizen.customtypes._
import io.horizen.fixtures.SecretFixture
import io.horizen.proposition.{MCPublicKeyHashProposition, Proposition, PublicKey25519Proposition, VrfPublicKey}
import io.horizen.sc2sc.CrossChainProtocolVersion
import io.horizen.secret.PrivateKey25519
import io.horizen.utils.ZenCoinsUtils
import io.horizen.utxo.box.data.{CrossChainMessageBoxData, ForgerBoxData, WithdrawalRequestBoxData, ZenBoxData}
import io.horizen.utxo.box.{Box, CrossChainMessageBox, ForgerBox, WithdrawalRequestBox, ZenBox}
import io.horizen.utxo.customtypes.{CustomBox, CustomBoxData}
import io.horizen.utxo.wallet.WalletBox
import sparkz.core.bytesToId

import java.util.{ArrayList => JArrayList, List => JList}
import scala.collection.JavaConverters._
import scala.util.Random

trait BoxFixture
  extends SecretFixture
    with SidechainTypes {
  def getZenBoxData: ZenBoxData = {
    new ZenBoxData(getPrivateKey25519.publicImage(), Random.nextInt(100))
  }

  def getZenBox: ZenBox = {
    new ZenBox(new ZenBoxData(getPrivateKey25519.publicImage(), Random.nextInt(100)), 1)
  }

  def getZenBox(seed: Long): ZenBox = {
    val random: Random = new Random(seed)
    new ZenBox(new ZenBoxData(getPrivateKey25519(Longs.toByteArray(seed)).publicImage(), random.nextInt(100)), random.nextLong())
  }

  def getZenBox(privateKey: PrivateKey25519, nonce: Long, value: Long): ZenBox = {
    new ZenBox(new ZenBoxData(privateKey.publicImage(), value), nonce)
  }

  def getZenBox(proposition: PublicKey25519Proposition): ZenBox = {
    new ZenBox(new ZenBoxData(proposition, Random.nextInt(100)), Random.nextInt(100))
  }

  def getZenBox(proposition: PublicKey25519Proposition, nonce: Long, value: Long): ZenBox = {
    new ZenBox(new ZenBoxData(proposition, value), nonce)
  }

  def getCrossMessageBox(proposition: PublicKey25519Proposition,
                         protocolVersion: CrossChainProtocolVersion,
                         messageType: Integer,
                         receiverSidechain: Array[Byte],
                         receiverAddress: Array[Byte],
                         payload: Array[Byte],
                         nonce: Long
                        ): CrossChainMessageBox = {
    new CrossChainMessageBox(new CrossChainMessageBoxData(proposition, protocolVersion,
      messageType, receiverSidechain, receiverAddress, payload), nonce)
  }

  def getRandomCrossMessageBox(seed: Long): CrossChainMessageBox = {
    val random: Random = new Random(seed)
    val receiverSidechain = new Array[Byte](32)
    random.nextBytes(receiverSidechain)
    val receiverAddress = new Array[Byte](20)
    random.nextBytes(receiverAddress)
    val payloadHash = new Array[Byte](32)
    random.nextBytes(payloadHash)
    getCrossMessageBox(
      getPrivateKey25519(Longs.toByteArray(random.nextLong())).publicImage(),
      CrossChainProtocolVersion.VERSION_1,
      1,
      receiverSidechain,
      receiverAddress,
      payloadHash,
      random.nextLong()
    )
  }

  def getZenBoxList(count: Int): JList[ZenBox] = {
    val boxList: JList[ZenBox] = new JArrayList[ZenBox]()

    for (i <- 1 to count)
      boxList.add(getZenBox)

    boxList
  }

  def getZenBoxList(secretList: JList[PrivateKey25519], minBoxAmount: Int = 0): JList[ZenBox] = {
    val boxList: JList[ZenBox] = new JArrayList[ZenBox]()

    for (s <- secretList.asScala)
      boxList.add(getZenBox(s.publicImage(), 1, minBoxAmount + Random.nextInt(10000)))

    boxList
  }

  def getCustomBoxData: CustomBoxData = {
    new CustomBoxData(getCustomPrivateKey.publicImage(), Random.nextInt(100))
  }

  def getCustomBox: CustomBox = {
    new CustomBox(new CustomBoxData(getCustomPrivateKey.publicImage(), Random.nextInt(100)), Random.nextInt(1000))
  }

  def getCustomBoxWithPrivateKey(proposition: CustomPublicKeyProposition): CustomBox = {
    new CustomBox(new CustomBoxData(proposition, Random.nextInt(100)), Random.nextInt(1000))
  }

  def getCustomBoxList(count: Int): JList[CustomBox] = {
    val boxList: JList[CustomBox] = new JArrayList()

    for (i <- 1 to count)
      boxList.add(getCustomBox)

    boxList
  }

  def getCustomBoxListWithPrivateKeys(secretList: JList[CustomPrivateKey]): JList[CustomBox] = {
    val boxList: JList[CustomBox] = new JArrayList()
    for (s <- secretList.asScala)
      boxList.add(getCustomBoxWithPrivateKey(s.publicImage()))

    boxList
  }

  def getWalletBox(box: SidechainTypes#SCB): WalletBox = {
    val txId = new Array[Byte](32)
    Random.nextBytes(txId)
    new WalletBox(box, bytesToId(txId), Random.nextInt(100000))
  }

  def getWalletBox(boxClass: Class[_ <: Box[_ <: Proposition]]): WalletBox = {
    val txId = new Array[Byte](32)
    Random.nextBytes(txId)

    boxClass match {
      case v if v == classOf[ZenBox] => new WalletBox(getZenBox, bytesToId(txId), Random.nextInt(100000))
      case v if v == classOf[CustomBox] => new WalletBox(getCustomBox.asInstanceOf[SidechainTypes#SCB], bytesToId(txId), Random.nextInt(100000))
      case _ => null
    }
  }

  def getWalletBoxList(boxClass: Class[_ <: Box[_ <: Proposition]], count: Int): JList[WalletBox] = {
    val boxList: JList[WalletBox] = new JArrayList[WalletBox]()

    for (i <- 1 to count)
      boxList.add(getWalletBox(boxClass))

    boxList
  }

  def getWalletBoxList(boxList: JList[SidechainTypes#SCB]): JList[WalletBox] = {
    val wboxList: JList[WalletBox] = new JArrayList[WalletBox]()
    val txId = new Array[Byte](32)
    Random.nextBytes(txId)

    for (b <- boxList.asScala)
      wboxList.add(new WalletBox(b, bytesToId(txId), Random.nextInt(100000)))

    wboxList
  }

  val dustThreshold: Long = ZenCoinsUtils.getMinDustThreshold(ZenCoinsUtils.MC_DEFAULT_FEE_RATE)

  def getWithdrawalRequestBoxData: WithdrawalRequestBoxData = {
    new WithdrawalRequestBoxData(getMCPublicKeyHashProposition, Random.nextInt(100) + dustThreshold)
  }

  def getLowWithdrawalRequestBoxData: WithdrawalRequestBoxData = {
    new WithdrawalRequestBoxData(getMCPublicKeyHashProposition, Random.nextInt(dustThreshold.toInt))
  }

  def getWithdrawalRequestBox: WithdrawalRequestBox = {
    new WithdrawalRequestBox(new WithdrawalRequestBoxData(getMCPublicKeyHashProposition, Random.nextInt(100) + dustThreshold), Random.nextInt(100))
  }

  def getWithdrawalRequestBox(key: MCPublicKeyHashProposition, nonce: Long, value: Long): WithdrawalRequestBox = {
    new WithdrawalRequestBox(new WithdrawalRequestBoxData(key, value), nonce)
  }

  def getWithdrawalRequestsBoxList(count: Int): JList[WithdrawalRequestBox] = {
    val boxList: JList[WithdrawalRequestBox] = new JArrayList()

    for (i <- 1 to count)
      boxList.add(getWithdrawalRequestBox)

    boxList
  }

  def getForgerBoxData: ForgerBoxData = {
    new ForgerBoxData(getPrivateKey25519.publicImage(), Random.nextInt(100), getPrivateKey25519.publicImage(), getVRFPublicKey)
  }

  def getForgerBox: ForgerBox = {
    new ForgerBoxData(getPrivateKey25519.publicImage(), Random.nextInt(100), getPrivateKey25519.publicImage(), getVRFPublicKey).getBox(Random.nextInt(100))
  }

  def getForgerBox(proposition: PublicKey25519Proposition): ForgerBox =
    new ForgerBoxData(proposition, Random.nextInt(100), getPrivateKey25519.publicImage(), getVRFPublicKey).getBox(Random.nextInt(100))


  def getForgerBox(proposition: PublicKey25519Proposition, nonce: Long, value: Long,
                   blockSignProposition: PublicKey25519Proposition, vrfPublicKey: VrfPublicKey): ForgerBox = {
    new ForgerBoxData(proposition, value, blockSignProposition, vrfPublicKey).getBox(nonce)
  }


  def getForgerBoxList(count: Int): JList[ForgerBox] = {
    val boxList: JList[ForgerBox] = new JArrayList()

    for (i <- 1 to count)
      boxList.add(getForgerBox)

    boxList
  }

  def getRandomBoxId: Array[Byte] = {
    val id: Array[Byte] = new Array[Byte](32)
    Random.nextBytes(id)
    id
  }

  def getRandomBoxId(seed: Long): Array[Byte] = {
    val id: Array[Byte] = new Array[Byte](32)
    new Random(seed).nextBytes(id)
    id
  }
}

class BoxFixtureClass extends BoxFixture