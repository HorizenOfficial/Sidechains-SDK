package com.horizen.api.http

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.module.SimpleModule
import com.fasterxml.jackson.databind.{DeserializationFeature, JsonSerializer, MapperFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.horizen.serialization.Views

class JsonObjectMapper {

  private val mapper = new ObjectMapper() with ScalaObjectMapper
  private var defaultView : Class[_] = classOf[Views.Default]

  def getDefaultView : Class[_] = defaultView

  def configureObjectMapper() = {
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    //mapper.disable(MapperFeature.AUTO_DETECT_FIELDS)
    //mapper.disable(MapperFeature.AUTO_DETECT_CREATORS)
    //mapper.disable(MapperFeature.AUTO_DETECT_GETTERS)
    //mapper.disable(MapperFeature.AUTO_DETECT_SETTERS)
    mapper.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
    //mapper.enable(SerializationFeature.WRAP_ROOT_VALUE)
    mapper.enable(SerializationFeature.INDENT_OUTPUT)
    mapper.setSerializationInclusion(Include.NON_NULL)
    mapper.setSerializationInclusion(Include.NON_ABSENT)
  }

  def addCustomSerializer[C](objClass : Class[_ <: C], serializer : JsonSerializer[C]) = {
    var module = new SimpleModule()
    module.addSerializer[C](objClass, serializer)
    mapper.registerModule(module)
  }

  def serialize(value : Any, view : Class[_] = getDefaultView) : String = {
    mapper.writerWithView(view).writeValueAsString(value)
  }

  def getObjectMapper() : ObjectMapper = mapper

}
