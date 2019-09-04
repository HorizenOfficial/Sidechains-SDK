package com.horizen.api.http

import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.ser.std.StdSerializer
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonSerializer, MapperFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.horizen.serialization.Views

object JsonUtil {

  val mapper = new ObjectMapper() with ScalaObjectMapper
  mapper.registerModule(DefaultScalaModule)
  mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
  //mapper.disable(MapperFeature.AUTO_DETECT_FIELDS)
  //mapper.disable(MapperFeature.AUTO_DETECT_CREATORS)
  //mapper.disable(MapperFeature.AUTO_DETECT_GETTERS)
  //mapper.disable(MapperFeature.AUTO_DETECT_SETTERS)
  mapper.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
  //mapper.enable(SerializationFeature.WRAP_ROOT_VALUE)
  mapper.enable(SerializationFeature.INDENT_OUTPUT)

  def addCustomSerializer[C](objClass : Class[_ <: C], serializer : JsonSerializer[C]) = {
    var module = new SimpleModule()
    module.addSerializer[C](objClass, serializer)
    mapper.registerModule(module)
  }

  def toJson(value : Any) : String = {
    mapper.writerWithView(classOf[Views.Default]).writeValueAsString(value)
  }

  def toJson(value : Any, view : Class[_]) : String = {
    mapper.writerWithView(view).writeValueAsString(value)
  }

}
