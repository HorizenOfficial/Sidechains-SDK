package io.horizen.fork

import scala.collection.mutable.ListBuffer
import scala.util.{Failure, Success, Try}

class ForkManager {}

object ForkManager {
    var networkName: String = null

    var mainchainForks: ListBuffer[BaseMainchainHeightFork] = ListBuffer[BaseMainchainHeightFork]()
    var consensusEpochForks: ListBuffer[BaseConsensusEpochFork] = ListBuffer[BaseConsensusEpochFork]()

    def getMainchainFork(mainchainHeight:Int):BaseMainchainHeightFork = {
        if (networkName == null) {
            throw new RuntimeException("Forkmanager hasn't been initialized.")
        }

        if (mainchainForks.isEmpty)
            throw new RuntimeException("MainchainForks list is empty")

        val mcForksIter = mainchainForks.iterator
        var mcFork: BaseMainchainHeightFork = null

        networkName match {
            case "regtest" => mcForksIter.takeWhile(fork => fork.heights.regtestHeight <= mainchainHeight).foreach(fork => {mcFork = fork})
            case "testnet" => mcForksIter.takeWhile(fork => fork.heights.testnetHeight <= mainchainHeight).foreach(fork => {mcFork = fork})
            case "mainnet" => mcForksIter.takeWhile(fork => fork.heights.mainnetHeight <= mainchainHeight).foreach(fork => {mcFork = fork})
        }

        mcFork
    }

    def getSidechainConsensusEpochFork(consensusEpoch:Int):BaseConsensusEpochFork = {
        if (networkName == null) {
            throw new RuntimeException("Forkmanager hasn't been initialized.")
        }

        if (consensusEpochForks.isEmpty)
            throw new RuntimeException("ConsensusEpochForks list is empty")

        val consensusForksIter = consensusEpochForks.iterator
        var consensusFork: BaseConsensusEpochFork = null

        networkName match {
            case "regtest" => consensusForksIter.takeWhile(fork => fork.epochNumber.regtestEpochNumber <= consensusEpoch).foreach(fork => {consensusFork = fork})
            case "testnet" => consensusForksIter.takeWhile(fork => fork.epochNumber.testnetEpochNumber <= consensusEpoch).foreach(fork => {consensusFork = fork})
            case "mainnet" => consensusForksIter.takeWhile(fork => fork.epochNumber.mainnetEpochNumber <= consensusEpoch).foreach(fork => {consensusFork = fork})
        }

        consensusFork
    }

    def init(forkConfigurator:ForkConfigurator, networkName:String): Try[Unit] = Try {
        if (this.networkName != null) {
            throw new IllegalStateException("ForkManager is already initialized.")
        }

        networkName match {
            case "regtest" | "testnet" | "mainnet" => this.networkName = networkName
            case _ => throw new IllegalArgumentException("Unknown network type.")
        }

        mainchainForks += new BaseMainchainHeightFork(BaseMainchainHeightFork.DEFAULT_MAINCHAIN_FORK_HEIGHTS)

        forkConfigurator.check() match {
            case Success(_) => {
                consensusEpochForks += new BaseConsensusEpochFork(forkConfigurator.getBaseSidechainConsensusEpochNumbers())
                consensusEpochForks += new SidechainFork1(forkConfigurator.getSidechainFork1())
            }
            case Failure(exception) => {
                this.networkName = null
                throw exception
            }
        }
    }

    private[horizen] def reset(): Unit = {
        this.networkName = null
        this.mainchainForks = ListBuffer[BaseMainchainHeightFork]()
        this.consensusEpochForks = ListBuffer[BaseConsensusEpochFork]()
    }
}