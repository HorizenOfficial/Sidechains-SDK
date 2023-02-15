package com.horizen.account.validation

import com.horizen.validation.InvalidBlockException


class MissingTransactionSignatureException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

class InvalidTransactionChainIdException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

class InvalidBaseFeeException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)
