package com.horizen.chain

import scorex.util.ModifierId

case class MainchainHeaderInfo(hash: MainchainHeaderHash,
                               parentHash: MainchainHeaderHash,
                               height: Int,
                               sidechainBlockId: ModifierId)
