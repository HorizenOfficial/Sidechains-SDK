#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block, assert_equal
from SidechainTestFramework.secure_enclave_http_api_server import SecureEnclaveApiServer
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index

"""
Check Certificate submission behaviour with enabled Secure Enclave.

Configuration:
    Start 1 MC node and 1 SC node.
    Enable Secure Enclave.
    SC node manages keys 0,1,2.
    Secure Enclave manages keys the remaining keys (from 3 up to MAX_NUMBER_OF_KEYS)
    SC Node together with secure enclave has enough keys to produce certificate signatures.

Test:
    - Generate a few MC and SC blocks to reach the end of the withdrawal epoch.
    - Check that certificate was generated.
"""


class ScCertSubmitterSecureEnclave(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1
    remote_keys_ip_address = "127.0.0.1"
    remote_keys_port = 5000

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    sc_creation_amount = 100  # Zen

    MAX_NUMBER_OF_KEYS = 47

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-logtimemicros=1',
                                        '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        remote_address = f"http://{self.remote_keys_ip_address}:{self.remote_keys_port + self.options.parallel}"

        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True, True, list([0, 1, 2], ), remote_keys_manager_enabled=True, remote_keys_server_address=remote_address
            # certificate submitter is enabled, signing is enabled with 3 schnorr PKs
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, self.sc_creation_amount, self.sc_withdrawal_epoch_length,
                           cert_max_keys=self.MAX_NUMBER_OF_KEYS, csw_enabled=True),
            sc_node_1_configuration)

        # rewind sc genesis block timestamp for 10 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]

        api_server = SecureEnclaveApiServer(
            self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_signers_secrets[4:],
            self.sc_nodes_bootstrap_info.certificate_proof_info.public_signing_keys[4:],
            self.remote_keys_ip_address,
            self.remote_keys_port + self.options.parallel
        )
        api_server.start()

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
        generate_next_block(sc_node1, "first node")  # 1 MC block to reach the end of WE
        generate_next_block(sc_node1, "first node")  # 1 MC block to trigger Submitter logic
        self.sc_sync_all()  # Sync SC nodes

        # Wait for Certificates appearance
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] < 1 and sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificates in the MC mempool...")
            if sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
                logging.info("sc_node1 generating certificate now.")
            time.sleep(2)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificates was not added to MC node mempool.")
        assert_equal(self.MAX_NUMBER_OF_KEYS - 1, mc_node.getrawtransaction(mc_node.getrawmempool()[0], 1)['cert']['quality'],
                     "Certificate has wrong quality")
        logging.info("Node with Secure Enclave was able to sign, collect signatures and emit certificate.")


if __name__ == "__main__":
    ScCertSubmitterSecureEnclave().main()
