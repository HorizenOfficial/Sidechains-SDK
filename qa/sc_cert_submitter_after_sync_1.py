#!/usr/bin/env python3
import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block, connect_sc_nodes, sync_sc_blocks
from SidechainTestFramework.sc_forging_util import *

"""
Check Certificate submission behaviour for the node after sync from scratch with an existing chain.
Note: All the sc blocks belongs to the same withdrawal epoch.

Configuration:
    Start 1 MC node and 2 SC nodes (with default websocket configuration) disconnected.
    SC nodes 2 is a certificate submitter.
    SC Nodes altogether have enough keys to produce certificate signatures.

Test:
    - Generate 2000 blocks on SC node 1
    - Connect SC node 2 to SC node 1 and start syncing
    - Ensure that node 2 was able to sync
    - Generate a few more MC and SC blocks to reach the end of the withdrawal epoch.
    - Check that certificate was generated. So Submitter and Signer are alive on SC node 2.
"""
class ScCertSubmitterAfterSync1(SidechainTestFramework):

    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    sc_creation_amount = 100  # Zen

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-logtimemicros=1', '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False, True, list([0, 1, 2]),  # certificate submitter is disabled, signing is enabled with 3 schnorr PKs
            type_of_circuit_number=int(self.options.certcircuittype)  # in run_sc_tests.sh resolved by ${passOn} var
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True, True, list([3, 4, 5]),  # certificate submitter is enabled, signing enabled with 3 other schnorr PKs
            type_of_circuit_number=int(self.options.certcircuittype)  # in run_sc_tests.sh resolved by ${passOn} var
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, self.sc_creation_amount, self.sc_withdrawal_epoch_length, csw_enabled=True),
            sc_node_1_configuration,
            sc_node_2_configuration)

        # rewind sc genesis block timestamp for 10 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*10)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Generate 2000 SC blocks on SC node 1
        for i in range(2000):
            generate_next_block(sc_node1, "first node")

        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        logging.info("Starting synchronization...")
        sync_sc_blocks(self.sc_nodes, 100, True)
        logging.info("Synchronization finished.")

        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length - 1  # minus genesis block

        # Generate MC blocks to reach one block before the end of the withdrawal epoch (WE)
        mc_block_hashes = mc_node.generate(mc_blocks_left_for_we - 1)
        mc_blocks_left_for_we -= len(mc_block_hashes)

        # Generate 1 more SC block to sync with MC
        generate_next_block(sc_node1, "first node")
        self.sc_sync_all()  # Sync SC nodes

        # Generate MC blocks to switch WE epoch
        logging.info("mc blocks left = " + str(mc_blocks_left_for_we))
        mc_node.generate(mc_blocks_left_for_we + 1)

        # Generate 2 SC blocks on SC node and start them automatic cert creation.
        generate_next_block(sc_node1, "second node")  # 1 MC block to reach the end of WE
        generate_next_block(sc_node1, "second node")  # 1 MC block to trigger Submitter logic

        self.sc_sync_all()  # Sync SC nodes

        # Wait for Certificates appearance
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] < 1 and sc_node2.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificates in the MC mempool...")
            if sc_node2.submitter_isCertGenerationActive()["result"]["state"]:
                logging.info("sc_node2 generating certificate now.")
            time.sleep(2)

        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificates was not added to MC node mempool.")

        logging.info("Node after synchronization was able to sign, collect signatures and emit certificate.")


if __name__ == "__main__":
    ScCertSubmitterAfterSync1().main()
