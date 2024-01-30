#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    generate_next_block, connect_sc_nodes, sync_sc_blocks, EVM_APP_BINARY, SIMPLE_APP_BINARY
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index

"""
Check Certificate submission behaviour for the node after sync from scratch with an existing chain.
Note: All the sc blocks belongs to the same withdrawal epoch.

Configuration:
    Start 1 MC node and 2 SC nodes (with default websocket configuration) disconnected.
    SC nodes 2 is a certificate submitter.
    SC Nodes altogether have enough keys to produce certificate signatures.

Note:
    This test can be executed in two modes:
    1. using no key rotation circuit (by default)
    2. using key rotation circuit (with --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation)
    With key rotation circuit can be executed in two modes:
    1. ceasing (by default)
    2. non-ceasing (with --nonceasing flag)
    
Test:
    - Generate 2000 blocks on SC node 1
    - Connect SC node 2 to SC node 1 and start syncing
    - Ensure that node 2 was able to sync
    - Generate a few more MC and SC blocks to reach the end of the withdrawal epoch.
    - Check that certificate was generated. So Submitter and Signer are alive on SC node 2.
"""

NUM_BLOCKS = 2000


class ScCertSubmitterAfterSync1(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    sc_creation_amount = 100  # Zen

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-logtimemicros=1',
                                        '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False, True, list([0, 1, 2])  # certificate submitter is disabled, signing is enabled with 3 schnorr PKs
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True, True, list([3, 4, 5])  # certificate submitter is enabled, signing is enabled with 3 other schnorr PKs
        )

        if self.options.certcircuittype == KEY_ROTATION_CIRCUIT:
            sc_creation_version = SC_CREATION_VERSION_2  # non-ceasing could be only SC_CREATION_VERSION_2>=2
        else:
            sc_creation_version = SC_CREATION_VERSION_1

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 1000, self.sc_withdrawal_epoch_length,
                                                        sc_creation_version=sc_creation_version,
                                                        is_non_ceasing=self.options.nonceasing,
                                                        circuit_type=self.options.certcircuittype),
                                         sc_node_1_configuration,
                                         sc_node_2_configuration)

        # rewind sc genesis block timestamp for 10 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return self.sc_setup_nodes_with_extra_arg('-max_hist_rew_len', str(NUM_BLOCKS + 1), SIMPLE_APP_BINARY)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Generate 2000 SC blocks on SC node 1
        for i in range(NUM_BLOCKS):
            generate_next_block(sc_node1, "first node")

        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        logging.info("Starting synchronization...")
        sync_sc_blocks(self.sc_nodes, 300, True)
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
