package io.horizen.utxo.companion

import io.horizen.SidechainTypes
import io.horizen.utils.{CheckedCompanion, DynamicTypedSerializer}
import io.horizen.utxo.box.CoreBoxesIdsEnum._
import io.horizen.utxo.box._

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

case class SidechainBoxesCompanion(customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]], isSc2scEnabled: Boolean)
  extends DynamicTypedSerializer[SidechainTypes#SCB, BoxSerializer[SidechainTypes#SCB]]({
    val hashMap = new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]() {
      put(ZenBoxId.id(), ZenBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
      put(WithdrawalRequestBoxId.id(), WithdrawalRequestBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
      put(ForgerBoxId.id(), ForgerBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
    }
    if (isSc2scEnabled) {
      hashMap.put(CrossChainMessageBoxId.id(), CrossChainMessageBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
      hashMap.put(CrossChainRedeemMessageBoxId.id(), CrossChainRedeemMessageBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
    }
    hashMap
  },
    customBoxSerializers
  ) with CheckedCompanion[SidechainTypes#SCB, BoxSerializer[SidechainTypes#SCB]]

