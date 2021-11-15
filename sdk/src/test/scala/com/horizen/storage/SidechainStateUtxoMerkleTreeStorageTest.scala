package com.horizen.storage

import com.horizen.SidechainTypes
import com.horizen.fixtures.{SecretFixture, StoreFixture, TransactionFixture}
import org.junit.Test
import org.scalatest.junit.JUnitSuite
import org.scalatest.mockito.MockitoSugar

class SidechainStateUtxoMerkleTreeStorageTest
  extends JUnitSuite
    with SecretFixture
    with TransactionFixture
    with StoreFixture
    with MockitoSugar
    with SidechainTypes {

  val mockedPhysicalStorage: Storage = mock[Storage]


  @Test
  def initEmptyStorage(): Unit = {

  }

  @Test
  def initNonEmptyStorage(): Unit = {

  }

  @Test
  def storageUpdate(): Unit = {

  }

  @Test
  def storageRollback(): Unit = {

  }
}
