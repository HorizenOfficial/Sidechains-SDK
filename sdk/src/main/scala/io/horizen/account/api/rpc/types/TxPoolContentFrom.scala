package io.horizen.account.api.rpc.types

import com.fasterxml.jackson.annotation.JsonView
import io.horizen.json.Views

import java.math.BigInteger
import scala.collection.mutable

@JsonView(Array(classOf[Views.Default]))
class TxPoolContentFrom(
    val pending: mutable.SortedMap[BigInteger, TxPoolTransaction],
    val queued: mutable.SortedMap[BigInteger, TxPoolTransaction]) { }
