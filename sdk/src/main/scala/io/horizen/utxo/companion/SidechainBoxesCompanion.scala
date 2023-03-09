package io.horizen.utxo.companion

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}
import io.horizen.utxo.box.CoreBoxesIdsEnum._
import io.horizen.SidechainTypes
import io.horizen.utxo.box._
import io.horizen.utils.{CheckedCompanion, DynamicTypedSerializer}

case class SidechainBoxesCompanion(customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]])
  extends DynamicTypedSerializer[SidechainTypes#SCB, BoxSerializer[SidechainTypes#SCB]](
    new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]() {{
        put(ZenBoxId.id(), ZenBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
        put(WithdrawalRequestBoxId.id(), WithdrawalRequestBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
        put(ForgerBoxId.id(), ForgerBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
    }},
    customBoxSerializers
  ) with CheckedCompanion[SidechainTypes#SCB, BoxSerializer[SidechainTypes#SCB]]

