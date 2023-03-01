package io.horizen

import io.horizen.account.transaction.{AccountTransaction, EthereumTransaction}

import language.implicitConversions
import java.util.{List => JList}
import io.horizen.proof.Proof
import io.horizen.proposition.Proposition
import io.horizen.secret.Secret
import io.horizen.utxo.box.{Box, ForgerBox, WithdrawalRequestBox, ZenBox}
import io.horizen.utxo.box.data.BoxData
import io.horizen.utxo.transaction.{BoxTransaction, SidechainTransaction}

trait SidechainTypes {

  type SCS = Secret
  type SCP = Proposition
  type SCPR = Proof[SCP]

  // Box types
  type SCB = Box[SCP]
  type SCBD = BoxData[SCP, SCB]
  type SCBT = BoxTransaction[SCP, SCB]

  // Account types
  type SCAT = AccountTransaction[SCP, SCPR]

  //implicit def ponpToSCP(p : ProofOfKnowledgeProposition[_ <: Secret]) : SCP = p.asInstanceOf[SCP]

  implicit def sidechainTxToScbt(t: SidechainTransaction[Proposition, Box[Proposition]]): SCBT = t.asInstanceOf[SCBT]

  implicit def ethereumTxToScat(t: EthereumTransaction): SCAT = t.asInstanceOf[SCAT]

  implicit def sidechainTxListToScbtList(tl: JList[SidechainTransaction[Proposition, Box[Proposition]]]): JList[SCBT] = tl.asInstanceOf[JList[SCBT]]

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
