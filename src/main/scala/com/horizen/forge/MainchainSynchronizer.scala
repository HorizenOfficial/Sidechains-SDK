package com.horizen.forge

import com.horizen.SidechainHistory
import com.horizen.block.MainchainBlockReference
import com.horizen.utils.BytesUtils
import com.horizen.websocket.MainchainNodeChannel
import scala.util.{Success, Failure}

class MainchainSynchronizer(mainchainNodeChannel: MainchainNodeChannel) {
  def getNewMainchainBlockReferences(history: SidechainHistory, limit: Int): Seq[MainchainBlockReference] = {
    // to do: define history.MainchainBlockReferencesLocatorHashes, that will return seq of hashes
    val locatorHashes: Seq[String] = Seq(BytesUtils.toHexString(history.getBestMainchainBlockReferenceInfo().getMainchainBlockReferenceHash))
    mainchainNodeChannel.getNewBlockHashes(locatorHashes, limit) match {
      case Success(hashes) =>
        val references = hashes.map(hash => mainchainNodeChannel.getBlockByHash(hash)).filter(_.isSuccess).map(_.get)
        if(references.size != hashes.size)
          Seq()
        else
          references
      case Failure(ex) =>
        Seq()
    }
  }

}
