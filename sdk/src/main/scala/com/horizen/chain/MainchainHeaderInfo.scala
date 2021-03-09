package com.horizen.chain

import scorex.util.ModifierId
import com.horizen.librustsidechains.FieldElement

case class MainchainHeaderInfo(hash: MainchainHeaderHash,
                               parentHash: MainchainHeaderHash,
                               height: Int,
                               sidechainBlockId: ModifierId,
                               cumulativeHash: FieldElement)
