package com.horizen.backwardtransfer

import scala.util.Try

object BackwardTransferLoader {
  lazy val schnorrFunctions: BackwardTransferFunctions = loadClass("com.horizen.schnorr.SchnorrFunctionsImpl")

  private def loadClass(className: String): BackwardTransferFunctions = {
    val loaders: List[ClassLoader] = List(ClassLoader.getSystemClassLoader, this.getClass.getClassLoader)
    val pairs = loaders.view
      .flatMap { loader =>
        Try(loader.loadClass(className).getConstructor().newInstance().asInstanceOf[BackwardTransferFunctions]).toOption
      }

    val loadedClass = pairs.headOption.getOrElse(
      throw new RuntimeException(s"Could not load class ${className}"))

    loadedClass
  }
}
