package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.companion.SidechainTransactionsCompanion
import com.horizen.utils.{ByteArrayWrapper, Pair => JPair}
import java.util.{ArrayList => JArrayList}
import com.horizen.utils.Utils

import scala.collection.JavaConverters._
import sparkz.util.{ModifierId, SparkzLogging, idToBytes}
import scala.compat.java8.OptionConverters.RichOptionalGeneric
import scala.util.{Failure, Success, Try}

class SidechainWalletTransactionStorage (storage : Storage, sidechainTransactionsCompanion: SidechainTransactionsCompanion)
extends SidechainTypes
  with SidechainStorageInfo
  with SparkzLogging
{
  // Version - block Id
  // Key - byte array transaction Id
  // No remove operation

  require(storage != null, "Storage must be NOT NULL.")
  require(sidechainTransactionsCompanion != null, "SidechainTransactionsCompanion must be NOT NULL.")


  def get (transactionId : Array[Byte]) : Option[SidechainTypes#SCBT] = {
    storage.get(Utils.calculateKey(transactionId)) match {
      case v if v.isPresent => {
        sidechainTransactionsCompanion.parseBytesTry(v.get().data) match {
          case Success(transaction) => Option(transaction.asInstanceOf[SidechainTypes#SCBT])
          case Failure(exception) => {
            log.error("Error while Transaction parsing.", exception)
            Option.empty
          }
        }
      }
      case _ => Option.empty
    }
  }


  def update (version : ByteArrayWrapper, transactionUpdateList : Seq[SidechainTypes#SCBT]) : Try[SidechainWalletTransactionStorage] = Try {
    require(transactionUpdateList != null, "List of Transactions to add/update must be NOT NULL. Use empty List instead.")
    require(!transactionUpdateList.contains(null), "Transactions to add/update must be NOT NULL.")

    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    for (tx <- transactionUpdateList)
      updateList.add(new JPair[ByteArrayWrapper, ByteArrayWrapper](Utils.calculateKey(idToBytes(ModifierId @@ tx.id)),
        new ByteArrayWrapper(sidechainTransactionsCompanion.toBytes(tx))))

    storage.update(version,
      updateList,
      new JArrayList[ByteArrayWrapper]())

    this
  }

  override def lastVersionId : Option[ByteArrayWrapper] = {
    storage.lastVersionID().asScala
  }

  def rollbackVersions : List[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Try[SidechainWalletTransactionStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty

}
