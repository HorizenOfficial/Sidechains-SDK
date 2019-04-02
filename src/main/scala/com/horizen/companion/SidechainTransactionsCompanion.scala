package com.horizen.companion

import com.google.common.primitives.Bytes
import com.horizen.transaction._
import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.{Failure, Try}
// import com.google.common.primitives.Bytes;


case class SidechainTransactionsCompanion(customTransactionSerializers: Map[Byte, TransactionSerializer[_ <: Transaction]])
    extends Serializer[Transaction] {

  val coreTransactionSerializers: Map[Byte, TransactionSerializer[_ <: Transaction]] =
    // TO DO: uncomment, when Serizalizers will be placed to separate files
    Map(RegularTransaction.TRANSACTION_TYPE_ID -> RegularTransactionSerializer.getSerializer,
        MC2SCAggregatedTransaction.TRANSACTION_TYPE_ID -> MC2SCAggregatedTransactionSerializer.getSerializer)
        //WithdrawalRequestTransaction.TRANSACTION_TYPE_ID -> new WithdrawalRequestTransactionSerializer()
        //CertifierUnlockRequestTransaction.TRANSACTION_TYPE_ID -> new CertifierUnlockRequestTransactionSerializer())

  val customTransactionType : Byte = Byte.MaxValue // TO DO: think about proper value

  override def toBytes(tx: Transaction): Array[Byte] = {
    tx match {
        // TO DO: look into SimpleBoxTransaction in Treasury POC
      case t: RegularTransaction => Bytes.concat(Array(tx.transactionTypeId()),
        tx.serializer().asInstanceOf[TransactionSerializer[Transaction]].toBytes(tx))
      case t: MC2SCAggregatedTransaction => Bytes.concat(Array(tx.transactionTypeId()),
        tx.serializer().asInstanceOf[TransactionSerializer[Transaction]].toBytes(tx))
      case t: WithdrawalRequestTransaction => Bytes.concat(Array(tx.transactionTypeId()),
        tx.serializer().asInstanceOf[TransactionSerializer[Transaction]].toBytes(tx))
      case t: CertifierUnlockRequestTransaction => Bytes.concat(Array(tx.transactionTypeId()),
        tx.serializer().asInstanceOf[TransactionSerializer[Transaction]].toBytes(tx))
      case _ => Bytes.concat(Array(customTransactionType), Array(tx.transactionTypeId()),
        tx.serializer().asInstanceOf[TransactionSerializer[Transaction]].toBytes(tx))
    }
  }

  override def parseBytes(bytes: Array[Byte]): Try[Transaction] = {
    val transactionType = bytes(0)
    transactionType match {
      case `customTransactionType` => customTransactionSerializers.get(bytes(1)) match {
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
