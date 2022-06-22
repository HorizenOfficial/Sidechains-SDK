package com.horizen.account.state

import com.google.common.primitives.{Bytes, Ints}
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.evm.{LevelDBDatabase, StateDB}
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.BytesUtils
import org.junit.rules.TemporaryFolder
import org.scalatestplus.mockito.MockitoSugar.mock

import scala.util.Random


trait MessageProcessorFixture {
  var tempFolder = new TemporaryFolder
  val mcAddr =  new MCPublicKeyHashProposition(Array.fill(20)(Random.nextInt().toByte))

  def getView: AccountStateView = {
    tempFolder.create()
    val databaseFolder = tempFolder.newFolder("evm-db" + Math.random())
    val hashNull = BytesUtils.fromHexString("0000000000000000000000000000000000000000000000000000000000000000")
    val db = new LevelDBDatabase(databaseFolder.getAbsolutePath)
    val messageProcessors: Seq[MessageProcessor] = Seq()
    val metadataStorageView: AccountStateMetadataStorageView = mock[AccountStateMetadataStorageView]
    val stateDb: StateDB = new StateDB(db, hashNull)
    new AccountStateView(metadataStorageView, stateDb, messageProcessors)
  }


  def getAddWithdrawalRequestMessage(amount: java.math.BigInteger): Message = {
     val data: Array[Byte] = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.addNewWithdrawalReqCmdSig),
      mcAddr.bytes())

    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, amount, data)
  }

  def getGetListOfWithdrawalRequestMessage(epochNum: Int): Message ={
    val data: Array[Byte] = Bytes.concat(BytesUtils.fromHexString(WithdrawalMsgProcessor.getListOfWithdrawalReqsCmdSig),
      Ints.toByteArray(epochNum))

    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, java.math.BigInteger.ZERO, data)
  }


  def getMessage(destContractAddress: AddressProposition, amount: java.math.BigInteger, data: Array[Byte]): Message ={
    val gas = java.math.BigInteger.ONE
    val nonce = java.math.BigInteger.valueOf(234)
    val from: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))

    new Message(from, destContractAddress, gas, gas, gas, gas, amount, nonce, data)

  }
}
