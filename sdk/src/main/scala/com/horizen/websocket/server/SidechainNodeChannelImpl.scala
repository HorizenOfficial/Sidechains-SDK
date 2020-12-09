package com.horizen.websocket.server

import java.util

import com.horizen.SidechainNodeViewHolder.ReceivableMessages.GetDataFromCurrentSidechainNodeView
import com.horizen.node.SidechainNodeView
import javax.websocket.{SendHandler, SendResult, Session}
import scorex.util.ScorexLogging
import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.{JsonNode, ObjectMapper}
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen.block.SidechainBlock
import com.horizen.box.Box
import com.horizen.proposition.Proposition

import scala.concurrent.duration._
import scala.concurrent.{ExecutionContext, Future}
import scala.util.Try
import com.horizen.serialization.SerializationUtil
import com.horizen.transaction.BoxTransaction

case object RESPONSE_MESSAGE extends RequestType(2)
case object EVENT_MESSAGE extends RequestType(0)
case object ERROR_MESSAGE extends RequestType(3)


class SidechainNodeChannelImpl(client: Session) extends SidechainNodeChannel with ScorexLogging{

  implicit val duration: Timeout = 20 seconds
  implicit val ec:ExecutionContext = ExecutionContext.Implicits.global
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  protected def viewAsync(): Future[SidechainNodeView] = {
    def f(v: SidechainNodeView) = v

    (WebSocketServerRef.sidechainNodeViewHolderRef ? GetDataFromCurrentSidechainNodeView(f))
      .mapTo[SidechainNodeView]
  }

  override def sendBlockByHeight(height: Int, requestId: Int, answerType: Int): Try[Unit] = Try {
    val sidechainNodeView: Future[SidechainNodeView] = viewAsync()
    sidechainNodeView.onComplete(view => {
      //get block hash by id
      val optionSidechainBlockHash = view.get.getNodeHistory.getBlockIdByHeight(height)
      if (optionSidechainBlockHash.isPresent) {
        val sblockHash = optionSidechainBlockHash.get()

        //get block by hash
        val optionalSidechainBlock = view.get.getNodeHistory.getBlockById(sblockHash)
        if (optionalSidechainBlock.isPresent) {
          val sblock = optionalSidechainBlock.get()
          //serialize JSON
          val blockJson = SerializationUtil.serializeWithResult(sblock)
          val responsePayload = mapper.createObjectNode()
          responsePayload.put("block", blockJson)
          responsePayload.put("hash",sblockHash)

          sendMessage(RESPONSE_MESSAGE.code, requestId, answerType, responsePayload)
        }else {
          sendError(requestId, answerType, 5, "Invalid parameter")
        }
      } else {
        sendError(requestId, answerType, 5, "Invalid parameter")
      }
    })
  }

  override def sendBlockByHash(hash: String, requestId: Int, answerType: Int): Try[Unit] = Try {
    val sidechainNodeView: Future[SidechainNodeView] = viewAsync()
    sidechainNodeView.onComplete(view => {
        //get block by hash
        val optionalSidechainBlock = view.get.getNodeHistory.getBlockById(hash)
        if (optionalSidechainBlock.isPresent) {
          val sblock = optionalSidechainBlock.get()
          //serialize JSON
          val blockJson = SerializationUtil.serializeWithResult(sblock)
          val responsePayload = mapper.createObjectNode()
          responsePayload.put("block", blockJson)
          responsePayload.put("hash",hash)

          sendMessage(RESPONSE_MESSAGE.code, requestId, answerType, responsePayload)
      } else {
        sendError(requestId, answerType, 5, "Invalid parameter")
      }
    })
  }

  override def sendNewBlockHashes(locatorHashes: JsonNode, limit: Int, requestId: Int, answerType: Int): Try[Unit] = Try {
    if (limit > 50) {
      sendError(requestId, 2, 4,  "Invalid limit size! Max limit is 50")
    }
    var lastHeight = 1
    var startBlock: SidechainBlock = null

    val sidechainNodeView: Future[SidechainNodeView] = viewAsync()
    sidechainNodeView.onComplete(view => {
      //Find the best block in common
      locatorHashes.forEach(hash => {
        val optionalSidechainBlock = view.get.getNodeHistory.getBlockById(hash.asText())

        if (optionalSidechainBlock.isPresent) {
          val sblock = optionalSidechainBlock.get()
          val height = view.get.getNodeHistory.getBlockHeightById(sblock.id).get()
          if(height > lastHeight) {
            lastHeight = height
            startBlock = sblock
          }
        }
      })
      var startHash:String = ""
      if (startBlock == null) {
        val optionalGenesisBlockHash = view.get.getNodeHistory.getBlockIdByHeight(1)
        if (optionalGenesisBlockHash.isPresent) {
          startHash = optionalGenesisBlockHash.get()
        }
      }else {
        startHash = startBlock.id
      }

      // Retrieve the best block + limit block hashes
      val headerList: util.ArrayList[String] = new util.ArrayList[String]()
      var c = 0
      var height = lastHeight
      headerList.add(startHash)
      var found = true
      do {
        height = height + 1
        val optionalSidechainBlock = view.get.getNodeHistory.getBlockIdByHeight(height)
        if (optionalSidechainBlock.isPresent) {
          val hash = optionalSidechainBlock.get()
          headerList.add(hash)
          c = c+1
        } else {
          found = false
        }
      } while (c < limit && found)

      val hashes = SerializationUtil.serializeWithResult(headerList.toArray())

      val responsePayload = mapper.createObjectNode()
      responsePayload.put("height",lastHeight)
      responsePayload.put("hashes", hashes)

      sendMessage(RESPONSE_MESSAGE.code,requestId, answerType, responsePayload)

    })

  }

  override def sendMempoolTxs(txids: JsonNode, requestId: Int, answerType: Int): Try[Unit] = Try {
    val sidechainNodeView: Future[SidechainNodeView] = viewAsync()
    sidechainNodeView.onComplete(view => {

      val txs: util.ArrayList[BoxTransaction[Proposition, Box[Proposition]]] = new util.ArrayList[BoxTransaction[Proposition, Box[Proposition]]]()
      if (txids.size() > 10) {
        sendError(requestId, 2, 4,  "Exceed max number of transactions (10)!")
      }
      txids.forEach(txid => {
        val tx = view.get.getNodeMemoryPool.getTransactionById(txid.asText())
        if (tx.isPresent) {
          txs.add(tx.get())
        }
      })
      val txsJson = mapper.readTree(SerializationUtil.serializeWithResult(txs.toArray()))
      val responsePayload = mapper.createObjectNode()
      responsePayload.put("transactions",txsJson.get("result"))
      sendMessage(RESPONSE_MESSAGE.code, requestId, answerType,responsePayload)
    })
  }

  override def sendRawMempool(requestId: Int, answerType:Int): Try[Unit] = Try {
    val sidechainNodeView: Future[SidechainNodeView] = viewAsync()
    sidechainNodeView.onComplete(view => {
      val txids: util.ArrayList[String] = new util.ArrayList[String]()
      val mempoolTxes: util.List[BoxTransaction[Proposition, Box[Proposition]]] = view.get.getNodeMemoryPool.getTransactions
      mempoolTxes.forEach(txs => txids.add(txs.id()))

      val responsePayload = mapper.createObjectNode()
      val json = mapper.readTree(SerializationUtil.serializeWithResult(txids.toArray()))

      responsePayload.put("transactions",json.get("result"))
      responsePayload.put("size", mempoolTxes.size())

      if(requestId == -1) {
        sendMessage(EVENT_MESSAGE.code, requestId, answerType,responsePayload)

      } else {
        sendMessage(RESPONSE_MESSAGE.code, requestId, answerType,responsePayload)
      }
    })
  }

  override def sendBestBlock(): Try[Unit] = Try {
    val sidechainNodeView: Future[SidechainNodeView] = viewAsync()
    sidechainNodeView.onComplete(view => {
      val bestBlock = view.get.getNodeHistory.getBestBlock
      val height = view.get.getNodeHistory.getBlockHeightById(bestBlock.id).get()
      val responsePayload = mapper.createObjectNode()
      responsePayload.put("height",height)
      responsePayload.put("hash",bestBlock.id)
      responsePayload.put("block",SerializationUtil.serializeWithResult(bestBlock))

      sendMessage(EVENT_MESSAGE.code, -1, 0,responsePayload)
    })
  }


  // answerType is new field added to the default mainchain websocket events because to help the Explorer to understand
  // which type of response is included in the message
  def sendMessage(msgType: Int,  requestId: Int, answerType: Int, payload: ObjectNode): Unit = {
    try {
      val json = mapper.createObjectNode()
      if (msgType == 0) { //send event message
        json.put("msgType",msgType)
        json.put("answerType",answerType)
        json.put("eventPayload",payload)
      } else { //send response message
        json.put("msgType",msgType)
        json.put("requestId",requestId)
        json.put("answerType",answerType)
        json.put("responsePayload",payload)
      }

      val message = json.toString

      client.getAsyncRemote().sendText(message, new SendHandler {
        override def onResult(sendResult: SendResult): Unit = {
          if (!sendResult.isOK) {
            log.info("Send message failed.")
          }
          else log.info("Message sent")
        }
      }
      )
    } catch {
      case e: Throwable => log.info("ERROR on sending message")
    }
  }


  def sendError(requestId: Int, answerType: Int, errorCode: Int, responsePayload: String): Unit = {
    try {
      val json = mapper.createObjectNode()
      json.put("msgType",ERROR_MESSAGE.code)
      json.put("requestId",requestId)
      json.put("answerType",answerType)
      json.put("errorCode",errorCode)
      json.put("responsePayload",responsePayload)

      val message = json.toString
      client.getAsyncRemote().sendText(message, new SendHandler {
        override def onResult(sendResult: SendResult): Unit = {
          if (!sendResult.isOK) {
            log.info("Send message failed.")
          }
          else log.info("Message sent")
        }
      }
      )
    } catch {
      case e: Throwable => log.info("ERROR on sending message")
    }
  }

}
