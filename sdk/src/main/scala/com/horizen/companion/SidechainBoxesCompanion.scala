package com.horizen.companion

import java.lang.{Byte => JByte}
import java.util.{HashMap => JHashMap}

import com.horizen.SidechainTypes
import com.horizen.box._
import com.horizen.utils.DynamicTypedSerializer


case class SidechainBoxesCompanion(customSerializers: JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]])
  extends DynamicTypedSerializer[SidechainTypes#SCB, BoxSerializer[SidechainTypes#SCB]](
    new JHashMap[JByte, BoxSerializer[SidechainTypes#SCB]]() {{
        put(RegularBox.BOX_TYPE_ID, RegularBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
        put(CertifierRightBox.BOX_TYPE_ID, CertifierRightBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
        put(ForgerBox.BOX_TYPE_ID, ForgerBoxSerializer.getSerializer.asInstanceOf[BoxSerializer[SidechainTypes#SCB]])
      }},
    customSerializers)

