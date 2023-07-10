#!/usr/bin/env python3
import logging
import os
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
Check multiple certificate submitters processing for ceasing sidechain:
    - Check that "faster" node which submits the certificate first, does not cause "slower" node to output error when trying to 
    submit its certificate for the same epoch.

Configuration:
    Start 1 MC node and 2 SC nodes.
    SC Node 1 is the only forger.
    SC node 1 has 3 schnorr private keys [0, 1, 2] for cert submission. Submitter and signer are ENABLED.
    SC node 2 has 3 other schnorr private keys [3, 4, 5] for cert submission. Submitter is ENABLED and signer is ENABLED.
    Sidechain is ceasing sidechain.

Test:
    - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
    - generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
    - check epoch 0 certificate with not backward transfers in the MC mempool
    - check that only one SC node forged certificate successfully, and the other one contains no errors
"""


class SCMultipleCertSubmitters(SidechainTestFramework):
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
            cert_submitter_enabled=True,
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

        assert_true(sc_node1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"], "sc node1 should be submitter")
        assert_true(sc_node2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"], "sc node2 should be submitter")

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
        while (mc_node.getmempoolinfo()["size"] < 1 and (sc_node1.submitter_isCertGenerationActive()["result"]["state"]
            or sc_node2.submitter_isCertGenerationActive()["result"]["state"])):
            logging.info("Wait for certificate in mc mempool...")
            print("node1 isGenerating: " + str(sc_node1.submitter_isCertGenerationActive()["result"]["state"]))
            print("node2 isGenerating: " + str(sc_node2.submitter_isCertGenerationActive()["result"]["state"]))
            time.sleep(2)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # wait till both SC nodes stop generating certificate
        while sc_node1.submitter_isCertGenerationActive()["result"]["state"] or sc_node2.submitter_isCertGenerationActive()["result"]["state"]:
            time.sleep(2)

        # Assert that proper log messages are present in both log files - only one node submitted certificate and the other one faced no errors
        with open(os.path.join(self.options.tmpdir + "/sc_node0/log", "debugLog.txt")) as node0, open(os.path.join(self.options.tmpdir + "/sc_node1/log", "debugLog.txt")) as node1:
            log0 = node0.read()
            log1 = node1.read()
            submission_not_needed_msg = "Submission not needed. Certificate of equal or higher quality already present in epoch 0"
            submission_successful = "Backward transfer certificate response had been received"
            assert_true((submission_not_needed_msg in log0 and submission_successful in log1) or (submission_not_needed_msg in log1 and submission_successful in log0))

if __name__ == "__main__":
    SCMultipleCertSubmitters().main()