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


class SidechainNodeChannelImpl() extends SidechainNodeChannel with ScorexLogging{

  implicit val duration: Timeout = 20 seconds
  implicit val ec:ExecutionContext = ExecutionContext.Implicits.global
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

  override def getBlockByHeight(height: Int): Try[ObjectNode] = {

    applyOnNodeView {sidechainNodeView =>
      Try {
        //get block hash by id
        val sidechainBlockHash: Option[String] = sidechainNodeView.history.blockIdByHeight(height)
        if (sidechainBlockHash.isEmpty) throw new IllegalStateException(s"Block not found for height: " + height)
        //get block by hash
        val sblock = sidechainNodeView.history.modifierById(sidechainBlockHash.get.asInstanceOf[ModifierId])
        if (sblock.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + sidechainBlockHash)
        sblock
      }
    }.map(block => {
      //serialize JSON
      val responsePayload = mapper.createObjectNode()
      val blockJson = mapper.readTree(SerializationUtil.serializeWithResult(block))
      responsePayload.put("block", blockJson.get("result"))
      responsePayload.put("hash", block.get.id)
      responsePayload.put("height",height)

      responsePayload
    })

  }


  override def getBlockByHash(hash: String): Try[ObjectNode] = {

    applyOnNodeView {sidechainNodeView =>
      Try {
        //get block by hash
        val sblock = sidechainNodeView.history.modifierById(hash.asInstanceOf[ModifierId])
        if (sblock.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + hash)
        //get block height by hash
        val height = sidechainNodeView.history.blockInfoById(hash.asInstanceOf[ModifierId]).height
        (sblock, height)
      }
    }.map(blockInfo => {
      //serialize JSON
      val responsePayload = mapper.createObjectNode()
      val blockJson = mapper.readTree(SerializationUtil.serializeWithResult(blockInfo._1))
      responsePayload.put("block", blockJson.get("result"))
      responsePayload.put("hash",hash)
      responsePayload.put("height",blockInfo._2)

      responsePayload
    })
  }

  override def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[ObjectNode] = Try {
    var lastHeight = -1
    val responsePayload = mapper.createObjectNode()

    val scInfo: SidechainSyncInfo = new SidechainSyncInfo(locatorHashes.asInstanceOf[Seq[ModifierId]])
    val headerList = applyOnNodeView {sidechainNodeView =>
      val headers = sidechainNodeView.history.continuationIds(scInfo ,limit)
      if (headers.size > 0) {
        lastHeight = sidechainNodeView.history.blockInfoById(headers.last._2.asInstanceOf[ModifierId]).height
      }
      headers
    }

    val hashes = mapper.readTree(SerializationUtil.serializeWithResult(headerList.map(el =>el._2)))
    responsePayload.put("height",lastHeight)
    responsePayload.put("hashes", hashes.get("result"))

    responsePayload
  }

  override def getMempoolTxs(txids: Seq[String]): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()

    val txs = applyOnNodeView {sidechainNodeView =>
      sidechainNodeView.pool.getAll(txids.asInstanceOf[Seq[ModifierId]])
    }

    val txsJson = mapper.readTree(SerializationUtil.serializeWithResult(txs))
    responsePayload.put("transactions",txsJson.get("result"))
    responsePayload
  }

  override def getRawMempool(): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()

    val txids = applyOnNodeView {sidechainNodeView =>
      val txs: util.ArrayList[String] = new util.ArrayList[String]()
      sidechainNodeView.pool.take(sidechainNodeView.pool.size).foreach(tx => txs.add(tx.id()))
      txs
    }

    val json = mapper.readTree(SerializationUtil.serializeWithResult(txids.toArray()))

    responsePayload.put("transactions",json.get("result"))
    responsePayload.put("size", txids.size())

    responsePayload
  }

  override def getBestBlock(): Try[ObjectNode] = Try {
    val eventPayload = mapper.createObjectNode()

    val bestBlock: (SidechainBlock, Integer) = applyOnNodeView {sidechainNodeView =>
      //get best block
      val bBlock = sidechainNodeView.history.bestBlock
      //get block height by hash
      val height = sidechainNodeView.history.getCurrentHeight
      (bBlock,height)
    }

    val blockJson = mapper.readTree(SerializationUtil.serializeWithResult(bestBlock._1))

    eventPayload.put("height",bestBlock._2)
    eventPayload.put("hash",bestBlock._1.id)
    eventPayload.put("block",blockJson.get("result"))

    eventPayload
  }

}
