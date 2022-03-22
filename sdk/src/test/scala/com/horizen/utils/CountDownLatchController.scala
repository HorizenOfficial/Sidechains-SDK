package com.horizen.utils

import java.util.concurrent.{CountDownLatch, TimeUnit}

// Utility class that helps to test asynchronous operations completion.
class CountDownLatchController(count: Int) {
  private var countDownLatch: CountDownLatch = new CountDownLatch(count)

  def reset(count: Int): Unit = countDownLatch = new CountDownLatch(count)

  def countDown(): Unit = countDownLatch.countDown()

  def await(ms: Long): Boolean = countDownLatch.await(ms, TimeUnit.MILLISECONDS)
}