package com.horizen.validation

import com.horizen.AbstractHistory
import com.horizen.block.{ProofOfWorkVerifier, SidechainBlockBase}
import com.horizen.params.NetworkParams
import com.horizen.storage.AbstractHistoryStorage
import com.horizen.transaction.Transaction
import com.horizen.utils.BytesUtils
import scorex.util.idToBytes

import scala.util.{Failure, Success, Try}

class MainchainPoWValidator[
  TX <: Transaction,
  PMOD <: SidechainBlockBase[TX],
  HSTOR <: AbstractHistoryStorage[PMOD, HSTOR],
  HT <: AbstractHistory[TX, PMOD, HSTOR, HT]
]
(
  params: NetworkParams
)
  extends HistoryBlockValidator[TX, PMOD, HSTOR, HT] {

  override def validate(block: PMOD, history: HT): Try[Unit] = {
    if(ProofOfWorkVerifier.checkNextWorkRequired[TX, PMOD, HSTOR](block, history.storage, params)) {
      Success(Unit)
    }
    else {
      Failure(new IllegalArgumentException("Containing MC Blocks PoW difficulty is invalid for block %s".format(BytesUtils.toHexString(idToBytes(block.id)))))
    }
  }
}
