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

class SidechainBlockTimestampInFutureException(message: String = "", cause: Option[Throwable] = None)
  extends BlockInFutureException(message, cause)

class MainchainHeaderTimestampInFutureException(message: String = "", cause: Option[Throwable] = None)
  extends BlockInFutureException(message, cause)


// SidechainBlock contains valid header, but Body is inconsistent.
// For example, transactions merkle root hash is different to the on specified in Header.
// Action: ban the sender only.
class InconsistentDataException(message: String = "", cause: Option[Throwable] = None)
  extends Exception(message, cause.orNull)

class SidechainBlockInconsistentDataException(message: String = "", cause: Option[Throwable] = None)
  extends InconsistentDataException(message, cause)

// Ommers data inconsistent to SidechainBlockHeader
class OmmerInconsistentDataException(message: String = "", cause: Option[Throwable] = None)
  extends InconsistentDataException(message, cause)


// SidechainBlockHeader is invalid itself.
// Action: ban both modifier and sender.
class SidechainBlockHeaderInvalidException(message: String = "", cause: Option[Throwable] = None)
  extends Exception(message, cause.orNull)


// SidechainBlock data is invalid.
// It means, that the full block is invalid.
// Action: ban both modifier and sender.
class InvalidDataException(message: String = "", cause: Option[Throwable] = None)
  extends Exception(message, cause.orNull)

// SidechainBlock data corresponds to Header but is invalid.
class SidechainBlockInvalidDataException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidDataException(message, cause)

// Ommer data is invalid.
class OmmerInvalidDataException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidDataException(message, cause)

// MainchainHeader is invalid
class MainchainHeaderInvalidException(message: String = "", cause: Option[Throwable] = None)
  extends InvalidDataException(message, cause)
