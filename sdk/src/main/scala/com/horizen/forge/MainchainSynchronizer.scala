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
    val (_: Int, commonHashHex: String) = getMainchainCommonPointInfo(history).get
    mainchainNodeChannel.getNewBlockHashes(Seq(commonHashHex), limit) match {
      case Success((height, hashes)) => (height, hashes.map(hex => byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(hex))))
      case Failure(ex) => throw ex
    }
  }

  // Return common block height and hash as a hex string.
  def getMainchainCommonPointInfo(history: SidechainHistory): Try[(Int, String)] = Try {
    val locatorHashes: Seq[String] = history.getMainchainHashesLocator.map(baw => BytesUtils.toHexString(baw.data))
    val (commonHeight, commonHashHex) = mainchainNodeChannel.getNewBlockHashes(locatorHashes, 1) match {
      case Success((height, hashes)) => (height, hashes.head)
      case Failure(ex) => throw ex
    }

    if(commonHeight == history.getBestMainchainHeaderInfo.get.height) {
      // No orphan mainchain blocks -> return result as is
      (commonHeight, commonHashHex)
    } else {
      // Orphan mainchain blocks present
      // Check if there is more recent common block, that was not a part of locatorHashes
      val commonHashLocatorIndex: Int = locatorHashes.indexOf(commonHashHex)
      val commonHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(commonHashHex))
      val lastUnknownHash: MainchainHeaderHash = byteArrayToMainchainHeaderHash(BytesUtils.fromHexString(locatorHashes(commonHashLocatorIndex - 1)))
      // Get the list of MainchainHeader Hashes between last unknown point and previously found common point
      val locator: Seq[String] = history.getMainchainHashes(lastUnknownHash, commonHash).map(baw => BytesUtils.toHexString(baw.data))

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