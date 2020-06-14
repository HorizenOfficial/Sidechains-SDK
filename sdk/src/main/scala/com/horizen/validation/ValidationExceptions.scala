package com.horizen.validation

/*
  The list of Exceptions, that could be thrown by any SemanticBlockValidator
  or HistoryBlockValidator during applying to the Node.

  These exceptions require specific processing logic in NodeViewSynchronizer:
  1) banning modifier ID and sender;
  2) banning the sender only;
  3) no ban at all.
*/


// SidechainBlock contains valid Header and valid consistent body,
// but block itself or MC Headers inside are too far in the future
// Action: no ban at all.
class BlockInFutureException(message: String = "", cause: Option[Throwable] = None)
  extends Exception(message, cause.orNull)

class SidechainBlockSlotInFutureException(message: String = "", cause: Option[Throwable] = None)
  extends BlockInFutureException(message, cause)

class MainchainHeaderTimestampInFutureException(message: String = "", cause: Option[Throwable] = None)
  extends BlockInFutureException(message, cause)


// SidechainBlock contains valid header, but Body is inconsistent.
// For example, transactions merkle root hash is different to the on specified in Header.
// Action: ban the sender only.
class InconsistentDataException(message: String = "", cause: Option[Throwable] = None)
  extends Exception(message, cause.orNull)

// SidechainBlock data is inconsistent to its SidechainBlockHeader
class InconsistentSidechainBlockDataException(message: String = "", cause: Option[Throwable] = None)
  extends InconsistentDataException(message, cause)

// Ommers data inconsistent to Ommer's SidechainBlockHeader
class InconsistentOmmerDataException(message: String = "", cause: Option[Throwable] = None)
  extends InconsistentDataException(message, cause)

// MainchainBlockReferenceData data inconsistent to MainchainBlockReference's MainchainHeader
class InconsistentMainchainBlockReferenceDataException(message: String = "", cause: Option[Throwable] = None)
  extends InconsistentDataException(message, cause)


// SidechainBlock is invalid.
// Action: ban both modifier and sender.
class InvalidBlockException(message: String = "", cause: Option[Throwable] = None)
  extends Exception(message, cause.orNull)

// SidechainBlockHeader is invalid itself.
class InvalidSidechainBlockHeaderException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

// SidechainBlock data corresponds to Header but is invalid.
class InvalidSidechainBlockDataException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

// Ommer data is invalid.
class InvalidOmmerDataException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

// MainchainHeader is invalid
class InvalidMainchainHeaderException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)

class InvalidMainchainDataException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidBlockException(message, cause)
