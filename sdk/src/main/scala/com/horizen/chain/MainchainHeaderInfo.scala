package com.horizen.chain

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views
import sparkz.util.ModifierId

@JsonView(Array(classOf[Views.Default]))
case class MainchainHeaderInfo(hash: MainchainHeaderHash,
                               parentHash: MainchainHeaderHash,
                               height: Int,
                               sidechainBlockId: ModifierId,
                               cumulativeCommTreeHash: Array[Byte])
