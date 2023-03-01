package io.horizen.chain

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.json.Views
import sparkz.util.ModifierId

@JsonView(Array(classOf[Views.Default]))
case class MainchainHeaderInfo(hash: MainchainHeaderHash,
                               parentHash: MainchainHeaderHash,
                               height: Int,
                               sidechainBlockId: ModifierId,
                               cumulativeCommTreeHash: Array[Byte])
