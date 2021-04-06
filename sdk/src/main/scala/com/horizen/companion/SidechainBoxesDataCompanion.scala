package com.horizen.companion

import java.util.{HashMap => JHashMap}
import java.lang.{Byte => JByte}

import com.horizen.SidechainTypes
import com.horizen.box.data.CoreBoxesDataIdsEnum.{ForgerBoxDataId, ZenBoxDataId, WithdrawalRequestBoxDataId}
import com.horizen.box.data._
import com.horizen.utils.DynamicTypedSerializer

case class SidechainBoxesDataCompanion(customSerializers: JHashMap[JByte, NoncedBoxDataSerializer[SidechainTypes#SCBD]])
  extends DynamicTypedSerializer[SidechainTypes#SCBD, NoncedBoxDataSerializer[SidechainTypes#SCBD]](
    new JHashMap[JByte, NoncedBoxDataSerializer[SidechainTypes#SCBD]]() {{
      put(ZenBoxDataId.id(), ZenBoxDataSerializer.getSerializer.asInstanceOf[NoncedBoxDataSerializer[SidechainTypes#SCBD]])
      put(ForgerBoxDataId.id(), ForgerBoxDataSerializer.getSerializer.asInstanceOf[NoncedBoxDataSerializer[SidechainTypes#SCBD]])
      put(WithdrawalRequestBoxDataId.id(), WithdrawalRequestBoxDataSerializer.getSerializer.asInstanceOf[NoncedBoxDataSerializer[SidechainTypes#SCBD]])
    }},
    customSerializers)
