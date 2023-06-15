package io.horizen.account.api.rpc.service

import scala.util.Try

object RpcUtils {

  def getClientVersion: String = {
    val default = "dev"
    val architecture = Try(System.getProperty("os.arch")).getOrElse(default)
    val javaVersion = Try(System.getProperty("java.specification.version")).getOrElse(default)
    val sdkPackage = this.getClass.getPackage
    val sdkTitle = sdkPackage.getImplementationTitle match {
      case null => default
      case title => Try(title.split(":")(1)).getOrElse(title)
    }
    val sdkVersion = sdkPackage.getImplementationVersion
    s"$sdkTitle/$sdkVersion/$architecture/jdk$javaVersion"
  }
}
