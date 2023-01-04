#!/usr/bin/env python3
import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block, connect_sc_nodes, sync_sc_blocks
from SidechainTestFramework.sc_forging_util import *

"""
Check Certificate submission behaviour for the node after sync from scratch with an existing chain.
Note: SC network cross withdrawal epochs many times (10) on the existing chain.

Configuration:
    Start 1 MC node and 2 SC nodes (with default websocket configuration) disconnected.
    SC nodes are certificate submitters.
    Every SC Node has enough keys to produce certificate signatures.

Note:
    This test can be executed in two modes:
    1. using no key rotation circuit (by default)
    2. using key rotation circuit (with --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation)
    With key rotation circuit can be executed in two modes:
    1. ceasing (by default)
    2. non-ceasing (with --nonceasing flag)
    
Test:
    - Generate many MC blocks and SC blocks to pass 10 withdrawal epochs. SC node 1 generates certificates for every epoch.
    - Connect SC node 2 to SC node 1 and start syncing
    - Ensure that node 2 was able to sync
    - Disable cert submitter logic on SC node 1.
    - Generate a few more MC and SC blocks to reach the end of the withdrawal epoch.
    - Check that certificate was generated. So Submitter and Signer are alive on SC node 2.
    - Sleep for 20 seconds and visually check that there is no continues errors/warnings after sync.
"""


class ScCertSubmitterAfterSync2(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 100
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
            True,  # certificate submitter is enabled
            True,  # certificate signer is enabled
            list([0, 1, 2, 3, 4])  # with 5 schnorr PKs, so has enough signatures to start cert generation.
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True,  # certificate submitter is enabled
            True,  # certificate signer is enabled
            list([4, 5, 6])  # with 3 schnorr PKs
        )

        if self.options.certcircuittype == KEY_ROTATION_CIRCUIT:
            sc_creation_version = SC_CREATION_VERSION_2  # non-ceasing could be only SC_CREATION_VERSION_2>=2
        else:
            sc_creation_version = SC_CREATION_VERSION_1

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, self.sc_creation_amount, self.sc_withdrawal_epoch_length,
                                                        sc_creation_version=sc_creation_version,
                                                        is_non_ceasing=self.options.nonceasing,
                                                        circuit_type=self.options.certcircuittype),
                                         sc_node_1_configuration,
                                         sc_node_2_configuration)

        # rewind sc genesis block timestamp for 10 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def do_cert_cycle(self, mc_node, sc_forger_node, sc_submitter_node, mc_blocks_in_epoch_left, additional_sc_blocks):
        # Generate MC blocks to reach one block before the end of the withdrawal epoch (WE)
        # 1 SC block contains MC block
        for i in range(mc_blocks_in_epoch_left - 1):
            mc_node.generate(1)
            generate_next_block(sc_forger_node, "node")

        # Generate additional sc blocks
        for i in range(additional_sc_blocks):
            generate_next_block(sc_forger_node, "node")

        # Generate MC blocks to switch WE epoch
        mc_node.generate(2)

        # Generate 2 SC blocks on SC node and start them automatic cert creation.
        generate_next_block(sc_forger_node, "second node")  # 1 MC block to reach the end of WE
        generate_next_block(sc_forger_node, "second node")  # 1 MC block to trigger Submitter logic

        # Wait for Certificates appearance
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] < 1 and sc_submitter_node.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificates in the MC mempool...")
            if sc_submitter_node.submitter_isCertGenerationActive()["result"]["state"]:
                logging.info("sc_node generating certificate now.")
            time.sleep(2)

        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificates was not added to MC node mempool.")

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        for i in range(10):
            self.do_cert_cycle(mc_node, sc_node1, sc_node1, self.sc_withdrawal_epoch_length - 1, 300)

        logging.info("Connecting nodes...")
        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        logging.info("Starting synchronization...")
        time.sleep(20)
        # The following lines checks Certificate Submitter Api during the node synchronization
        assert_equal(True, sc_node2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"])
        assert_equal(True, sc_node2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"])
        sync_sc_blocks(self.sc_nodes, 500, True)
        logging.info("Synchronization finished.")

        # Disable sc node 1 submitter
        sc_node1.submitter_disableCertificateSubmitter()

        # Check if Node 2 can submit certificate
        self.do_cert_cycle(mc_node, sc_node1, sc_node2, self.sc_withdrawal_epoch_length - 1, 5)

        logging.info("Node after synchronization was able to sign, collect signatures and emit certificate.")

        logging.info("Sleep for 20 seconds to check for potential errors after")
        time.sleep(20)


if __name__ == "__main__":
    ScCertSubmitterAfterSync2().main()
