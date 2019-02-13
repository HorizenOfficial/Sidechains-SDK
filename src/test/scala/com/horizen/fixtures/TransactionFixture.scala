package com.horizen.fixtures

import com.horizen.transaction.RegularTransaction
import com.horizen.secret.PrivateKey25519
import com.horizen.box.RegularBox
import java.util.{List => JList}
import java.util.{ArrayList => JArrayList}

import com.horizen.proposition.PublicKey25519Proposition
import javafx.util.{Pair => JPair}

trait TransactionFixture extends BoxFixture {

  def getTransaction () : RegularTransaction = {
    val from : JList[JPair[RegularBox,PrivateKey25519]] = new JArrayList[JPair[RegularBox,PrivateKey25519]]()
    val to : JList[JPair[PublicKey25519Proposition, java.lang.Long]] = new JArrayList[JPair[PublicKey25519Proposition, java.lang.Long]]()

    from.add(new JPair(rb1, pk1))
    from.add(new JPair(rb2, pk2))
    from.add(new JPair(rb3, pk3))

    to.add(new JPair(pk4.publicImage(), 10L))
    to.add(new JPair(pk5.publicImage(), 20L))
    to.add(new JPair(pk6.publicImage(), 90L))

    RegularTransaction.create(from, to, 10L, 1547798549470L)
  }

}
