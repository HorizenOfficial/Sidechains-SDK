package com.horizen.chain

import sparkz.util.ModifierId

case class MainchainBlockReferenceDataInfo(headerHash: MainchainHeaderHash,
                                           height: Int,
                                           sidechainBlockId: ModifierId)
