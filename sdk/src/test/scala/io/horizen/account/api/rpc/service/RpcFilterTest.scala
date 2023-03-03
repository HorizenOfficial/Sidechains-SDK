package io.horizen.account.api.rpc.service

import com.fasterxml.jackson.databind.ObjectMapper
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import io.horizen.account.api.rpc.types.{EthereumLogView, FilterQuery}
import io.horizen.account.serialization.EthJsonMapper
import io.horizen.account.state.receipt.{EthereumConsensusDataLog, EthereumReceipt}
import io.horizen.evm.Hash
import io.horizen.json.SerializationUtil
import org.junit.Assert.assertEquals
import org.junit.Test

import java.math.BigInteger

class RpcFilterTest {

  val dataMocks = new RpcFilterDataMocks()
  private val mapper: ObjectMapper = new ObjectMapper().registerModule(DefaultScalaModule)

  @Test
  def testAddressFilter(): Unit = {
    // Filter:
    //  -address: [transactionAddress]
    // Expected result: log0, log1, log2

    var filterQuery: FilterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress.toString)), Option.empty)
    filterQuery.sanitize()
    var result: Seq[EthereumLogView] = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    var expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: [transactionAddress]
    //  -topics: []
    // Expected result: log0, log1, log2

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress.toString)), Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: [transactionAddress2]
    //  -topics: []
    // Expected result: log0, log1, log2

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress2.toString)), Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger("3"))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: [unusedTransactionAddress]
    //  -topics: []
    // Expected result: None

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.unusedTransactionAddress.toString)), Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array()
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: [transactionAddress, transactionAddress2]
    //  -topics: []
    // Expected result: log0, log1, log2, log3

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress.toString, dataMocks.transactionAddress2.toString)), Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: [transactionAddress, unusedTransactionAddress]
    //  -topics: []
    // Expected result: log0, log1, log2

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress.toString, dataMocks.unusedTransactionAddress.toString)), Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: [transactionAddress, transactionAddress2, unusedTransactionAddress]
    //  -topics: []
    // Expected result: log0, log1, log2, log3

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress.toString, dataMocks.transactionAddress2.toString, dataMocks.unusedTransactionAddress.toString)), Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))
  }

  @Test
  def testEmptyFilter(): Unit = {
    // Filter:
    //
    // Expected result: log0, log1, log2, log3

    var filterQuery: FilterQuery =  createFilterQuery(Option.empty, Option.empty)
    filterQuery.sanitize()
    var result: Seq[EthereumLogView] = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    var expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: []
    //
    // Expected result: log0, log1, log2, log3

    filterQuery =  createFilterQuery(Option.apply(Array()), Option.empty)
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -topics: []
    //
    // Expected result: log0, log1, log2, log3

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: []
    //  -topics: []
    // Expected result: log0, log1, log2, log3

    filterQuery =  createFilterQuery(Option.apply(Array()), Option.apply(Array(Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))
  }

  @Test
  def testTopicFilter(): Unit = {
    // Filter:
    //  -topics: [[t0]]
    // Expected result: log0, log1, log3

    var filterQuery: FilterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0))))
    filterQuery.sanitize()
    var result: Seq[EthereumLogView] = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    var expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result(0), expectedLogs(0), dataMocks.transactionReceipt, new BigInteger(String.valueOf(0)))
    checkLogs(result(1), expectedLogs(1), dataMocks.transactionReceipt, new BigInteger(String.valueOf(1)))
    checkLogs(result(2), expectedLogs(2), dataMocks.transactionReceipt, new BigInteger(String.valueOf(3)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t3]]
    // Expected result: log2

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic3))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog2)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(2)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t3]]
    // Expected result: log2

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic3))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog2)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(2)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0, t1]]
    // Expected result: log0, log1, log3

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0, dataMocks.transactionTopic1))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result(0), expectedLogs(0), dataMocks.transactionReceipt, new BigInteger(String.valueOf(0)))
    checkLogs(result(1), expectedLogs(1), dataMocks.transactionReceipt, new BigInteger(String.valueOf(1)))
    checkLogs(result(2), expectedLogs(2), dataMocks.transactionReceipt, new BigInteger(String.valueOf(3)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0, t1]]
    // Expected result: log0, log1, log3

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0, dataMocks.transactionTopic1))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result(0), expectedLogs(0), dataMocks.transactionReceipt, new BigInteger(String.valueOf(0)))
    checkLogs(result(1), expectedLogs(1), dataMocks.transactionReceipt, new BigInteger(String.valueOf(1)))
    checkLogs(result(2), expectedLogs(2), dataMocks.transactionReceipt, new BigInteger(String.valueOf(3)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0, t3]]
    // Expected result: log0, log1, log2, log3

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0, dataMocks.transactionTopic3))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0, t1, t3]]
    // Expected result: log0, log1, log2, log3

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0, dataMocks.transactionTopic1, dataMocks.transactionTopic3))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1, dataMocks.transactionLog2, dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0], [t1]]
    // Expected result: log0

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0), Array(dataMocks.transactionTopic1))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(0)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[], [t1]]
    // Expected result: log0

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(), Array(dataMocks.transactionTopic1))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(0)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0], [], []]]
    // Expected result: log0
    // Only log0 has 3 topics to check

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0), Array(), Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(0)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0], [], [], []]]
    // Expected result: None
    // No logs with 4 topics

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0), Array(), Array(), Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array()
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[], [], [], []]]
    // Expected result: None
    // No logs with 4 topics

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(), Array(), Array(), Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array()
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[], [], []]]
    // Expected result: log0

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0), Array(), Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(0)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    // -topics: [[t0], [], [t2]]]
    // Expected result: log0

    filterQuery =  createFilterQuery(Option.empty, Option.apply(Array(Array(dataMocks.transactionTopic0), Array(), Array())))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog0)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(2)))
  }

  @Test
  def testAddressAndTopicFilter(): Unit = {
    // Filter:
    //  -address: transactionAddress
    //  -topics: [[t0]]
    // Expected result: log0, log1

    var filterQuery: FilterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress.toString)), Option.apply(Array(Array(dataMocks.transactionTopic0))))
    filterQuery.sanitize()
    var result: Seq[EthereumLogView] = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    var expectedLogs = Array(dataMocks.transactionLog0, dataMocks.transactionLog1)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    expectedLogs.indices.foreach(i => checkLogs(result(i), expectedLogs(i), dataMocks.transactionReceipt, new BigInteger(String.valueOf(i))))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: transactionAddress
    //  -topics: [[t3]]
    // Expected result: log2

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress.toString)), Option.apply(Array(Array(dataMocks.transactionTopic3))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog2)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(2)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: transactionAddress2
    //  -topics: [[t0]]
    // Expected result: log3

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress2.toString)), Option.apply(Array(Array(dataMocks.transactionTopic0))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array(dataMocks.transactionLog3)
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
    checkLogs(result.head, expectedLogs.head, dataMocks.transactionReceipt, new BigInteger(String.valueOf(3)))

    //////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////////

    // Filter:
    //  -address: transactionAddress2
    //  -topics: [[t1]]
    // Expected result: None

    filterQuery =  createFilterQuery(Option.apply(Array(dataMocks.transactionAddress2.toString)), Option.apply(Array(Array(dataMocks.transactionTopic1))))
    filterQuery.sanitize()
    result = RpcFilter.getBlockLogs(dataMocks.getNodeStateMock, dataMocks.mockedBlock, filterQuery)

    expectedLogs = Array()
    assertEquals(f"Expected to receive ${expectedLogs.length} logs", expectedLogs.length, result.size)
  }

  private def createFilterQuery(addresses: Option[Array[String]], topics: Option[Array[Array[Hash]]]): FilterQuery = {
    val jsonFilterQuery = mapper.createObjectNode()
    if (addresses.isDefined)
      jsonFilterQuery.set("address", mapper.readTree(SerializationUtil.serialize(addresses.get)))
    if (topics.isDefined)
      jsonFilterQuery.set("topics", mapper.readTree(SerializationUtil.serialize(topics.get)))

    EthJsonMapper.deserialize(jsonFilterQuery.toString, classOf[FilterQuery])
  }

  private def checkLogs(result: EthereumLogView, expectedLog: EthereumConsensusDataLog, expectedTransactionReceipt: EthereumReceipt, logIndex: BigInteger): Unit = {
    assertEquals("Wrong log data", result.data, expectedLog.data)
    assertEquals("Wrong log logIndex", result.logIndex, logIndex)
    assertEquals("Wrong log blockHash", result.blockHash, new Hash(expectedTransactionReceipt.blockHash))
    assertEquals("Wrong log blockNumber", result.blockNumber, BigInteger.valueOf(expectedTransactionReceipt.blockNumber))
    assertEquals("Wrong log transactionHash", result.transactionHash, new Hash(expectedTransactionReceipt.transactionHash))
    assertEquals("Wrong log transactionIndex", result.transactionIndex, BigInteger.valueOf(expectedTransactionReceipt.transactionIndex))
    assertEquals("Wrong log topics", result.topics, expectedLog.topics)
  }
}
