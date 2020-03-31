package com.horizen.chain

import scorex.util.ModifierId

case class MainchainBlockReferenceDataInfo(headerHash: MainchainHeaderHash,
                                           height: Int,
                                           sidechainBlockId: ModifierId)
