package com.horizen.account.api.rpc.types

import com.fasterxml.jackson.annotation.JsonView
import com.horizen.serialization.Views

import java.math.BigInteger
import scala.collection.mutable

@JsonView(Array(classOf[Views.Default]))
class TxPoolContentFrom(
    val pending: mutable.SortedMap[BigInteger, EthereumTransactionView],
    val queued: mutable.SortedMap[BigInteger, EthereumTransactionView]) { }
