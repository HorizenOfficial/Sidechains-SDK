package com.horizen.companion

import com.horizen.box._
import com.horizen.proposition._
import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.utils.DynamicTypedSerializer


case class SidechainBoxesCompanion(customSerializers: JHashMap[JByte, BoxSerializer[_ <: Box[_ <: Proposition]]])
  extends DynamicTypedSerializer[Box[_ <: Proposition], BoxSerializer[_ <: Box[_ <: Proposition]]](
    new JHashMap[JByte, BoxSerializer[_ <: Box[_ <: Proposition]]]() {{
        put(RegularBox.BOX_TYPE_ID, RegularBoxSerializer.getSerializer)
        put(CertifierRightBox.BOX_TYPE_ID, CertifierRightBoxSerializer.getSerializer)
      }},
    customSerializers)

