package com.horizen.companion

import com.google.common.primitives.Bytes
import com.horizen.transaction._
import scorex.core.serialization.Serializer

import scala.util.{Failure, Try}


case class SidechainTransactionsCompanion(customTransactionSerializers: Map[Byte, TransactionSerializer[_ <: Transaction]])
    extends Serializer[Transaction] {

  val coreTransactionSerializers: Map[Byte, TransactionSerializer[_ <: Transaction]] =
    // TODO: uncomment, when Serizalizers will be placed to separate files
    Map(RegularTransaction.TRANSACTION_TYPE_ID -> RegularTransactionSerializer.getSerializer,
        MC2SCAggregatedTransaction.TRANSACTION_TYPE_ID -> MC2SCAggregatedTransactionSerializer.getSerializer)
        //WithdrawalRequestTransaction.TRANSACTION_TYPE_ID -> WithdrawalRequestTransaction.()
        //CertifierUnlockRequestTransaction.TRANSACTION_TYPE_ID -> new CertifierUnlockRequestTransactionSerializer())

  val CUSTOM_TRANSACTION_TYPE : Byte = Byte.MaxValue // TO DO: think about proper value

  override def toBytes(tx: Transaction): Array[Byte] = {
    tx match {
      case t: RegularTransaction => Bytes.concat(Array(tx.transactionTypeId()),
        RegularTransactionSerializer.getSerializer.toBytes(t))
      case t: MC2SCAggregatedTransaction => Bytes.concat(Array(tx.transactionTypeId()),
        MC2SCAggregatedTransactionSerializer.getSerializer.toBytes(t))
      case _ => customTransactionSerializers.get(tx.transactionTypeId()) match {
        case Some(serializer) => Bytes.concat(Array(CUSTOM_TRANSACTION_TYPE), Array(tx.transactionTypeId()),
          serializer.asInstanceOf[Serializer[Transaction]].toBytes(tx))
        case None => throw new IllegalArgumentException("Unknown transaction type - " + tx)
      }
    }
  }

  override def parseBytes(bytes: Array[Byte]): Try[Transaction] = {
    val transactionType = bytes(0)
    transactionType match {
      case `CUSTOM_TRANSACTION_TYPE` => customTransactionSerializers.get(bytes(1)) match {
        case Some(b) => b.parseBytes(bytes.drop(2))
        case None => Failure(new MatchError("Unknown custom transaction type id"))
      }
      case _ => coreTransactionSerializers.get(transactionType) match {
        case Some(b) => b.parseBytes(bytes.drop(1))
        case None => Failure(new MatchError("Unknown core transaction type id"))
      }
    }
  }
}
