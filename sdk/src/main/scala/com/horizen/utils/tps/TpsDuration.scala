package com.horizen.utils.tps

class TpsDuration(val min: Long, val sec: Long, val millis: Long, val nanos: Long) {
  override def toString: String = s"$min':$sec'':$millis:$nanos"
}