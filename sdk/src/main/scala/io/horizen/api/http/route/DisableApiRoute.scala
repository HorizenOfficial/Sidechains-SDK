package io.horizen.api.http.route

import io.horizen.params.NetworkParams

//This trait can be used by a core API route if it needs to disable some (or all) endpoints in specific cases (e.g.
// seeder node)
trait DisableApiRoute {

  type EndpointPath = String
  type EndpointPrefix = String
  type ErrorMsg = String
  // Returns the list of endpoints that need to be disabled. It returns a sequence of tuples where first element is
  // the endpoint path prefix (e.g. "wallet", "transaction"), the second element is the endpoint path
  // (e.g. "sendTransaction") and the third is an optional message error.
  def listOfDisabledEndpoints(params: NetworkParams): Seq[(EndpointPrefix, EndpointPath, Option[ErrorMsg])]

}


object ErrorNotEnabledOnSeederNode {
  val description: String = "Invalid operation on node not supporting transactions."
}
