package com.horizen.storage

import com.horizen.companion.SidechainSecretsCompanion
import com.horizen.customtypes.{CustomPrivateKey, CustomPrivateKeySerializer}
import com.horizen.fixtures._
import com.horizen.secret._
import com.horizen.utils.{ByteArrayWrapper, BytesUtils}
import javafx.util.Pair
import java.util.{ArrayList => JArrayList, List => JList}

import org.junit.Assert._
import org.junit._
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito._

import scala.collection.JavaConverters._
import scala.collection.mutable.{ListBuffer, Map}
import org.mockito._
import org.mockito.stubbing._

class SidechainSecretStorageTest
  extends JUnitSuite
    with SecretFixture
    with IODBStoreFixture
    with MockitoSugar
{

  val mockedStorage : Storage = mock[IODBStoreAdapter]
  val secretList = new ListBuffer[Secret]()
  val storedList = new ListBuffer[Pair[ByteArrayWrapper, ByteArrayWrapper]]()

  val customSecretSerializers: Map[Byte, SecretSerializer[_ <: Secret]] =
    Map(CustomPrivateKey.SECRET_TYPE_ID ->  CustomPrivateKeySerializer.getSerializer)
  val sidechainSecretsCompanion = new SidechainSecretsCompanion(customSecretSerializers)
  val sidechainSecretsCompanionCore = new SidechainSecretsCompanion(Map())

  @Before
  def setUp() : Unit = {

    secretList ++= getSecretList(5).asScala ++ getCustomSecretList(5).asScala

    for (s <- secretList) {
      storedList.append({
        val key = new ByteArrayWrapper(s.publicImage().bytes)
        val value = new ByteArrayWrapper(sidechainSecretsCompanion.toBytes(s))
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

    val ss = new SidechainSecretStorage(mockedStorage, sidechainSecretsCompanion)

    //Test get one item.
    assertEquals("Storage must return existing Secret.", secretList(3), ss.get(secretList(3).publicImage()).get)

    //Test get items from list.
    val subList = secretList.slice(0, 2)
    assertTrue("Storage must contain specified Secrets.", ss.get(subList.map(_.publicImage()).toList).asJava.containsAll(subList.asJava))

    //Test get all items.
    assertTrue("Storage must contain all Secrets.", ss.getAll.asJava.containsAll(secretList.asJava))
  }

}
