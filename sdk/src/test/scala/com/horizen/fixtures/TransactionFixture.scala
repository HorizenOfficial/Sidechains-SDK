package com.horizen.fixtures

import com.horizen.transaction.RegularTransaction
import com.horizen.secret.PrivateKey25519
import com.horizen.box.RegularBox
import java.util.{ArrayList => JArrayList, List => JList}

import com.horizen.box.data.{BoxData, RegularBoxData, WithdrawalRequestBoxData}
import com.horizen.proposition.{MCPublicKeyHashProposition, Proposition, PublicKey25519Proposition}
import com.horizen.utils.{Pair => JPair}

import scala.util.Random

trait TransactionFixture extends BoxFixture {

  def getRegularTransaction(inputsSecrets: Seq[PrivateKey25519], outputPropositions: Seq[PublicKey25519Proposition]): RegularTransaction = {
    val from: JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition]] = new JArrayList()
    var totalFrom = 0L

    for(secret <- inputsSecrets) {
      val value = 10 + Random.nextInt(10)
      from.add(new JPair(getRegularBox(secret.publicImage(), Random.nextInt(1000), value), secret))
      totalFrom += value
    }

    val minimumFee = 5L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L

    for(proposition <- outputPropositions) {
      val value = maxTo / outputPropositions.size
      to.add(new RegularBoxData(proposition, value))
      totalTo += value
    }

    val fee = totalFrom - totalTo

    RegularTransaction.create(from, to, fee, System.currentTimeMillis - Random.nextInt(10000))
  }

  def getRegularTransaction(inputBoxes: Seq[RegularBox], inputSecrets: Seq[PrivateKey25519], outputPropositions: Seq[PublicKey25519Proposition]): RegularTransaction = {
    val from: JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition]] = new JArrayList()
    var totalFrom = 0L

    for(box <- inputBoxes) {
      from.add(new JPair(box, inputSecrets.find(_.publicImage().equals(box.proposition())).get))
      totalFrom += box.value()
    }

    val minimumFee = 0L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L

    for(proposition <- outputPropositions) {
      val value = maxTo / outputPropositions.size
      to.add(new RegularBoxData(proposition, value))
      totalTo += value
    }

    val fee = totalFrom - totalTo

    RegularTransaction.create(from, to, fee, System.currentTimeMillis - Random.nextInt(10000))
  }

  def getTransaction () : RegularTransaction = {
    val from : JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition]] = new JArrayList()

    from.add(new JPair(getRegularBox(pk1.publicImage(), 1, 10), pk1))
    from.add(new JPair(getRegularBox(pk2.publicImage(), 1, 20), pk2))

    to.add(new RegularBoxData(pk7.publicImage(), 10L))

    RegularTransaction.create(from, to, 10L, 1547798549470L)
  }

  def getCompatibleTransaction () : RegularTransaction = {
    val from : JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition]] = new JArrayList()

    from.add(new JPair(getRegularBox(pk3.publicImage(), 1, 10), pk3))
    from.add(new JPair(getRegularBox(pk4.publicImage(), 1, 10), pk4))

    to.add(new RegularBoxData(pk7.publicImage(), 10L))

    RegularTransaction.create(from, to, 5L, 1547798549470L)
  }

  def getIncompatibleTransaction () : RegularTransaction = {
    val from : JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition]] = new JArrayList()

    from.add(new JPair(getRegularBox(pk1.publicImage(), 1, 10), pk1))
    from.add(new JPair(getRegularBox(pk6.publicImage(), 1, 10), pk6))

    to.add(new RegularBoxData(pk7.publicImage(), 10L))

    RegularTransaction.create(from, to, 5L, 1547798549470L)
  }


  def getTransactionList () : List[RegularTransaction] = {
    val txLst = List[RegularTransaction](getTransaction)
    txLst
  }

  def getCompatibleTransactionList () : List[RegularTransaction] = {
    val txLst = List[RegularTransaction](getCompatibleTransaction)
    txLst
  }

  def getIncompatibleTransactionList () : List[RegularTransaction] = {
    val txLst = List[RegularTransaction](getIncompatibleTransaction)
    txLst
  }

  def getRegularTransaction(inputBoxList: Seq[RegularBox], inputSecretList: Seq[PrivateKey25519],
                            outputList: Seq[PublicKey25519Proposition],
                            withdrawalList: Seq[MCPublicKeyHashProposition]) : RegularTransaction = {
    val from = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to: JList[BoxData[_ <: Proposition]] = new JArrayList()
    var totalFrom = 0L

    for(box <- inputBoxList) {
      from.add(new JPair(box, inputSecretList.find(_.publicImage().equals(box.proposition())).get))
      totalFrom += box.value()
    }

    val minimumFee = 0L
    val maxTo = totalFrom - minimumFee
    var totalTo = 0L
    val outputSize = outputList.size + withdrawalList.size

    for(proposition <- outputList) {
      val value = maxTo / outputSize
      to.add(new RegularBoxData(proposition, value))
      totalTo += value
    }

    for(proposition <- withdrawalList) {
      val value = maxTo / outputSize
      to.add(new WithdrawalRequestBoxData(proposition, value))
      totalTo += value
    }

    val fee = totalFrom - totalTo

    RegularTransaction.create(from, to, fee, System.currentTimeMillis - Random.nextInt(10000))
  }
}
