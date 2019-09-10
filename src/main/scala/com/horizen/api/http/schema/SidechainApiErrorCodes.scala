package com.horizen.api.http.schema

trait ApiGroupErrorCode {

  def groupCode : String
  def groupName : String

}

trait BlockApiGroupErrorCodes extends ApiGroupErrorCode {

  override final def groupCode: String = "01"
  override final def groupName: String = "block"
}

trait TransactionApiGroupErrorCodes extends ApiGroupErrorCode {

  override final def groupCode: String = "02"
  override final def groupName: String = "transaction"

}

trait WalletApiGroupErrorCodes extends ApiGroupErrorCode {

  override final def groupCode: String = "03"
  override final def groupName: String = "wallet"

}

trait NodeApiGroupErrorCodes extends ApiGroupErrorCode {

  override final def groupCode: String = "04"
  override final def groupName: String = "node"

}

trait ApiErrorCode extends ApiGroupErrorCode {

  def internalCode : String
  final def apiCode : String = groupCode + internalCode
  def internalName : String

}

case class INVALID_ID() extends ApiErrorCode with BlockApiGroupErrorCodes {
  override final def internalCode: String = "01"
  override final def internalName: String = "INVALID_ID"
}

case class INVALID_HEIGHT() extends ApiErrorCode with BlockApiGroupErrorCodes {
  override final def internalCode: String = "02"
  override final def internalName: String = "INVALID_HEIGHT"
}

case class TEMPLATE_FAILURE() extends ApiErrorCode with BlockApiGroupErrorCodes {
  override final def internalCode: String = "03"
  override final def internalName: String = "TEMPLATE_FAILURE"
}

case class NOT_ACCEPTED() extends ApiErrorCode with BlockApiGroupErrorCodes {
  override final def internalCode: String = "04"
  override final def internalName: String = "NOT_ACCEPTED"
}

case class NOT_CREATED() extends ApiErrorCode with BlockApiGroupErrorCodes {
  override final def internalCode: String = "05"
  override final def internalName: String = "NOT_CREATED"
}

case class NOT_FOUND_ID() extends ApiErrorCode with TransactionApiGroupErrorCodes {
  override final def internalCode: String = "01"
  override final def internalName: String = "NOT_FOUND_ID"
}

case class NOT_FOUND_INPUT() extends ApiErrorCode with TransactionApiGroupErrorCodes {
  override final def internalCode: String = "02"
  override final def internalName: String = "NOT_FOUND_INPUT"
}

case class BYTE_PARSING_FAILURE() extends ApiErrorCode with TransactionApiGroupErrorCodes {
  override final def internalCode: String = "03"
  override final def internalName: String = "BYTE_PARSING_FAILURE"
}

case class GENERIC_FAILURE() extends ApiErrorCode with TransactionApiGroupErrorCodes {
  override final def internalCode: String = "04"
  override final def internalName: String = "GENERIC_FAILURE"
}

case class SECRET_NOT_ADDED()  extends ApiErrorCode with WalletApiGroupErrorCodes {
  override final def internalCode: String = "01"
  override final def internalName: String = "SECRET_NOT_ADDED"
}

case class INVALID_HOST_PORT()  extends ApiErrorCode with NodeApiGroupErrorCodes {
  override final def internalCode: String = "01"
  override final def internalName: String = "INVALID_HOST_PORT"
}

object SidechainApiErrorCodeUtil {

  val predefinedApiErrorCodes : Seq[ApiErrorCode] = Seq(INVALID_ID(), INVALID_HEIGHT(), TEMPLATE_FAILURE(), NOT_ACCEPTED(), NOT_CREATED(),
    NOT_FOUND_ID(), NOT_FOUND_INPUT(), BYTE_PARSING_FAILURE(), GENERIC_FAILURE(), SECRET_NOT_ADDED(), INVALID_HOST_PORT())
}

