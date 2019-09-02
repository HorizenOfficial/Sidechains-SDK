package com.horizen.api.http

import com.fasterxml.jackson.databind.{DeserializationFeature, MapperFeature, ObjectMapper, SerializationFeature}
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
  mapper.disable(MapperFeature.DEFAULT_VIEW_INCLUSION);
  mapper.enable(SerializationFeature.INDENT_OUTPUT)

  def toJson(value : Any) : String = {
    mapper.writerWithView(classOf[Views.Default]).writeValueAsString(value)
  }

  def toJsonWithCustomView(value : Any, view : Class[_]) : String = {
    mapper.writerWithView(view).writeValueAsString(value)
  }

}
