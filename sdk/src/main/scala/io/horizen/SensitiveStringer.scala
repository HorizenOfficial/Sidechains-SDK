package io.horizen

import java.lang.reflect.Field

trait SensitiveStringer {
  self: Product =>

  private def filter(field: Field, value: Any): String = {
    if (value == null) return "null"
    if (field.isAnnotationPresent(classOf[SensitiveString])) {
      if (value.toString.isEmpty) "" else "****"
    } else {
      value.toString
    }
  }

  override def toString: String = {
    this.getClass.getDeclaredFields
      .zip(productIterator.toSeq)
//      .filter { case (a, _) => a.getAnnotation(classOf[SensitiveString]) == null }
      .map { case (a, b) => s"${a.getName}=${filter(a, b)}" }
      .mkString(s"$productPrefix(", ",", ")")
  }
}
