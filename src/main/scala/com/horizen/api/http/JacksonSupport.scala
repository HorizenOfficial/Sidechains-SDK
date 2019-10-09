package com.horizen.api.http

import akka.http.scaladsl.model.{HttpRequest, HttpResponse}
import akka.http.scaladsl.unmarshalling._
import akka.stream.Materializer
import com.fasterxml.jackson.databind._
import com.fasterxml.jackson.module.scala.DefaultScalaModule

import scala.concurrent.{ExecutionContext, Future}
import scala.concurrent.duration._
import scala.reflect.ClassTag
import scala.language.postfixOps

object JacksonSupport {

  private val mapper = new ObjectMapper().registerModule(DefaultScalaModule)

  /*implicit def JacksonMarshaller: ToEntityMarshaller[AnyRef] = {
    Marshaller.withFixedContentType(`application/json`) { obj =>
      HttpEntity(`application/json`, mapper.writeValueAsString(obj).getBytes("UTF-8"))
    }
  }*/

  implicit def JacksonRequestUnmarshaller[T <: AnyRef](implicit c: ClassTag[T]): FromRequestUnmarshaller[T] = {
    new FromRequestUnmarshaller[T]{
      override def apply(request: HttpRequest)(implicit ec: ExecutionContext, materializer: Materializer): Future[T] = {
        request.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8")).map { str =>
          mapper.readValue(str, c.runtimeClass).asInstanceOf[T]
        }
      }
    }
  }

  implicit def JacksonResponseUnmarshaller[T <: AnyRef](implicit c: ClassTag[T]): FromResponseUnmarshaller[T] = {
    new FromResponseUnmarshaller[T]{
      override def apply(response: HttpResponse)(implicit ec: ExecutionContext, materializer: Materializer): Future[T] = {
        response.entity.toStrict(5 seconds).map(_.data.decodeString("UTF-8")).map { str =>
          mapper.readValue(str, c.runtimeClass).asInstanceOf[T]
        }
      }
    }
  }

}
