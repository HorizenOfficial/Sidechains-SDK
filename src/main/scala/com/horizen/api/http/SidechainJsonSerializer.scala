package com.horizen.api.http

import com.fasterxml.jackson.annotation.JsonInclude.Include
import com.fasterxml.jackson.databind.{DeserializationFeature, MapperFeature, ObjectMapper, SerializationFeature}
import com.fasterxml.jackson.module.scala.DefaultScalaModule
import com.fasterxml.jackson.module.scala.experimental.ScalaObjectMapper
import com.horizen.serialization.Views

class SidechainJsonSerializer {

  private val mapper = new ObjectMapper() with ScalaObjectMapper
  private var defaultView : Class[_] = classOf[Views.Default]

  def getDefaultView : Class[_] = defaultView

  def setDeafultView(view : Class[_]) : Unit = {
    defaultView = view
  }

  def setDefaultConfiguration() = {
    mapper.registerModule(DefaultScalaModule)
    mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
    mapper.disable(MapperFeature.DEFAULT_VIEW_INCLUSION)
    mapper.enable(SerializationFeature.INDENT_OUTPUT)
    mapper.setSerializationInclusion(Include.NON_NULL)
    mapper.setSerializationInclusion(Include.NON_ABSENT)
  }

  def serialize(value : Any, view : Class[_] = getDefaultView) : String = {
    mapper.writerWithView(view).writeValueAsString(value)
  }

  def getObjectMapper() : ObjectMapper = mapper

}
