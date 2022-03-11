package com.horizen.storage.performance

import org.junit.Ignore
import org.scalatestplus.junit.JUnitSuite

class StoragePerformanceTest extends JUnitSuite {
  private val performanceConfig =
    new StoragePerformanceTestConfig(
      storageCreationUpdatingBatchSize = 1 * 1024 * 1024,
      storageCreationMeasureStep = 20,
      measureIterationsCount = 10,
      readingBatchingSizesInKb = Seq(1, 1, 1, 5, 5, 5, 10, 10, 10, 50, 50, 50, 100, 100, 100, 200, 200, 200, 400, 400, 800, 800, 1024, 2048, 2048, 4096),
      writingBatchingSizesInKb = Seq(1, 1, 1, 5, 5, 5, 10, 10, 10, 50, 50, 50, 100, 100, 200, 400, 400, 800, 800, 1024, 2048, 2048, 4096)) {

      override def storageGenerationSettings: Seq[(Int, StorageDataGenerator)] = {
        val trSettings = Seq(
          (50000000, TransactionStorageDataGenerator(3 to 7, 3 to 7, 255 * 255)),
          (10000000, TransactionStorageDataGenerator(3 to 7, 3 to 7, 255 * 255)),
          (10000000, TransactionStorageDataGenerator(3 to 7, 3 to 7, 255 * 255)),
          (10000000, TransactionStorageDataGenerator(30 to 70, 30 to 70, 255 * 255)),
          (1000000, TransactionStorageDataGenerator(300 to 700, 300 to 700, 255 * 255)),
          (1000000, TransactionStorageDataGenerator(300 to 700, 300 to 700, 255 * 255)),
          (1000000, TransactionStorageDataGenerator(700 to 900, 700 to 900, 255 * 255))
        )

        val byteDataSettings = Seq(
          (10000, ByteDataGenerator(1024 * 1024 to 2 * 1024 * 1024, 255 * 255)),
          (10000, ByteDataGenerator(1024 * 1024 to 2 * 1024 * 1024, 255 * 255)),
          (10000, ByteDataGenerator(1024 * 1024 to 2 * 1024 * 1024, 255 * 255)),
          (20000, ByteDataGenerator(1024 * 1023 to 1 * 1024 * 1024, 255 * 255)),
          (30000, ByteDataGenerator(1024 * 1023 to 1 * 1024 * 1024, 255 * 255))
        )

        trSettings ++ byteDataSettings
      }
    }

  val runner = new StoragePerformanceRunner(performanceConfig)

  def printHeader(): Unit = {
    TimeMeasure.header.foreach(column => print(s"$column\t"))
    println()
  }

  @Ignore
  def runMeasures(): Unit = {
    runner.measurePerformance().foreach{
      case(storageDescription, results) =>
        println(storageDescription)
        printHeader()
        results.foreach(println(_))
        println()
    }
  }


}
