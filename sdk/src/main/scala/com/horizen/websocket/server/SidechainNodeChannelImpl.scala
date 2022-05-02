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
import com.horizen.chain.FeePaymentsInfo

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
        val blockId: ModifierId = ModifierId @@ sidechainBlockHash.get
        //get block by hash
        val sblockOpt = sidechainNodeView.history.modifierById(blockId)
        if (sblockOpt.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + sidechainBlockHash)

        // get fee payments made during the block apply if exists.
        val feePaymentsInfoOpt = sidechainNodeView.history.feePaymentsInfo(blockId)
        (sblockOpt.get, feePaymentsInfoOpt)
      }
    }.map(blockInfo => {
      //serialize JSON
      calculateBlockPayload(blockInfo._1, height, blockInfo._2)
    })

  }


  override def getBlockInfoByHash(hash: String): Try[ObjectNode] = {
    applyOnNodeView { sidechainNodeView =>
      Try {
        val blockId: ModifierId = ModifierId @@ hash
        //get block by hash
        val blockOpt = sidechainNodeView.history.modifierById(blockId)
        if (blockOpt.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + hash)
        //get block height by hash
        val height = sidechainNodeView.history.blockInfoById(blockId).height
        // get fee payments made during the block apply if exists.
        val feePaymentsInfoOpt = sidechainNodeView.history.feePaymentsInfo(blockId)
        (blockOpt.get, height, feePaymentsInfoOpt)
      }
    }.map(blockInfo => {
      //serialize JSON
      calculateBlockPayload(blockInfo._1, blockInfo._2, blockInfo._3)
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

    val hashes = mapper.readTree(SerializationUtil.serialize(headerList.map(el => el._2)))
    responsePayload.put("height", lastHeight)
    responsePayload.set("hashes", hashes)

    responsePayload
  }

  override def getMempoolTxs(txids: Seq[String]): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()

    val txs = applyOnNodeView { sidechainNodeView =>
      sidechainNodeView.pool.getAll(txids.asInstanceOf[Seq[ModifierId]])
    }

    val txsJson = mapper.readTree(SerializationUtil.serialize(txs))
    responsePayload.set("transactions", txsJson)
    responsePayload
  }

  override def getRawMempool(): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()

    val txids = applyOnNodeView { sidechainNodeView =>
      val txs: util.ArrayList[String] = new util.ArrayList[String]()
      sidechainNodeView.pool.take(sidechainNodeView.pool.size).foreach(tx => txs.add(tx.id()))
      txs
    }

    val json = mapper.readTree(SerializationUtil.serialize(txids.toArray()))

    responsePayload.set("transactions", json)
    responsePayload.put("size", txids.size())

    responsePayload
  }

  override def getBestBlockInfo(): Try[ObjectNode] = Try {
    val (bestBlock: SidechainBlock, height: Int, feePaymentsInfoOpt: Option[FeePaymentsInfo]) = applyOnNodeView { sidechainNodeView =>
      //get best block
      val bBlock = sidechainNodeView.history.bestBlock
      //get block height by hash
      val height = sidechainNodeView.history.height
      // get fee payments made during the block apply if exists.
      val feePaymentsInfoOpt = sidechainNodeView.history.feePaymentsInfo(bBlock.id)
      (bBlock, height, feePaymentsInfoOpt)
    }

    calculateBlockPayload(bestBlock, height, feePaymentsInfoOpt)
  }

  override def getBlockInfo(block: SidechainBlock): Try[ObjectNode] = Try {
    val (height: Int, feePaymentsInfoOpt: Option[FeePaymentsInfo])  = applyOnNodeView { sidechainNodeView =>
        (sidechainNodeView.history.blockInfoById(block.id).height,
          sidechainNodeView.history.feePaymentsInfo(block.id))
    }

    calculateBlockPayload(block, height, feePaymentsInfoOpt)
  }

  private def calculateBlockPayload(block: SidechainBlock, height: Int, feePaymentsInfoOpt: Option[FeePaymentsInfo]): ObjectNode = {
    val eventPayload = mapper.createObjectNode()

    val blockJson = mapper.readTree(SerializationUtil.serialize(block))

    eventPayload.put("height", height)
    eventPayload.put("hash", block.id)
    eventPayload.set("block", blockJson)
    feePaymentsInfoOpt.foreach(feePaymentsInfo => {
      val feePaymentsTxJson = mapper.readTree(SerializationUtil.serialize(feePaymentsInfo.transaction))
      eventPayload.set("feePayments", feePaymentsTxJson)
    })

    eventPayload
  }
}
