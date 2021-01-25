package com.horizen.websocket.server

import java.util
import scorex.util.ScorexLogging
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.{ObjectMapper}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.{SidechainHistory, SidechainMemoryPool, SidechainState, SidechainWallet}
import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.proposition.Proposition

import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.util.{Try}
import com.horizen.serialization.SerializationUtil
import com.horizen.transaction.BoxTransaction
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView


class SidechainNodeChannelImpl() extends SidechainNodeChannel with ScorexLogging{

  implicit val duration: Timeout = 20 seconds
  implicit val ec:ExecutionContext = ExecutionContext.Implicits.global
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  type View = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  protected def viewAsync(): Future[View] = {
    def f(v: View) = v

    (WebSocketServerRef.sidechainNodeViewHolderRef ? GetDataFromCurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool, View](f))
      .mapTo[View]
  }


  override def getBlockByHeight(height: Int): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()
    val sidechainNodeView: View = Await.result(viewAsync(), 5000 millis)

    //get block hash by id
    sidechainNodeView.history.getBlockIdByHeight(height).ifPresent(
      sidechainBlockHash => {
        //get block by hash
        sidechainNodeView.history.getBlockById(sidechainBlockHash).ifPresent(
          sblock => {
            //serialize JSON
            val blockJson = mapper.readTree(SerializationUtil.serializeWithResult(sblock))
            responsePayload.put("block", blockJson.get("result"))
            responsePayload.put("hash",sidechainBlockHash)
            responsePayload.put("height",height)
          }
        )
      }
    )

    responsePayload
  }

  override def getBlockByHash(hash: String): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()
    val sidechainNodeView: View = Await.result(viewAsync(), 5000 millis)

    //get block by hash
    sidechainNodeView.history.getBlockById(hash).ifPresent(
      sidechainBlock => {
        val height = sidechainNodeView.history.getBlockHeightById(sidechainBlock.id).get()
        //serialize JSON
        val blockJson = mapper.readTree(SerializationUtil.serializeWithResult(sidechainBlock))
        responsePayload.put("block", blockJson.get("result"))
        responsePayload.put("hash",hash)
        responsePayload.put("height",height)
      }
    )
    responsePayload
  }

  override def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[ObjectNode] = Try {
    var lastHeight = 1
    var startBlock: SidechainBlock = null
    val responsePayload = mapper.createObjectNode()

    val sidechainNodeView: View = Await.result(viewAsync(), 5000 millis)
    //Find the best block in common
    locatorHashes.foreach(hash => {
      val optionalSidechainBlock = sidechainNodeView.history.getBlockById(hash).ifPresent(
        sblock => {
          val height = sidechainNodeView.history.getBlockHeightById(sblock.id).get()
          if(height > lastHeight) {
            lastHeight = height
            startBlock = sblock
          }
        }
      )
    })
    var startHash:String = ""
    var hashLimit = limit
    if (startBlock == null) {
      val optionalGenesisBlockHash = sidechainNodeView.history.getBlockIdByHeight(1).ifPresent(
        optionalGenesisBlockHash => {
          startHash = optionalGenesisBlockHash
          hashLimit = hashLimit - 1
        }
      )
    }else {
      startHash = startBlock.id
    }

    // Retrieve the best block + hashLimit block hashes
    val headerList: util.ArrayList[String] = new util.ArrayList[String]()
    var c = 0
    var height = lastHeight
    headerList.add(startHash)
    var found = true
    do {
      height = height + 1
      val optionalSidechainBlock = sidechainNodeView.history.getBlockIdByHeight(height)
      if (optionalSidechainBlock.isPresent) {
        val hash = optionalSidechainBlock.get()
        headerList.add(hash)
        c = c+1
      } else {
        found = false
      }
    } while (c < hashLimit && found)

    val hashes = mapper.readTree(SerializationUtil.serializeWithResult(headerList.toArray()))

    responsePayload.put("height",lastHeight)
    responsePayload.put("hashes", hashes.get("result"))

    responsePayload
  }

  override def getMempoolTxs(txids: Seq[String]): Try[ObjectNode] = Try {
    val txs: util.ArrayList[BoxTransaction[Proposition, Box[Proposition]]] = new util.ArrayList[BoxTransaction[Proposition, Box[Proposition]]]()
    val responsePayload = mapper.createObjectNode()
    val sidechainNodeView: View = Await.result(viewAsync(), 5000 millis)

    txids.foreach(txid => {
      sidechainNodeView.pool.getTransactionById(txid).ifPresent(
        tx => {
          txs.add(tx)
        }
      )
      val txsJson = mapper.readTree(SerializationUtil.serializeWithResult(txs.toArray()))
      responsePayload.put("transactions",txsJson.get("result"))
    })
    responsePayload
  }

  override def getRawMempool(): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()
    val sidechainNodeView: View = Await.result(viewAsync(), 5000 millis)

    val txids: util.ArrayList[String] = new util.ArrayList[String]()
    val mempoolTxes: util.List[BoxTransaction[Proposition, Box[Proposition]]] = sidechainNodeView.pool.getTransactions
    mempoolTxes.forEach(txs => txids.add(txs.id()))

    val json = mapper.readTree(SerializationUtil.serializeWithResult(txids.toArray()))

    responsePayload.put("transactions",json.get("result"))
    responsePayload.put("size", mempoolTxes.size())

    responsePayload
  }

  override def getBestBlock(): Try[ObjectNode] = Try {
    val eventPayload = mapper.createObjectNode()
    val sidechainNodeView: View = Await.result(viewAsync(), 5000 millis)

    val bestBlock = sidechainNodeView.history.getBestBlock
    val height = sidechainNodeView.history.getBlockHeightById(bestBlock.id).get()
    val blockJson = mapper.readTree(SerializationUtil.serializeWithResult(bestBlock))

    eventPayload.put("height",height)
    eventPayload.put("hash",bestBlock.id)
    eventPayload.put("block",blockJson.get("result"))

    eventPayload
  }

}
