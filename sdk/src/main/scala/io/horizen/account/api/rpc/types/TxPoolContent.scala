package io.horizen.account.api.rpc.types

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.evm.Address
import io.horizen.json.Views

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

@JsonView(Array(classOf[Views.Default]))
class TxPoolContent(
    val pending: TrieMap[Address, mutable.SortedMap[BigInteger, EthereumTransactionView]],
    val queued: TrieMap[Address, mutable.SortedMap[BigInteger, EthereumTransactionView]]) { }
