package io.horizen.history.validation

import io.horizen.block.{ProofOfWorkVerifier, SidechainBlockBase, SidechainBlockHeaderBase}
import io.horizen.chain.AbstractFeePaymentsInfo
import io.horizen.history.AbstractHistory
import io.horizen.params.NetworkParams
import io.horizen.storage.AbstractHistoryStorage
import io.horizen.transaction.Transaction
import io.horizen.utils.BytesUtils
import sparkz.util.idToBytes

import scala.util.{Failure, Success, Try}

class MainchainPoWValidator[
  TX <: Transaction,
  H <: SidechainBlockHeaderBase,
  PMOD <: SidechainBlockBase[TX, H],
  FPI <: AbstractFeePaymentsInfo,
  HSTOR <: AbstractHistoryStorage[PMOD, FPI, HSTOR],
  HT <: AbstractHistory[TX, H, PMOD, FPI, HSTOR, HT]
]
(
  params: NetworkParams
)
  extends HistoryBlockValidator[TX, H, PMOD, FPI, HSTOR, HT] {

  override def validate(block: PMOD, history: HT): Try[Unit] = {
    if(ProofOfWorkVerifier.checkNextWorkRequired[H, PMOD, FPI, HSTOR](block, history.storage, params)) {
      Success(Unit)
    }
    else {
      Failure(new IllegalArgumentException("Containing MC Blocks PoW difficulty is invalid for block %s".format(BytesUtils.toHexString(idToBytes(block.id)))))
    }
  }
}
