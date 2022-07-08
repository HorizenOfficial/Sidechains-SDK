package com.horizen.account.state

import com.horizen.account.abi
import com.horizen.account.proposition.AddressProposition
import com.horizen.account.storage.AccountStateMetadataStorageView
import com.horizen.evm.{LevelDBDatabase, StateDB}
import com.horizen.proposition.MCPublicKeyHashProposition
import com.horizen.utils.BytesUtils
import org.junit.rules.TemporaryFolder
import org.scalatestplus.mockito.MockitoSugar.mock
import org.web3j.abi.datatypes.DynamicArray
import org.web3j.abi.datatypes.generated.{Bytes20, Uint32}
import org.web3j.abi.{DefaultFunctionReturnDecoder, FunctionEncoder, TypeReference}

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


  def getAddWithdrawalRequestMessage(amount: java.math.BigInteger): Message = {
    val data = org.web3j.utils.Numeric.hexStringToByteArray(FunctionEncoder.encode(WithdrawalMsgProcessor.AddNewWithdrawalReqCmdSig,
      util.Arrays.asList(new Bytes20(mcAddr.bytes()))))

    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, amount, data)
  }

  def getGetListOfWithdrawalRequestMessage(epochNum: Int): Message = {
    val data = org.web3j.utils.Numeric.hexStringToByteArray(FunctionEncoder.encode(WithdrawalMsgProcessor.GetListOfWithdrawalReqsCmdSig, util.Arrays.asList(new Uint32(epochNum))))
    getMessage(WithdrawalMsgProcessor.fakeSmartContractAddress, java.math.BigInteger.ZERO, data)
  }

  def decodeListOfWithdrawalRequest(wrListInBytes: Array[Byte]): util.List[abi.WithdrawalRequest] = {
    val decoder = new DefaultFunctionReturnDecoder()
    val typeRef1 = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[DynamicArray[abi.WithdrawalRequest]]() {}))
    val listOfWR = decoder.decodeFunctionResult(org.web3j.utils.Numeric.toHexString(wrListInBytes), typeRef1)
    listOfWR.get(0).asInstanceOf[DynamicArray[abi.WithdrawalRequest]].getValue
  }

  def decodeWithdrawalRequest(wrInBytes: Array[Byte]): abi.WithdrawalRequest = {
    val decoder = new DefaultFunctionReturnDecoder()
    val typeRef = org.web3j.abi.Utils.convert(util.Arrays.asList(new TypeReference[abi.WithdrawalRequest]() {}))
    val wr = decoder.decodeFunctionResult(org.web3j.utils.Numeric.toHexString(wrInBytes), typeRef)
    wr.get(0).asInstanceOf[abi.WithdrawalRequest]

  }

  def getMessage(destContractAddress: AddressProposition, amount: java.math.BigInteger, data: Array[Byte]): Message = {
    val gas = java.math.BigInteger.ONE
    val nonce = java.math.BigInteger.valueOf(234)
    val from: AddressProposition = new AddressProposition(BytesUtils.fromHexString("00aabbcc9900aabbcc9900aabbcc9900aabbcc99"))

    new Message(from, destContractAddress, gas, gas, gas, gas, amount, nonce, data)

  }
}
