package com.horizen.account.api.rpc.service.utils

import akka.util.Timeout
import com.horizen.account.api.rpc.handler.RpcException
import com.horizen.account.api.rpc.utils.{RpcCode, RpcError}
import com.horizen.account.block.AccountBlock
import com.horizen.account.forger.AccountForgeMessageBuilder
import com.horizen.account.history.AccountHistory
import com.horizen.account.mempool.AccountMemoryPool
import com.horizen.account.state._
import com.horizen.account.wallet.AccountWallet
import com.horizen.chain.{MainchainHeaderHash, SidechainBlockInfo}
import com.horizen.consensus.ForgingStakeInfo
import com.horizen.forge.MainchainSynchronizer
import com.horizen.params.NetworkParams
import com.horizen.proof.VrfProof
import com.horizen.proposition.{PublicKey25519Proposition, VrfPublicKey}
import com.horizen.secret.PrivateKey25519
import com.horizen.utils.{ClosableResourceHandler, ForgingStakeMerklePathInfo, MerklePath, WithdrawalEpochUtils}
import scorex.util.{ModifierId, ScorexLogging}
import sparkz.core.NodeViewHolder.CurrentView
import sparkz.core.consensus.ModifierSemanticValidity

import java.util.{ArrayList => JArrayList}
import scala.concurrent.duration.SECONDS
import scala.util.{Failure, Success}

case class BranchPointInfo(
    branchPointId: ModifierId,
    referenceDataToInclude: Seq[MainchainHeaderHash],
    headersToInclude: Seq[MainchainHeaderHash]
)

class PendingBlock(
    nodeView: CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]
) extends ScorexLogging with ClosableResourceHandler {

  type NV = CurrentView[AccountHistory, AccountState, AccountWallet, AccountMemoryPool]
  private val history: AccountHistory = nodeView.history
  private val networkParams: NetworkParams = nodeView.state.params

  def getPendingBlock: Option[AccountBlock] = {
    val mainchainSynchronizer = new MainchainSynchronizer(null)
    val accountForgeMessageBuilder =
      new AccountForgeMessageBuilder(mainchainSynchronizer, null, networkParams, false)

    val branchPointInfo = accountForgeMessageBuilder.getBranchPointInfo(history, false) match {
      case Success(info) => info
      case Failure(_) =>
        throw new RpcException(RpcError.fromCode(RpcCode.InternalError, "Could not get branch point info"))
    }

    val forgingStakeInfo: ForgingStakeInfo = new ForgingStakeInfo(
      new PublicKey25519Proposition(new Array[Byte](PublicKey25519Proposition.KEY_LENGTH)),
      new VrfPublicKey(new Array[Byte](VrfPublicKey.KEY_LENGTH)),
      0
    )
    val forgingStakeMerklePathInfo: ForgingStakeMerklePathInfo =
      ForgingStakeMerklePathInfo(forgingStakeInfo, new MerklePath(new JArrayList()))
    val vrfProof = new VrfProof(new Array[Byte](VrfProof.PROOF_LENGTH))

    implicit val timeout: Timeout = new Timeout(5, SECONDS)

    val transactions = nodeView.pool.getExecutableTransactionsMap.values.flatMap(_.values)

    val block = accountForgeMessageBuilder.getPendingBlock(
      nodeView,
      System.currentTimeMillis / 1000,
      branchPointInfo,
      forgingStakeMerklePathInfo,
      new PrivateKey25519(
        new Array[Byte](PrivateKey25519.PRIVATE_KEY_LENGTH),
        new Array[Byte](PrivateKey25519.PUBLIC_KEY_LENGTH)
      ),
      vrfProof,
      timeout,
      transactions
    )
    block
  }

  def getBlockInfo(block: AccountBlock, parentId: ModifierId, parentInfo: SidechainBlockInfo): SidechainBlockInfo = {
    new SidechainBlockInfo(
      parentInfo.height + 1,
      parentInfo.score + 1,
      parentId,
      System.currentTimeMillis / 1000,
      ModifierSemanticValidity.Unknown,
      null,
      null,
      WithdrawalEpochUtils.getWithdrawalEpochInfo(
        block,
        parentInfo.withdrawalEpochInfo,
        networkParams
      ),
      None,
      parentInfo.lastBlockInPreviousConsensusEpoch
    )
  }
}
