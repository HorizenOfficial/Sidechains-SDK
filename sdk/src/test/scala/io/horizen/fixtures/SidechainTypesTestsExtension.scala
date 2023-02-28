package io.horizen.fixtures

import io.horizen.SidechainTypes
import io.horizen.utxo.transaction.RegularTransaction
import scala.language.implicitConversions

trait SidechainTypesTestsExtension extends SidechainTypes {

  implicit def regularTxToScbt(t: RegularTransaction): SCBT = t.asInstanceOf[SCBT]

  implicit def regularTxListToScbtList(tl: List[RegularTransaction]): List[SCBT] = tl.asInstanceOf[List[SCBT]]

}
