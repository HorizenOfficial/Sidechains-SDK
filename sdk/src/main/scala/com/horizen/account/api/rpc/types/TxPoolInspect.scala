package com.horizen.account.api.rpc.types

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views
import io.horizen.evm.Address

import java.math.BigInteger
import scala.collection.concurrent.TrieMap
import scala.collection.mutable

@JsonView(Array(classOf[Views.Default]))
class TxPoolInspect(
    val pending: TrieMap[Address, mutable.SortedMap[BigInteger, String]],
    val queued: TrieMap[Address, mutable.SortedMap[BigInteger, String]]) { }
