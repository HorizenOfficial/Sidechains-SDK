package com.horizen.vrf

import scala.util.Try

object VrfLoader {
  val vrfStub = "com.horizen.vrf.VrfFunctionsImpl"
  val zendooImpl = "com.horizen.vrf.VrfFunctionsImplZendoo"

  lazy val vrfFunctions: VrfFunctions = loadClass(zendooImpl)

  private def loadClass(className: String): VrfFunctions = {
    val loaders: List[ClassLoader] = List(ClassLoader.getSystemClassLoader, this.getClass.getClassLoader)
    val pairs = loaders.view
      .flatMap { loader =>
        Try(loader.loadClass(className).getConstructor().newInstance().asInstanceOf[VrfFunctions]).toOption
      }

    val loadedClass = pairs.headOption.getOrElse(
      throw new RuntimeException(s"Could not load class ${className}"))

    loadedClass
  }
}
