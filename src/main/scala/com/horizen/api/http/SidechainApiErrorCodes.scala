package com.horizen.api.http

trait ApiGroupErrorCodes {

  def code : String

}

object BlockApiGroupErrorCodes extends ApiGroupErrorCodes {

  override def code: String = "01"

  final val INVALID_ID = code + "01"

  final val INVALID_HEIGHT = code + "02"

  final val TEMPLATE_FAILURE = code + "03"

  final val NOT_ACCEPTED = code + "04"

  final val NOT_CREATED = code + "05"

}

object TransactionApiGroupErrorCodes extends ApiGroupErrorCodes {

  override def code: String = "02"

  final val NOT_FOUND_ID = code + "01"

  final val NOT_FOUND_INPUT = code + "02"

  final val BYTE_PARSING_FAILURE = code + "03"

  final val GENERIC_FAILURE = code + "04"

}

object WalletApiGroupErrorCodes extends ApiGroupErrorCodes {

  override def code: String = "03"

  final val KEY_PAIR_CREATION_FAILED = code + "01"

  final val SECRET_NOT_ADDED = code + "01"

}
