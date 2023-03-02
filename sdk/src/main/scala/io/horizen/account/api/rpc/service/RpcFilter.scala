package io.horizen.account.api.rpc.service

import io.horizen.account.api.rpc.types.{EthereumLogView, FilterQuery}
import io.horizen.evm.{Address, Hash}
import io.horizen.account.block.AccountBlock
import io.horizen.account.state.AccountStateView
import io.horizen.account.utils.Bloom

object RpcFilter {
  /**
   * Get all logs of a block matching the given query. Replication of the original implementation in GETH, see:
   * github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:227
   */
  def getBlockLogs(
                    stateView: AccountStateView,
                    block: AccountBlock,
                    query: FilterQuery
                  ): Seq[EthereumLogView] = {
    val filtered = query.address.length > 0 || query.topics.length > 0
    if (filtered && !testBloom(block.header.logsBloom, query.address, query.topics)) {
      // bail out if address or topic queries are given, but they fail the bloom filter test
      return Seq.empty
    }
    // retrieve all logs of the given block
    var logIndex = 0
    val logs = block.sidechainTransactions
      .map(_.id.toBytes)
      .flatMap(stateView.getTransactionReceipt)
      .flatMap(receipt =>
        receipt.consensusDataReceipt.logs.map(log => {
          val logView = new EthereumLogView(receipt, log, logIndex)
          logIndex += 1
          logView
        })
      )
    if (filtered) {
      // return filtered logs
      logs.filter(testLog(query.address, query.topics))
    } else {
      // return all logs
      logs
    }
  }

  /**
   * Tests if a bloom filter matches the given address and topic queries. Replication of the original implementation in
   * GETH, see: github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:328
   */
  def testBloom(bloom: Bloom, addresses: Array[Address], topics: Array[Array[Hash]]): Boolean = {
    // bail out if an address filter is given and none of the addresses are contained in the bloom filter
    if (addresses.length > 0 && !addresses.map(_.toBytes).exists(bloom.test)) {
      false
    } else {
      topics.forall(sub => {
        // empty rule set == wildcard, otherwise test if at least one of the given topics is contained
        sub.length == 0 || sub.map(_.toBytes).exists(bloom.test)
      })
    }
  }

  /**
   * Tests if a log matches the given address and topic queries. Replication of the original implementation in GETH,
   * see: github.com/ethereum/go-ethereum@v1.10.26/eth/filters/filter.go:293
   */
  def testLog(addresses: Array[Address], topics: Array[Array[Hash]])(log: EthereumLogView): Boolean = {
    if (addresses.length > 0 && !addresses.contains(log.address)) return false
    // skip if the number of filtered topics is greater than the amount of topics in the log
    if (topics.length > log.topics.length) return false
    topics.zip(log.topics).forall({ case (sub, topic) => sub.length == 0 || sub.contains(topic) })
  }

}
