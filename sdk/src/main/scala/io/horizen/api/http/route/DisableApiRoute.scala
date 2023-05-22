package io.horizen.api.http.route

import akka.http.scaladsl.server.{PathMatcher0, Route}
import io.horizen.api.http.{ApiResponseUtil, ErrorResponse}
import sparkz.core.api.http.ApiRoute
import java.util.{Optional => JOptional}

trait DisableApiRoute extends ApiRoute {
  private def disabledEndpoint: Route =  (post & path(matches(listOfDisabledEndpoints))){
    ApiResponseUtil.toResponse(ErrorNotEnabledOnSeederNode())
  }

  def listOfDisabledEndpoints: Seq[String]
  protected val myPathPrefix: String

  abstract override def route: Route = pathPrefix(myPathPrefix) {
    disabledEndpoint
  } ~ super.route

  private def matches(endpointsNames: Seq[String]): PathMatcher0 = {
    require(endpointsNames.nonEmpty, "List of endpoint names cannot be empty")
    endpointsNames.tail.foldLeft[PathMatcher0](endpointsNames.head){
      (res, tmp) => res | tmp
    }
  }

}


case class ErrorNotEnabledOnSeederNode() extends ErrorResponse {
  override val description: String = "Invalid operation on node not supporting transactions."
  override val code: String = "1111"
  override val exception: JOptional[Throwable] = JOptional.empty()
}
