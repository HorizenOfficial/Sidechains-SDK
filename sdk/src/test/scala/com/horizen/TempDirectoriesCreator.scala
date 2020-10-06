package com.horizen

import java.io.File

import scala.collection.mutable
import scala.util.Random

trait TempDirectoriesCreator {
  private val prefix = if (this.getClass.getCanonicalName != null) {
    this.getClass.getSimpleName
  } else {
    ""
  }

  private val tempDirs: mutable.Set[File] = new mutable.HashSet[File]()


  Runtime.getRuntime.addShutdownHook(new Thread() {
    override def run(): Unit = {
      tempDirs.foreach(deleteRecur)
    }
  })

  private def deleteRecur(dir: File): Unit = {
    if (dir == null) {
      return
    }
    val files: Array[File] = dir.listFiles()
    if (files != null) {
      files.foreach(deleteRecur)
    }
    val deleteResult = dir.delete()
    //println(s"Delete result for delete: ${dir} is ${deleteResult}")
  }

  private def createTempDirPath(): File = new File(System.getProperty("java.io.tmpdir") + File.separator + prefix + "-" + Math.abs(Random.nextInt()))

  private def createTempDir(): File = {
    val dir = createTempDirPath()
    dir.mkdirs()
    dir
  }

  def createNewTempDir(): File = {
    val tempDir = createTempDir()
    tempDirs.add(tempDir)
    tempDir
  }

  def createNewTempDirPath(): File = {
    val tempDir = createTempDirPath()
    tempDirs.add(tempDir)
    tempDir
  }
}
