package com.horizen.storage

import java.util.{Optional, ArrayList => JArrayList, List => JList}
import java.time.{LocalDate => JLocalDate}

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.companion.SidechainBoxesCompanion
import com.horizen.utils.{ByteArrayWrapper, ListSerializer, Pair => JPair}
import com.horizen.{OpenedWalletBox, OpenedWalletBoxSerializer, SidechainTypes}
import scorex.core.serialization.{BytesSerializable, ScorexSerializer}
import scorex.crypto.hash.Blake2b256
import scorex.util.ScorexLogging
import scorex.util.serialization.{Reader, Writer}

import scala.collection.JavaConverters._
import scala.util.{Failure, Success, Try}

class SidechainWalletBoxOperationStorage(storage : Storage)
  extends SidechainTypes
  with ScorexLogging
{
  // Version - block Id
  // Key - date
  // Value - list of box Ids created/opened
  // No remove operation

  require(storage != null, "Storage must be NOT NULL.")

  private def operationListSerializer = new ListSerializer[WalletBoxOperation](new WalletBoxOperationSerializer())

  def calculateKey(date: JLocalDate) : ByteArrayWrapper = {
    val key = Bytes.concat(Ints.toByteArray(date.getYear), Ints.toByteArray(date.getMonthValue), Ints.toByteArray(date.getDayOfMonth))
    new ByteArrayWrapper(Blake2b256.hash(key))
  }

  def get (operationDate: JLocalDate) : JList[WalletBoxOperation] = {
    storage.get(calculateKey(operationDate)) match {
      case v if v.isPresent => {
        operationListSerializer.parseBytesTry(v.get().data) match {
          case Success(walletBoxOperations) => walletBoxOperations
          case Failure(exception) => {
            log.error("Error while parsing of wallet box operations.", exception)
            new JArrayList[WalletBoxOperation]()
          }
        }
      }
      case _ => new JArrayList[WalletBoxOperation]()
    }
  }

  def update (version : ByteArrayWrapper,
              operationDate: JLocalDate,
              walletBoxOperationAppendList : Set[WalletBoxOperation]) : Try[SidechainWalletBoxOperationStorage] = Try {
    require(version != null, "Version must be NOT NULL.")
    require(operationDate != null, "Date of operation must be NOT NULL.")
    require(walletBoxOperationAppendList != null, "List of OpenedWalletBoxes to append must be NOT NULL. Use empty List instead.")
    require(!walletBoxOperationAppendList.contains(null), "OpenedWalletBox to append must be NOT NULL.")

    val removeList = new JArrayList[ByteArrayWrapper]()
    val updateList = new JArrayList[JPair[ByteArrayWrapper,ByteArrayWrapper]]()

    val walletBoxOperation = get(operationDate)

    walletBoxOperation.addAll(walletBoxOperationAppendList.asJavaCollection)

    updateList.add(new JPair(calculateKey(operationDate),
      new ByteArrayWrapper(operationListSerializer.toBytes(walletBoxOperation))))

    storage.update(version,
      updateList,
      removeList)

    this
  }

  def lastVersionId : Optional[ByteArrayWrapper] = {
    storage.lastVersionID()
  }

  def rollbackVersions : List[ByteArrayWrapper] = {
    storage.rollbackVersions().asScala.toList
  }

  def rollback (version : ByteArrayWrapper) : Try[SidechainWalletBoxOperationStorage] = Try {
    require(version != null, "Version to rollback to must be NOT NULL.")
    storage.rollback(version)
    this
  }

  def isEmpty: Boolean = storage.isEmpty

}

case class WalletBoxOperation (val operation: Byte, boxId: Array[Byte])
  extends BytesSerializable {
  override type M = WalletBoxOperation
  override def serializer: ScorexSerializer[WalletBoxOperation] = new WalletBoxOperationSerializer()
}

class WalletBoxOperationSerializer
  extends ScorexSerializer[WalletBoxOperation]
{
  def serialize(walletBoxOperation: WalletBoxOperation, writer: Writer): Unit = {
    writer.put(walletBoxOperation.operation)
    writer.putInt(walletBoxOperation.boxId.size)
    writer.putBytes(walletBoxOperation.boxId)
  }

  def parse(reader: Reader): WalletBoxOperation = {
    val operation = reader.getByte()
    val boxIdSize = reader.getInt()
    val boxId = reader.getBytes(boxIdSize)
    WalletBoxOperation(operation, boxId)
  }
}
