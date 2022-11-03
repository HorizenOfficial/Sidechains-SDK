#!/usr/bin/env python3
import time
from decimal import *

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, check_wallet_coins_balance
from test_framework.util import assert_equal, start_nodes, \
    websocket_port_by_mc_node_index
from httpCalls.node.getCertificateSigners import http_get_certificate_signers
from httpCalls.node.getKeyRotationProof import http_get_key_rotation_proof

"""
Configuration:
    Start 1 MC node and 1 SC node.
    SC node 1 connected to the MC node 1.
Test:

"""


class SCKeyRotationTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    cert_max_keys = 10
    cert_sig_threshold = 6

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        # After bug spotted in 0.3.4 we test certificate generation with max keys number > 8
        
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            submitter_private_keys_indexes=list(range(self.cert_max_keys))  # SC node owns all schnorr private keys.
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length,
                                                        cert_max_keys=self.cert_max_keys,
                                                        cert_sig_threshold=self.cert_sig_threshold), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Call getCertificateSigners endpoint
        certificate_signers_keys = http_get_certificate_signers(sc_node)
        #TODO Add check

        # Call getKeyRotationProof endpoint and verify we don't have any KeyRotationProof
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 0)
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 1)
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})

        


if __name__ == "__main__":
    SCKeyRotationTest().main()
