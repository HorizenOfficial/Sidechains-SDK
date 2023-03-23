package io.horizen.storage.leveldb

import org.iq80.leveldb.DBFactory
import sparkz.util.SparkzLogging

import scala.util.Try

/**
 * That source code had been copied/modified from ErgoPlatform Project
 */

object LDBFactory extends SparkzLogging {

  private val nativeFactory = "org.fusesource.leveldbjni.JniDBFactory"
  private val javaFactory   = "org.iq80.leveldb.impl.Iq80DBFactory"

  lazy val factory: DBFactory = {
    val loaders = List(ClassLoader.getSystemClassLoader, this.getClass.getClassLoader)
    val factories = List(nativeFactory, javaFactory)
    val pairs = loaders.view
      .zip(factories)
      .flatMap { case (loader, factoryName) =>
        Try(loader.loadClass(factoryName).getConstructor().newInstance().asInstanceOf[DBFactory]).toOption
          .map(factoryName -> _)
      }

    val (name, factory) = pairs.headOption.getOrElse(
      throw new RuntimeException(s"Could not load any of the factory classes: $nativeFactory, $javaFactory"))

    if (name == javaFactory) {
      log.warn("Using the pure java LevelDB implementation which is still experimental")
    } else {
      log.info(s"Loaded $name with $factory")
    }

    factory
  }

}
