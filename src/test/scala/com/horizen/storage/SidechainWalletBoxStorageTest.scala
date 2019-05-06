package com.horizen.storage

import com.horizen.{WalletBox, WalletBoxSerializer}
import com.horizen.box._
import com.horizen.companion._
import com.horizen.customtypes._
import com.horizen.fixtures._
import com.horizen.proposition._
import com.horizen.utils.ByteArrayWrapper
import javafx.util.Pair
import java.util.{ArrayList => JArrayList, List => JList}

import org.junit.Assert._

import scala.collection.mutable.{ListBuffer, Map}
import org.junit._
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito._

import scala.collection.JavaConverters._
import scala.collection.mutable.Map
import org.mockito._
import org.mockito.stubbing._

class SidechainWalletBoxStorageTest
  extends JUnitSuite
    with BoxFixture
    with IODBStoreFixture
    with MockitoSugar
{

  val mockedStorage : Storage = mock[IODBStoreAdapter]
  val boxList = new ListBuffer[WalletBox]()
  val storedList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  val customBoxesSerializers: Map[Byte, BoxSerializer[_ <: Box[_ <: Proposition]]] =
    Map(CustomBox.BOX_TYPE_ID -> CustomBoxSerializer.getSerializer)
  val sidechainBoxesCompanion = new SidechainBoxesCompanion(customBoxesSerializers)
  val sidechainBoxesCompanionCore = new SidechainBoxesCompanion(Map())

  @Before
  def setUp() : Unit = {

    boxList ++= getWalletBoxList(classOf[RegularBox], 5).asScala ++ getWalletBoxList(classOf[CertifierRightBox], 5).asScala ++
      getWalletBoxList(classOf[CustomBox], 5).asScala

    for (b <- boxList) {
      storedList.append({
        val wbs = new WalletBoxSerializer(sidechainBoxesCompanion)
        val key = new ByteArrayWrapper(b.box.id())
        val value = new ByteArrayWrapper(wbs.toBytes(b))
        new Pair(key,value)
      })
    }

    Mockito.when(mockedStorage.getAll).thenReturn(storedList.asJava)
    //Mockito.when(mockedStorage.getAll).thenThrow(new Exception("Storage is not initialized."))

    Mockito.when(mockedStorage.get(ArgumentMatchers.any[ByteArrayWrapper]()))
      .thenAnswer((answer) => {
        storedList.filter(_.getKey.equals(answer.getArgument(0)))
      })

    Mockito.when(mockedStorage.get(ArgumentMatchers.anyList[ByteArrayWrapper]()))
      .thenAnswer((answer) => {
        storedList.filter((p) => answer.getArgument(0).asInstanceOf[JList[ByteArrayWrapper]].contains(p.getKey))
      })

  }

  @Test
  def testGet() : Unit = {
    val bs = new SidechainWalletBoxStorage(mockedStorage, sidechainBoxesCompanion)

    //Test get one item.
    assertEquals("Storage must return existing WalletBox.", boxList(3), bs.get(boxList(3).box.id()).get)

    //Test get items from list.
    val subList = boxList.slice(0, 2)
    assertTrue("Storage must contain specified WalletBoxes.", bs.get(subList.map(_.box.id()).toList).asJava.containsAll(subList.asJava))

    //Test get all items.
    assertTrue("Storage must contain all WalletBoxes.", bs.getAll.asJava.containsAll(boxList.asJava))

  }

}
