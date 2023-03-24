package io.horizen.utils

trait ClosableResourceHandler {
  //TODO This implementation is not perfect: in case of an exception calling close(), the latter exception will be
  //propagated and it will hide the original one. A better solution will be using scala.util.Using available
  //starting from 2.13 version.
  def using[A <: AutoCloseable, B](resource: A)(fun: A => B): B = {
    try {
      fun(resource)
    } finally {
      if (resource != null)
        resource.close()
    }
  }
}
