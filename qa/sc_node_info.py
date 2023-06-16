#!/usr/bin/env python3
import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCCreationInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_forging_util import check_mcreference_presence, check_mcreferencedata_presence
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import (
    generate_next_block, generate_next_blocks, bootstrap_sidechain_nodes, connect_sc_nodes, assert_true,
    start_sc_nodes
)
from test_framework.util import (
    assert_equal, websocket_port_by_mc_node_index, start_nodes,
)

"""
Check that node info endpoint gives correct data for UTXO sidechain model.

Configuration:
    Start 1 MC node and 2 SC nodes.
    SC Node 1 is the only forger.
    Sidechain is ceasing sidechain.

Test:
    - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
    - generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
    - generate MC for certificate to appear in MC
    - generate SC block to sync with MC
    - check node info results of both nodes
"""


class SCNodeInfo(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=True,
            cert_signing_enabled=True
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=False,
            cert_signing_enabled=True
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 100, self.sc_withdrawal_epoch_length),
                                         sc_node_1_configuration, sc_node_2_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)

        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        mcblock_hash1 = mc_node.generate(1)[0]
        scblock_id1 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_mcreferencedata_presence(mcblock_hash1, scblock_id1, sc_node1)

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]

        scblock_id2 = generate_next_block(sc_node1, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node1)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] < 1 and sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # generate one MC block for certificate to appear in MC
        # generate one SC block to sync with MC
        we3_end_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_block(sc_node1, "first node")
        check_mcreferencedata_presence(we3_end_mcblock_hash, scblock_id3, sc_node1)

        node_info_1 = sc_node1.node_info()['result']
        node_info_2 = sc_node2.node_info()['result']

        # sc node 1
        assert_true(node_info_1['nodeName'], 'node0')
        assert_true(node_info_1['nodeType'], 'signer,submitter')
        assert_true(node_info_1['protocolVersion'], '0.0.1')
        assert_true(node_info_1['agentName'], '2-Hop')
        assert_true(node_info_1['sdkVersion'], "sdkVersion should be present")
        assert_true('nodeVersion' not in node_info_1, "nodeVersion should not be present")
        assert_true(node_info_1['scId'], self.sc_nodes_bootstrap_info.sidechain_id)
        assert_true(node_info_1['scType'], "non ceasing" if self.sc_nodes_bootstrap_info.is_non_ceasing else "ceasing")
        assert_true(node_info_1['scModel'], 'UTXO')
        assert_true(node_info_1['scBlockHeight'], 5)
        assert_true(node_info_1['scConsensusEpoch'], 2)
        assert_true(node_info_1['epochForgersStake'], 10000000000)
        assert_true(node_info_1['scWithdrawalEpochLength'], self.sc_nodes_bootstrap_info.withdrawal_epoch_length)
        assert_equal(node_info_1['scWithdrawalEpochNum'], 1)
        assert_true(node_info_1['scEnv'], self.sc_nodes_bootstrap_info.network)
        assert_true(node_info_1['lastMcBlockReferenceHash'], we3_end_mcblock_hash)
        assert_true(node_info_1['numberOfPeers'], 1)
        assert_true(node_info_1['numberOfConnectedPeers'], 1)
        assert_equal(node_info_1['numberOfBlacklistedPeers'], 0)
        assert_equal(node_info_1['numOfTxInMempool'], 0)
        assert_equal(node_info_1['mempoolUsedSizeKBytes'], 0)
        assert_equal(node_info_1['mempoolUsedPercentage'], 0)
        assert_equal(node_info_1['lastCertEpoch'], 0)
        assert_equal(node_info_1['lastCertQuality'], self.sc_nodes_bootstrap_info.certificate_proof_info.public_signing_keys.__len__())
        assert_equal(node_info_1['lastCertBtrFee'], 0)
        assert_equal(node_info_1['lastCertFtMinAmount'], 0)
        assert_true(node_info_1['lastCertHash'], "lastCertHash should be present")
        assert_equal(node_info_1['errors'], [])
        assert_true(list(node_info_1.keys()).__len__(), 27)

        # sc node 2
        assert_true(node_info_2['nodeName'], 'node1')
        assert_true(node_info_2['nodeType'], 'signer')
        assert_true(node_info_2['protocolVersion'], '0.0.1')
        assert_true(node_info_2['agentName'], '2-Hop')
        assert_true(node_info_2['sdkVersion'], "sdkVersion should be present")
        assert_true('nodeVersion' not in node_info_2, "nodeVersion should not be present")
        assert_true(node_info_2['scId'], self.sc_nodes_bootstrap_info.sidechain_id)
        assert_true(node_info_2['scType'], "non ceasing" if self.sc_nodes_bootstrap_info.is_non_ceasing else "ceasing")
        assert_true(node_info_2['scModel'], 'UTXO')
        assert_true(node_info_2['scBlockHeight'], 5)
        assert_true(node_info_2['scConsensusEpoch'], 2)
        assert_true(node_info_2['epochForgersStake'], 10000000000)
        assert_true(node_info_2['scWithdrawalEpochLength'], self.sc_nodes_bootstrap_info.withdrawal_epoch_length)
        assert_equal(node_info_2['scWithdrawalEpochNum'], 1)
        assert_true(node_info_2['scEnv'], self.sc_nodes_bootstrap_info.network)
        assert_true(node_info_2['lastMcBlockReferenceHash'], we3_end_mcblock_hash)
        assert_true(node_info_2['numberOfPeers'], 1)
        assert_true(node_info_2['numberOfConnectedPeers'], 1)
        assert_equal(node_info_2['numberOfBlacklistedPeers'], 0)
        assert_equal(node_info_2['numOfTxInMempool'], 0)
        assert_equal(node_info_2['mempoolUsedSizeKBytes'], 0)
        assert_equal(node_info_2['mempoolUsedPercentage'], 0)
        assert_equal(node_info_2['lastCertEpoch'], 0)
        assert_equal(node_info_2['lastCertQuality'], self.sc_nodes_bootstrap_info.certificate_proof_info.public_signing_keys.__len__())
        assert_equal(node_info_2['lastCertBtrFee'], 0)
        assert_equal(node_info_2['lastCertFtMinAmount'], 0)
        assert_true(node_info_2['lastCertHash'], "lastCertHash should be present")
        assert_equal(node_info_2['errors'], [])
        assert_true(list(node_info_2.keys()).__len__(), 27)


if __name__ == "__main__":
    SCNodeInfo().main()
