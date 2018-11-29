import scorex.core.ModifierTypeId
import scorex.core.serialization.Serializer

import scala.util.{Failure, Try}
// import com.google.common.primitives.Bytes;


case class SidechainTransactionsCompanion(customTransactionSerializers: Map[scorex.core.ModifierTypeId, TransactionSerializer[_ <: Transaction]])
    extends Serializer[Transaction] {

  val coreTransactionSerializers: Map[scorex.core.ModifierTypeId, TransactionSerializer[_ <: Transaction]] =
    Map(new RegularTransaction().transactionTypeId() -> new RegularTransactionSerializer(),
      new ForwardTransaction().transactionTypeId() -> new ForwardTransactionSerializer(),
      new BackwardTransaction().transactionTypeId() -> new BackwardTransactionSerializer())

  val customTransactionId = ModifierTypeId @@ 0xFF // TODO: think about proper value

  override def toBytes(tx: Transaction): Array[Byte] = {
    tx match {
      case t: RegularTransaction => Bytes.concat(Array(tx.transactionTypeId), new RegularTransactionSerializer().toBytes(t))
      case t: ForwardTransaction => Bytes.concat(Array(tx.transactionTypeId), new ForwardTransactionSerializer().toBytes(t))
      case t: BackwardTransaction => Bytes.concat(Array(tx.transactionTypeId), new BackwardTransactionSerializer().toBytes(t))
      case _ => {
        customTransactionSerializers.get(tx.transactionTypeId()) match {
          case Some(s) => Bytes.concat(Array(customTransactionId), Array(tx.transactionTypeId()), s.toBytes(tx));
          case None => null // TO DO: process "missed serializer error"
        }
      }
    }
  }

  override def parseBytes(bytes: Array[Byte]): Try[Transaction] = {
    val transactionTypeId = ModifierTypeId @@ bytes(0)
    coreTransactionSerializers.get(transactionTypeId) match {
      case Some(s) => s.parseBytes(bytes.drop(1))
      case None => {
        if(customTransactionId == transactionTypeId) {
          val sidechainBytes = bytes.drop(1)
          val sidechainTransactionTypeId = ModifierTypeId @@ sidechainBytes(0)
          customTransactionSerializers.get(sidechainTransactionTypeId) match {
            case Some(s) => s.parseBytes(sidechainBytes.drop(1))
            case None => Failure(new MatchError("Unknown custom transaction type id"))
          }
        } else {
          Failure(new MatchError("Unknown transaction type id"))
        }
      }
    }
  }
}
