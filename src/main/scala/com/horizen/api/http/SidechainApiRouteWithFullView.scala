package com.horizen.api.http

import java.lang.reflect.Method
import java.util

import akka.http.scaladsl.server.{Route, RouteConcatenation}
import com.horizen.api.ApiCallRequest
import com.horizen.node.{NodeHistory, NodeMemoryPool, NodeState, NodeWallet}
import scorex.core.NodeViewHolder
import scorex.core.NodeViewHolder.CurrentView
import scorex.core.api.http.{ApiResponse, ApiRoute, ApiRouteWithFullView}

trait SidechainApiRouteWithFullViewTrait[HIS, MS, VL, MP]  extends ApiRouteWithFullView[HIS, MS, VL, MP] with ApiRoute

abstract class SidechainApiRouteWithFullView[NH <: NodeHistory, NS <: NodeState, NW <: NodeWallet, NMP <: NodeMemoryPool]
          extends SidechainApiRouteWithFullViewTrait[NH, NS, NW, NMP] with SidechainApiDirectives {

/*  override final def route: Route = {
    val l = new util.ArrayList[Route]()
    val r = {}
    for (m <- getClass.getMethods){
      for (a <- m.getAnnotations){
        if (a.annotationType() == ApiCallRequest)
          l.add(m)
      }
    }

  }*/

  def registerApiRoute(f: ApiCallRequest => _) : Route = {
    entity(as[String]){ body =>
      withNodeView { view =>
        val obj_res = f
        ApiResponse(obj_res)
      }
    }
  }

  protected def gerCurrentView() = viewAsync().mapTo[CurrentView[NH,NS,NW,NMP]].value.get.get
}