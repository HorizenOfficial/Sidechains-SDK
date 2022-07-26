package com.horizen.account.state

import com.horizen.account.abi
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.evm.utils.Hash
import com.horizen.evm.{LevelDBDatabase, StateDB}
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.BytesUtils
import org.junit.rules.TemporaryFolder
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.abi.datatypes.{DynamicArray, StaticStruct, Type}
import org.web3j.abi.datatypes.generated.{Bytes20, Uint32}
import org.web3j.abi.{DefaultFunctionReturnDecoder, EventEncoder, FunctionEncoder, FunctionReturnDecoder, TypeReference}

import java.util
import scala.util.Random


trait MessageProcessorFixture {
  var tempFolder = new TemporaryFolder
  val mcAddr = new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))
  val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]

  def getView: AccountStateView = {
    tempFolder.create()
    val databaseFolder = tempFolder.newFolder("evm-db" + Math.random())
    val hashNull = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")
    val db = new LevelDBDatabase(databaseFolder.getAbsolutePath)
    val messageProcessors: Seq[MessageProcessor] = Seq()
    val stateDb: StateDB = new StateDB(db, hashNull)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }

  def getMessage(destContractAddress: AddressProposition, amount: java.math.BigInteger, data: Array[Byte]): Message = {
    val gas = java.math.BigInteger.ONE
    val nonce = java.math.BigInteger.valueOf(234)
    val from: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))

    new Message(from, destContractAddress, gas, gas, gas, gas, amount, nonce, data)

  }

  def getEventSignature(eventABISignature: String): Array[Byte] =   org.web3j.utils.Numeric.hexStringToByteArray(EventEncoder.buildEventSignature(eventABISignature))

  def decodeEventTopic[T <:Type[_]] (topic: Hash, ref: TypeReference[T] ) = FunctionReturnDecoder.decodeIndexedValue(BytesUtils.toHexString(topic.toBytes),ref)

}
