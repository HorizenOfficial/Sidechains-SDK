package com.horizen

import language.implicitConversions
import java.util.{List => JList}

import com.horizen.box._
import com.horizen.box.data.NoncedBoxData
import com.horizen.proof.Proof
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.transaction.{BoxTransaction, SidechainTransaction}

trait SidechainTypes {

  type SCS = Secret
  type SCP = Proposition
  type SCPR = Proof[SCP]
  type SCB = Box[SCP]
  type SCBD = NoncedBoxData[SCP, NoncedBox[SCP]]
  type SCBT = BoxTransaction[SCP, SCB]

  //implicit def ponpToSCP(p : ProofOfKnowledgeProposition[_ <: Secret]) : SCP = p.asInstanceOf[SCP]

  implicit def sidechainNoncedBoxTxToScbt(t: SidechainTransaction[Proposition, NoncedBox[Proposition]]): SCBT = t.asInstanceOf[SCBT]

  implicit def sidechainNoncedBoxTxListToScbtList(tl: JList[SidechainTransaction[Proposition, NoncedBox[Proposition]]]): JList[SCBT] = tl.asInstanceOf[JList[SCBT]]

  implicit def zenBoxToScb(b: ZenBox): SCB = b.asInstanceOf[SCB]

  implicit def withdrawalRequestBoxToScb(b: WithdrawalRequestBox): SCB = b.asInstanceOf[SCB]

  implicit def forgerBoxToScb(b: ForgerBox): SCB = b.asInstanceOf[SCB]

  implicit def zenBoxJavaListToScbtJavaList(bl: JList[ZenBox]): JList[SCB] = bl.asInstanceOf[JList[SCB]]

  implicit def zenBoxListToScbtList(bl: List[ZenBox]): List[SCB] = bl.asInstanceOf[List[SCB]]

  implicit def forgerBoxListToScbtList(bl: List[ForgerBox]): List[SCB] = bl.asInstanceOf[List[SCB]]

  implicit def zenBoxSetToScbSet(bs: Set[ZenBox]): Set[SCB] = bs.asInstanceOf[Set[SCB]]

  implicit def scbToForgerBox(b: SCB): ForgerBox = b.asInstanceOf[ForgerBox]

  implicit def scbToWithdrawalRequestBox(b: SCB): WithdrawalRequestBox = b.asInstanceOf[WithdrawalRequestBox]
}
