package com.horizen

import language.implicitConversions
import com.horizen.box.{Box, RegularBox}
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.transaction.BoxTransaction

trait SidechainTypes {

  type S = Secret
  type P = Proposition
  type B = Box[P]
  type BT = BoxTransaction[P,B]

  type SX <: Secret
  type PX <: Proposition
  type BX <: Box[PX]
  type BTX <: BoxTransaction[PX,BX]

  implicit def propositionToExt(p : P) : PX = p.asInstanceOf[PX]
  implicit def extToProposition(px : PX) : P = px.asInstanceOf[P]

  implicit def boxToExt(b : B) : BX = b.asInstanceOf[BX]
  implicit def extToBox(bx : BX) : B = bx.asInstanceOf[B]

  implicit def transactionToExt(bt : BT) : BTX = bt.asInstanceOf[BTX]
  implicit def extToTransaction(btx : BTX) : BT = btx.asInstanceOf[BT]

  implicit def rbToBox(rb : RegularBox) : B = rb.asInstanceOf[B]
  implicit def boxToRb(b : B) : RegularBox = b.asInstanceOf[RegularBox]

}
