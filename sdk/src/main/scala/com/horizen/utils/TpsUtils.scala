package com.horizen.utils

import com.typesafe.scalalogging.Logger

import java.time.temporal.{ChronoUnit, Temporal}

object TpsUtils {
  def getMinAndSecFromTwoDates(fromDate: Temporal, toDate: Temporal): (Long, Long) = {
    val minutes = ChronoUnit.MINUTES.between(fromDate, toDate)
    val seconds = ChronoUnit.SECONDS.between(fromDate, toDate)
    (minutes, seconds)
  }

  def log(text: String, log: Logger): Unit = log.info(s"TPS-TEST: ${text}")
}
