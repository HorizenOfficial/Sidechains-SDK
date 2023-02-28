package io.horizen.account.history.validation

import com.horizen.history.validation.InvalidBlockException


class MissingTransactionSignatureException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

class InvalidTransactionChainIdException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

class InvalidBaseFeeException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)
