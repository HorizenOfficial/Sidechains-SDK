package com.horizen.forge

import com.horizen.SidechainHistory
import com.horizen.block.MainchainBlockReference
import com.horizen.chain.{MainchainHeaderHash, byteArrayToMainchainHeaderHash}
import com.horizen.utils.BytesUtils
import com.horizen.websocket.MainchainNodeChannel
import com.horizen.utils._

import scala.util.{Failure, Success, Try}

class MainchainSynchronizer(mainchainNodeChannel: MainchainNodeChannel) {
  // Get divergent mainchain suffix between SC Node and MC Node
  // Return last common header with height + divergent suffix
  def getMainchainDivergentSuffix(history: SidechainHistory, limit: Int): Try[(Int, Seq[MainchainHeaderHash])] = Try {
    val (_: Int, commonHashHex: String) = getMainchainCommonBlockHashAndHeight(history).get
    mainchainNodeChannel.getNewBlockHashes(Seq(commonHashHex), limit) match {
      case Success((height, hashes)) => (height, hashes.map(hex => byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(hex))))
      case Failure(ex) => throw ex
    }
  }

  // Return common block height and hash as a hex string.
  def getMainchainCommonBlockHashAndHeight(history: SidechainHistory): Try[(Int, String)] = Try {
    val locatorHashes: Seq[String] = history.getMainchainHashesLocator.map(baw => BytesUtils.toHexString(baw.data))
    val (commonHeight, commonHashHex) = mainchainNodeChannel.getBestCommonPoint(locatorHashes).get
    val commonHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(commonHashHex))

    if(commonHash == history.getBestMainchainHeaderInfo.get.hash) {
      // No orphan mainchain blocks -> return result as is
      (commonHeight, commonHashHex)
    } else {
      // Orphan mainchain blocks present
      // Check if there is more recent common block, that was not a part of locatorHashes
      val commonHashLocatorIndex: Int = locatorHashes.indexOf(commonHashHex)
      val firstUnknownHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(locatorHashes(commonHashLocatorIndex - 1)))
      // Get the list of MainchainHeader Hashes between previously found common point and first unknown point.
      // Order them from newest to oldest as locator.
      val locator: Seq[String] = history.getMainchainHashes(commonHash, firstUnknownHash).map(baw => BytesUtils.toHexString(baw.data)).reverse

      mainchainNodeChannel.getNewBlockHashes(locator, 1) match {
        case Success((height, hashes)) => (height, hashes.head)
        case Failure(ex) => throw ex
      }
    }
  }

  def getMainchainBlockReferences(history: SidechainHistory, hashes: Seq[MainchainHeaderHash]): Try[Seq[MainchainBlockReference]] = Try {
    val references = hashes.map(hash => mainchainNodeChannel.getBlockByHash(BytesUtils.toHexString(hash.data))).filter(_.isSuccess).map(_.get)
    if(references.size != hashes.size)
      throw new IllegalStateException("Can't retrieve all hashes requested. Connection error.")
    else
      references
  }
}

object MainchainSynchronizer {
  val MAX_BLOCKS_REQUEST: Int = 50
}