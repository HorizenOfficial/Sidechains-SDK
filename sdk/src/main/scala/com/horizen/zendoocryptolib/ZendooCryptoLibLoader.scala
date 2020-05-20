package com.horizen.zendoocryptolib

import scala.util.Try

//@TODO change to dependency injection mechanism
object ZendooCryptoLibLoader {
  val vrfStub = "com.horizen.zendoocryptolib.VrfFunctionsImpl"
  val zendooImpl = "com.horizen.zendoocryptolib.VrfFunctionsImplZendoo"
  val schnorrImpl = "com.horizen.zendoocryptolib.SchnorrFunctionsImplZendoo"
  val sigProofThresholdCircuitImpl = "com.horizen.zendoocryptolib.ThresholdSignatureCircuitImplZendoo"

  lazy val vrfFunctions: VrfFunctions = loadClass(zendooImpl)
  lazy val schnorrFunctions: SchnorrFunctions = loadClass(schnorrImpl)
  lazy val sigProofThresholdCircuitFunctions: ThresholdSignatureCircuit = loadClass(sigProofThresholdCircuitImpl)

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
