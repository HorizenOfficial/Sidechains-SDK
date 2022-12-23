package com.horizen.companion

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.box.CoreBoxesIdsEnum._
import com.horizen.SidechainTypes
import com.horizen.box._
import com.horizen.utils.DynamicTypedSerializer


case class SidechainBoxesCompanion(customBoxSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]], isSc2scEnabled: Boolean)
  extends DynamicTypedSerializer[SidechainTypes#SCB, BoxSerializer[SidechainTypes#SCB]]({
      val hashMap = new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]() {
          put(ZenBoxId.id(), ZenBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
          put(WithdrawalRequestBoxId.id(), WithdrawalRequestBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
          put(ForgerBoxId.id(), ForgerBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
      }
      if (isSc2scEnabled)
          hashMap.put(CrossChainMessageBoxId.id(), CrossChainMessageBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
      hashMap
     },
    customBoxSerializers)

