package com.horizen.websocket.server

import akka.pattern.ask
import akka.util.Timeout
import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.databind.node.ObjectNode
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.horizen._
import com.horizen.account.block.AccountBlock
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state.AccountState
import com.horizen.account.wallet.AccountWallet
import com.horizen.block.{SidechainBlock, SidechainBlockBase, SidechainBlockHeaderBase}
import com.horizen.chain.{AbstractFeePaymentsInfo, SidechainFeePaymentsInfo}
import com.horizen.serialization.SerializationUtil
import com.horizen.websocket.server.WebSocketServerRef.sidechainNodeViewHolderRef
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.NodeViewHolder.ReceivableMessages.GetDataFromCurrentView

import java.util
import scala.concurrent.duration._
import scala.concurrent.{Await, ExecutionContext, Future}
import scala.language.postfixOps
import scala.util.Try

// TODO must be tested for Account model
class SidechainNodeChannelImpl() extends SidechainNodeChannel with ScorexLogging {

  implicit val duration: Timeout = 20 seconds
  implicit val ec: ExecutionContext = ExecutionContext.Implicits.global
  type utxoView = CurrentView[SidechainHistory, SidechainState, SidechainWallet, SidechainMemoryPool]
  type accountView = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]
  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  override def getBlockInfoByHeight(height: Int): Try[ObjectNode] = {
    applyOnNodeView { sidechainNodeView =>
      Try {
        val nodeView =
          if (sidechainNodeView.isInstanceOf[utxoView]) sidechainNodeView.asInstanceOf[utxoView]
          else sidechainNodeView.asInstanceOf[accountView]
        val history = nodeView.history match {
          case history: SidechainHistory => history
          case history: AccountHistory => history
        }
        // get block hash by id
        val sidechainBlockHash: Option[String] = history.blockIdByHeight(height)
        if (sidechainBlockHash.isEmpty) throw new IllegalStateException(s"Block not found for height: " + height)
        val blockId: ModifierId = ModifierId @@ sidechainBlockHash.get
        // get block by hash
        val sblockOpt = history.modifierById(blockId)
        if (sblockOpt.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + sidechainBlockHash)

        // get fee payments made during the block apply if exists.
        // TODO must be generalized for Account model

        val feePaymentsInfoOpt = history.feePaymentsInfo(blockId)
        (sblockOpt.get, feePaymentsInfoOpt)
      }
    }.map(blockInfo => {
      // serialize JSON
      calculateBlockPayload(
        blockInfo._1.asInstanceOf[SidechainBlockBase[_, SidechainBlockHeaderBase]],
        height,
        blockInfo._2.asInstanceOf[Option[AbstractFeePaymentsInfo]]
      )
    })

  }

  override def getBlockInfoByHash(hash: String): Try[ObjectNode] = {
    applyOnNodeView { sidechainNodeView =>
      Try {
        val nodeView =
          if (sidechainNodeView.isInstanceOf[utxoView]) sidechainNodeView.asInstanceOf[utxoView]
          else sidechainNodeView.asInstanceOf[accountView]
        val history = nodeView.history match {
          case history: SidechainHistory => history
          case history: AccountHistory => history
        }
        val blockId: ModifierId = ModifierId @@ hash
        // get block by hash
        val blockOpt = history.modifierById(blockId)
        if (blockOpt.isEmpty) throw new IllegalStateException(s"Block not found for hash: " + hash)
        // get block height by hash
        val height = history.blockInfoById(blockId).height
        // get fee payments made during the block apply if exists.
        val feePaymentsInfoOpt = history.feePaymentsInfo(blockId)
        (blockOpt.get, height, feePaymentsInfoOpt)
      }
    }.map(blockInfo => {
      // serialize JSON
      calculateBlockPayload(
        blockInfo._1.asInstanceOf[SidechainBlockBase[_, SidechainBlockHeaderBase]],
        blockInfo._2,
        blockInfo._3.asInstanceOf[Option[AbstractFeePaymentsInfo]]
      )
    })
  }

  override def getNewBlockHashes(locatorHashes: Seq[String], limit: Int): Try[ObjectNode] = Try {
    var lastHeight = -1
    val responsePayload = mapper.createObjectNode()

    val scInfo: SidechainSyncInfo = SidechainSyncInfo(locatorHashes.asInstanceOf[Seq[ModifierId]])
    val headerList = applyOnNodeView { sidechainNodeView =>
      val nodeView =
        if (sidechainNodeView.isInstanceOf[utxoView]) sidechainNodeView.asInstanceOf[utxoView]
        else sidechainNodeView.asInstanceOf[accountView]
      val history = nodeView.history match {
        case history: SidechainHistory => history
        case history: AccountHistory => history
      }
      val headers = history.continuationIds(scInfo, limit)
      if (headers.nonEmpty) {
        lastHeight = history.blockInfoById(headers.last._2).height
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
      val nodeView =
        if (sidechainNodeView.isInstanceOf[utxoView]) sidechainNodeView.asInstanceOf[utxoView]
        else sidechainNodeView.asInstanceOf[accountView]
      val pool = nodeView.pool match {
        case pool: SidechainMemoryPool => pool
        case pool: AccountMemoryPool => pool
      }
      pool.getAll(txids.asInstanceOf[Seq[ModifierId]])
    }

    val txsJson = mapper.readTree(SerializationUtil.serialize(txs))
    responsePayload.set("transactions", txsJson)
    responsePayload
  }

  override def getRawMempool(): Try[ObjectNode] = Try {
    val responsePayload = mapper.createObjectNode()

    val txids = applyOnNodeView { sidechainNodeView =>
      val txs: util.ArrayList[String] = new util.ArrayList[String]()
      val nodeView = if (sidechainNodeView.isInstanceOf[utxoView]) {
        sidechainNodeView.asInstanceOf[utxoView]
      } else {
        sidechainNodeView.asInstanceOf[accountView]
      }
      val pool = nodeView.pool match {
        case pool: SidechainMemoryPool => pool
        case pool: AccountMemoryPool => pool
      }
      pool
        .take(pool.size)
        .foreach(tx =>
          txs.add(tx match {
            case tx: SidechainTypes#SCBT => tx.id
            case tx: SidechainTypes#SCAT => tx.id
            case _ => throw new IllegalStateException("Transaction type not supported")
          })
        )
      txs
    }

    val json = mapper.readTree(SerializationUtil.serialize(txids.toArray()))

    responsePayload.set("transactions", json)
    responsePayload.put("size", txids.size())

    responsePayload
  }

  override def getBestBlockInfo(): Try[ObjectNode] = Try {
    val (bestBlock: SidechainBlock, height: Int, feePaymentsInfoOpt: Option[SidechainFeePaymentsInfo]) =
      applyOnNodeView { sidechainNodeView =>
        val nodeView =
          if (sidechainNodeView.isInstanceOf[utxoView]) {
            sidechainNodeView.asInstanceOf[utxoView]
          } else {
            sidechainNodeView.asInstanceOf[accountView]
          }
        val history = nodeView.history match {
          case history: SidechainHistory => history
          case history: AccountHistory => history
        }
        // get best block
        val bBlock = history.bestBlock match {
          case block: SidechainBlock => block
          case block: AccountBlock => block
          case _ => throw new IllegalStateException("Unknown block type")
        }
        // get block height by hash
        val height = history.height
        // get fee payments made during the block apply if exists.
        val feePaymentsInfoOpt = history.feePaymentsInfo(bBlock.id)
        (bBlock, height, feePaymentsInfoOpt)
      }

    calculateBlockPayload(
      bestBlock.asInstanceOf[SidechainBlockBase[_, SidechainBlockHeaderBase]],
      height,
      feePaymentsInfoOpt
    )
  }

  override def getBlockInfo(block: SidechainBlock): Try[ObjectNode] = Try {
    val (height: Int, feePaymentsInfoOpt: Option[AbstractFeePaymentsInfo]) = applyOnNodeView { sidechainNodeView =>
      val nodeView =
        if (sidechainNodeView.isInstanceOf[utxoView]) sidechainNodeView.asInstanceOf[utxoView]
        else sidechainNodeView.asInstanceOf[accountView]
      val history = nodeView.history match {
        case history: SidechainHistory => history
        case history: AccountHistory => history
      }
      (history.blockInfoById(block.id).height, history.feePaymentsInfo(block.id))
    }

    calculateBlockPayload(
      block.asInstanceOf[SidechainBlockBase[_, SidechainBlockHeaderBase]],
      height,
      feePaymentsInfoOpt
    )
  }

  private def calculateBlockPayload(
      block: Any,
      height: Int,
      feePaymentsInfoOpt: Option[AbstractFeePaymentsInfo]
  ): ObjectNode = {
    val blockWithType = block match {
      case block: SidechainBlock => block
      case block: AccountBlock => block
      case _ => throw new IllegalStateException("Block type not supported")
    }
    val eventPayload = mapper.createObjectNode()

    val blockJson = mapper.readTree(SerializationUtil.serialize(blockWithType))

    eventPayload.put("height", height)
    eventPayload.put("hash", blockWithType.id)
    eventPayload.set("block", blockJson)
    feePaymentsInfoOpt.foreach(feePaymentsInfo => {
      val feePaymentsTxJson =
        mapper.readTree(SerializationUtil.serialize(feePaymentsInfo.asInstanceOf[SidechainFeePaymentsInfo].transaction))
      eventPayload.set[ObjectNode]("feePayments", feePaymentsTxJson)
    })

    eventPayload
  }

  def applyOnNodeView[R](functionToBeApplied: Any => R): R = {
    try {
      val f = functionToBeApplied match {
        case utxoFunction: (utxoView => R) => utxoFunction
        case accountFunction: (accountView => R) => accountFunction
        case _ => throw new IllegalStateException("Function type not supported")
      }
      val res = (sidechainNodeViewHolderRef ? GetDataFromCurrentView(f)).asInstanceOf[Future[R]]
      val result = Await.result(res, 5000 millis)
      result
    } catch {
      case e: Exception => throw new Exception(e)
    }
  }
}
