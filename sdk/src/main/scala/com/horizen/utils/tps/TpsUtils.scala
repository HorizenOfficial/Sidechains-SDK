package com.horizen.utils.tps

import com.typesafe.scalalogging.Logger

import java.time.Duration
import java.time.temporal.Temporal

object TpsUtils {
  var forkCounter: Int = 0

  def getMinAndSecFromTwoDates(fromDate: Temporal, toDate: Temporal): TpsDuration = {
    val duration = Duration.between(fromDate, toDate)
    new TpsDuration(duration.toMinutes, duration.toSeconds, duration.toMillis, duration.toNanos)
  }

  def log(text: String, log: Logger): Unit = log.info(s"TPS-TEST: ${text}")
}

