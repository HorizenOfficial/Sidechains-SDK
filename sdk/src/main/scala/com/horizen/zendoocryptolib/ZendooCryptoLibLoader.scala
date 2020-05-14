package com.horizen.zendoocryptolib

import scala.util.Try

//@TODO change to dependency injection mechanism
object ZendooCryptoLibLoader {
  val vrfStub = "com.horizen.zendoocryptolib.VrfFunctionsImpl"
  val zendooImpl = "com.horizen.zendoocryptolib.VrfFunctionsImplZendoo"

  lazy val vrfFunctions: VrfFunctions = loadClass(zendooImpl)
  lazy val schnorrFunctions: SchnorrFunctions = loadClass("com.horizen.zendoocryptolib.SchnorrFunctionsImplZendoo.java")
  lazy val sigProofThresholdCircuitFunctions: ThresholdSignatureCircuit = loadClass("com.horizen.zendoocryptolib.SigProofThresholdCircuitImplZendoo.java")

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
