package com.horizen.companion


import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.box.CoreBoxesIdsEnum._
import com.horizen.SidechainTypes
import com.horizen.box._
import com.horizen.utils.DynamicTypedSerializer


case class SidechainBoxesCompanion(customSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]])
  extends DynamicTypedSerializer[SidechainTypes#SCB, BoxSerializer[SidechainTypes#SCB]](
    new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]() {{
        put(RegularBoxId.id(), RegularBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
        put(CertifierRightBoxId.id(), CertifierRightBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
        put(ForgerBoxId.id(), ForgerBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
      }},
    customSerializers)

