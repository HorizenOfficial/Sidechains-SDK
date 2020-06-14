package com.horizen.api.http

import akka.http.scaladsl.model.HttpRequest
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.concurrent.{ExecutionContext, Future}
import scala.reflect.ClassTag
import scala.language.postfixOps

object JacksonSupport {

  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  implicit def JacksonRequestUnmarshaller[T <: AnyRef](implicit c: ClassTag[T]): FromRequestUnmarshaller[T] = {
    new FromRequestUnmarshaller[T] {
      override def apply(request: HttpRequest)(implicit ec: ExecutionContext, materializer: Materializer): Future[T] = {
        Unmarshal(request.entity).to[String].map(str => {
          if (str.isEmpty) mapper.readValue("{}", c.runtimeClass).asInstanceOf[T]
          else mapper.readValue(str, c.runtimeClass).asInstanceOf[T]
        })
      }
    }
  }
}
