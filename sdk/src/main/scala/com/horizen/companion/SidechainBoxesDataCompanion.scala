package com.horizen.companion

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes
import com.horizen.box.data.CoreBoxesDataIdsEnum.{CertifierRightBoxDataId, ForgerBoxDataId, RegularBoxDataId, WithdrawalRequestBoxDataId}
import com.horizen.box.data._
import com.horizen.utils.DynamicTypedSerializer

case class SidechainBoxesDataCompanion(customSerializers: JHashMap[JByte, BoxDataSerializer[SidechainTypes#SCBD]])
  extends DynamicTypedSerializer[SidechainTypes#SCBD, BoxDataSerializer[SidechainTypes#SCBD]](
    new JHashMap[JByte, BoxDataSerializer[SidechainTypes#SCBD]]() {{
      put(RegularBoxDataId.id(), RegularBoxDataSerializer.getSerializer.asInstanceOf[BoxDataSerializer[SidechainTypes#SCBD]])
      put(CertifierRightBoxDataId.id(), CertifierRightBoxDataSerializer.getSerializer.asInstanceOf[BoxDataSerializer[SidechainTypes#SCBD]])
      put(ForgerBoxDataId.id(), ForgerBoxDataSerializer.getSerializer.asInstanceOf[BoxDataSerializer[SidechainTypes#SCBD]])
      put(WithdrawalRequestBoxDataId.id(), WithdrawalRequestBoxDataSerializer.getSerializer.asInstanceOf[BoxDataSerializer[SidechainTypes#SCBD]])
    }},
    customSerializers)
