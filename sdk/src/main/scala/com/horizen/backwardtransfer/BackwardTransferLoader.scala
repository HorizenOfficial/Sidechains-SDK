package com.horizen.backwardtransfer

import com.horizen.zendoocryptolib.{SchnorrFunctions, ThresholdSignatureCircuit}

import scala.util.Try

//@TODO change to dependency injection mechanism
object BackwardTransferLoader {
  lazy val schnorrFunctions: SchnorrFunctions = loadClass("com.horizen.zendoocryptolib.SchnorrFunctionsImplZendoo.java")
  lazy val sigProofThresholdCircuit: ThresholdSignatureCircuit = loadClass("com.horizen.zendoocryptolib.SigProofThresholdCircuitImplZendoo.java")

  private def loadClass[T](className: String): T = {
    val loaders: List[ClassLoader] = List(ClassLoader.getSystemClassLoader, this.getClass.getClassLoader)
    val pairs = loaders.view
      .flatMap { loader =>
        Try(loader.loadClass(className).getConstructor().newInstance().asInstanceOf[T]).toOption
      }

    val loadedClass = pairs.headOption.getOrElse(
      throw new RuntimeException(s"Could not load class ${className}"))

    loadedClass
  }
}
