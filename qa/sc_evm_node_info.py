#!/usr/bin/env python3
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import connect_sc_nodes
from test_framework.util import assert_true

"""
Make sure that node info endpoint gives correct data for Account model.

Configuration: start 2 sidechain nodes.

Test:
    - connect the first node to the other one
    - get the first node info
    - check that number of all peers is 1 for each node
    - check that rest of the parameters from node_info endpoint match the expected

"""


class ScEvmNodeInfo(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def run_test(self):
        sc_nodes = self.sc_nodes

        connect_sc_nodes(sc_nodes[0], 1)

        node_info_0 = sc_nodes[0].node_info()['result']
        node_info_1 = sc_nodes[1].node_info()['result']

        assert_true(node_info_0['numberOfPeers'], 1)
        assert_true(node_info_1['numberOfPeers'], 1)

        assert_true(node_info_1['nodeName'], 'node1')
        assert_true(node_info_1['nodeType'], 'signer')

        assert_true(node_info_0['nodeName'], 'node0')
        assert_true(node_info_0['nodeType'], 'signer,submitter')
        assert_true(node_info_0['protocolVersion'], '0.0.1')
        assert_true(node_info_0['agentName'], '2-Hop')
        assert_true(node_info_0['sdkVersion'])
        assert_true(node_info_0['scId'])
        assert_true(node_info_0['scType'], 'ceasing')
        assert_true(node_info_0['scModel'], 'Account')
        assert_true(node_info_0['scBlockHeight'], 1)
        assert_true(node_info_0['scConsensusEpoch'], 1)
        assert_true(node_info_0['epochForgersStake'], 10000000000)
        assert_true(node_info_0['nextBaseFee'], 875000000)
        assert_true(node_info_0['scWithdrawalEpochLength'], 900)
        # assert_true(node_info_0['scWithdrawalEpochNum'], 0)
        assert_true(node_info_0['scEnv'], 'regtest')
        assert_true(node_info_0['lastMcBlockReferenceHash'])
        assert_true(node_info_0['numberOfConnectedPeers'], 1)
        # assert_true(node_info_0['numberOfBlacklistedPeers'], 0)
        assert_true(node_info_0['maxMemPoolSlots'], 6144)
        # assert_true(node_info_0['numOfTxInMempool'], 0)
        # assert_true(node_info_0['executableTxSize'], 0)
        # assert_true(node_info_0['nonExecutableTxSize'], 0)
        # assert_true(node_info_0['errors'], [])
        assert_true(list(node_info_0.keys()).__len__(), 24)


if __name__ == "__main__":
    ScEvmNodeInfo().main()
