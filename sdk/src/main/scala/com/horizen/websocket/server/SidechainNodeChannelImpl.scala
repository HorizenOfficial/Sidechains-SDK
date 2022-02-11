package com.horizen.websocket.server

import java.util
import scorex.util.{ModifierId, ScorexLogging}
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainSyncInfo, SidechainWallet}
import com.horizen.block.SidechainBlock

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.Try
import com.horizen.serialization.SerializationUtil
import com.horizen.websocket.server.WebSocketServerRef.sidechainNodeViewHolderRef
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import scala.language.postfixOps


class SidechainNodeChannelImpl() extends SidechainNodeChannel with ScorexLogging {

  implicit val duration: Timeout = 20 seconds
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)
  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]

  def applyOnNodeView[R](functionToBeApplied: View => R): R = {
    try {
      val res = (sidechainNodeViewHolderRef ? GetDataFromCurrentView(functionToBeApplied)).asInstanceOf[Future[R]]
      val result = Await.result[R](res, 5000 millis)
      result
    }
    catch {
      case e: Exception => throw new Exception(e)
    }

  }

  override def getBlockInfoByHeight(height: Int): Try[ObjectNode] = {
    applyOnNodeView { sidechainNodeView =>
      Try {
        //get block hash by id
        val sidechainBlockHash: Option[String] = sidechainNodeView.history.blockIdByHeight(height)
        if (sidechainBlockHash.isEmpty) throw new IllegalStateException(s"Block not found for height: " + height)
        //get block by hash
        val sblockOpt = sidechainNodeView.history.modifierById(sidechainBlockHash.get.asInstanceOf[ModifierId])
        if (sblockOpt.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + sidechainBlockHash)
        sblockOpt.get
      }
    }.map(block => {
      //serialize JSON
      calculateBlockPayload(block, height)
    })

  }


  override def getBlockInfoByHash(hash: String): Try[ObjectNode] = {
    applyOnNodeView { sidechainNodeView =>
      Try {
        //get block by hash
        val blockOpt = sidechainNodeView.history.modifierById(hash.asInstanceOf[ModifierId])
        if (blockOpt.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + hash)
        //get block height by hash
        val height = sidechainNodeView.history.blockInfoById(hash.asInstanceOf[ModifierId]).height
        (blockOpt.get, height)
      }
    }.map(blockInfo => {
      //serialize JSON
      calculateBlockPayload(blockInfo._1, blockInfo._2)
    })
  }

  override def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[ObjectNode] = Try {
    var lastHeight = -1
    val responsePayload = mapper.createObjectNode()

    val scInfo: SidechainSyncInfo = SidechainSyncInfo(locatorHashes.asInstanceOf[Seq[ModifierId]])
    val headerList = applyOnNodeView { sidechainNodeView =>
      val headers = sidechainNodeView.history.continuationIds(scInfo, limit)
      if (headers.nonEmpty) {
        lastHeight = sidechainNodeView.history.blockInfoById(headers.last._2.asInstanceOf[ModifierId]).height
      }
      headers
    }

    val hashes = mapper.readTree(SerializationUtil.serializeWithResult(headerList.map(el => el._2)))
    responsePayload.put("height", lastHeight)
    responsePayload.put("hashes", hashes.get("result"))

    responsePayload
  }

  override def getMempoolTxs(txids: Seq[String]): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()

    val txs = applyOnNodeView { sidechainNodeView =>
      sidechainNodeView.pool.getAll(txids.asInstanceOf[Seq[ModifierId]])
    }

    val txsJson = mapper.readTree(SerializationUtil.serializeWithResult(txs))
    responsePayload.put("transactions", txsJson.get("result"))
    responsePayload
  }

  override def getRawMempool(): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()

    val txids = applyOnNodeView { sidechainNodeView =>
      val txs: util.ArrayList[String] = new util.ArrayList[String]()
      sidechainNodeView.pool.take(sidechainNodeView.pool.size).foreach(tx => txs.add(tx.id()))
      txs
    }

    val json = mapper.readTree(SerializationUtil.serializeWithResult(txids.toArray()))

    responsePayload.set("transactions", json.get("result"))
    responsePayload.put("size", txids.size())

    responsePayload
  }

  override def getBestBlockInfo(): Try[ObjectNode] = Try {
    val bestBlock: (SidechainBlock, Integer) = applyOnNodeView { sidechainNodeView =>
      //get best block
      val bBlock = sidechainNodeView.history.bestBlock
      //get block height by hash
      val height = sidechainNodeView.history.height
      (bBlock, height)
    }

    calculateBlockPayload(bestBlock._1, bestBlock._2)
  }

  override def getBlockInfo(block: SidechainBlock): Try[ObjectNode] = Try {
    val blockHeight = applyOnNodeView {
      sidechainNodeView => sidechainNodeView.history.blockInfoById(block.id).height
    }

    calculateBlockPayload(block, blockHeight)
  }

  private def calculateBlockPayload(block: SidechainBlock, height: Int): ObjectNode = {
    val eventPayload = mapper.createObjectNode()

    val blockJson = mapper.readTree(SerializationUtil.serializeWithResult(block))

    eventPayload.put("height", height)
    eventPayload.put("hash", block.id)
    eventPayload.set("block", blockJson.get("result"))

    eventPayload
  }
}
