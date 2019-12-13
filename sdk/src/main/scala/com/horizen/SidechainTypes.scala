package com.horizen

import language.implicitConversions
import java.util.{List => JList}

import com.horizen.box.{Box, CertifierRightBox, NoncedBox, RegularBox}
import com.horizen.proposition.Proposition
import com.horizen.secret.Secret
import com.horizen.transaction.{BoxTransaction, RegularTransaction, SidechainTransaction}
import com.horizen.proposition.ProofOfKnowledgeProposition

trait SidechainTypes {

  type SCS = Secret
  type SCP = Proposition
  type SCB = Box[SCP]
  type SCBT = BoxTransaction[SCP, SCB]

  //implicit def ponpToSCP(p : ProofOfKnowledgeProposition[_ <: Secret]) : SCP = p.asInstanceOf[SCP]

  implicit def regulartxToScbt(t: RegularTransaction): SCBT = t.asInstanceOf[SCBT]

  implicit def sidechainNoncedBoxTxToScbt(t: SidechainTransaction[Proposition, NoncedBox[Proposition]]): SCBT = t.asInstanceOf[SCBT]

  implicit def regulartxlToScbtl(tl: List[RegularTransaction]): List[SCBT] = tl.asInstanceOf[List[SCBT]]

  implicit def sidechainNoncedBoxTxlToScbtl(tl: JList[SidechainTransaction[Proposition, NoncedBox[Proposition]]]): JList[SCBT] = tl.asInstanceOf[JList[SCBT]]

  implicit def regularboxToScb(b: RegularBox): SCB = b.asInstanceOf[SCB]

  implicit def certifierrightboxToScb(b: CertifierRightBox): SCB = b.asInstanceOf[SCB]

  implicit def regularboxjlToScbtjl(bl: JList[RegularBox]): JList[SCB] = bl.asInstanceOf[JList[SCB]]

  implicit def regularboxlToScbtL(bl: List[RegularBox]): List[SCB] = bl.asInstanceOf[List[SCB]]

  implicit def certifierrightboxjlToScbtjl(bl: JList[CertifierRightBox]): JList[SCB] = bl.asInstanceOf[JList[SCB]]

  implicit def certifierrightboxlToScbtL(bl: List[CertifierRightBox]): List[SCB] = bl.asInstanceOf[List[SCB]]

  implicit def regularboxsToScbs(bs: Set[RegularBox]): Set[SCB] = bs.asInstanceOf[Set[SCB]]

  implicit def certifierrightboxsToScbs(bs: Set[CertifierRightBox]): Set[SCB] = bs.asInstanceOf[Set[SCB]]

}
