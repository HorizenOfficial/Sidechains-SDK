#!/usr/bin/env python3
import time
from decimal import *

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1, NO_KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks
from test_framework.util import assert_equal, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from httpCalls.submitter.getCertifiersKeys import http_get_certifiers_keys
from httpCalls.submitter.getKeyRotationProof import http_get_key_rotation_proof
from httpCalls.transaction.createKeyRotationTransaction import http_create_key_rotation_transaction
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519

"""
Configuration:
    Start 1 MC node and 1 SC node.
    SC node 1 connected to the MC node 1.

Test:
    - Perform a FT.
    - Call getCertificateSigners endpoint and verify that the signer keys are the ones defined during the bootstrapping phase and we have no master keys
    - Call getKeyRotationProof endpoint and verify that we receive a bad circuit error
    - Call createKeyRotationTransaction endpoint and verify that we receive a bad circuit error
"""

def convertSecretToPrivateKey(secret):
    return secret[2:66]


class SCKeyRotationOldCircuitTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    cert_max_keys = 7

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length,
                                                        circuit_type = NO_KEY_ROTATION_CIRCUIT,
                                                        cert_max_keys=self.cert_max_keys,
                                                        sc_creation_version = SC_CREATION_VERSION_1,
                                                        csw_enabled=False), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1


        sc_address_1 = http_wallet_createPrivateKey25519(sc_node)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        epoch_mc_blocks_left -= 1

        # Call getCertificateSigners endpoint
        certificate_signers_keys = http_get_certifiers_keys(sc_node, -1)["certifiersKeys"]
        assert_equal(len(certificate_signers_keys["signingKeys"]), self.cert_max_keys)
        assert_equal(len(certificate_signers_keys["masterKeys"]), 0)

        # Call getKeyRotationProof endpoint
        response = http_get_key_rotation_proof(sc_node, 0, 0, 0)
        assert("error" in response)
        assert_equal(response["error"]["description"], "The current circuit doesn't support key rotation proofs!")

        # Circuit with no key rotation
        response = http_create_key_rotation_transaction(sc_node, 
                        key_type=0,
                        key_index=0,
                        new_key="0",
                        signing_key_signature="0",
                        master_key_signature="0",
                        new_key_signature="0",
                        format=True,
                        automatic_send=True)
        assert("error" in response)
        assert_equal(response["error"]["description"], "The current circuit doesn't support key rotation transaction!")

if __name__ == "__main__":
    SCKeyRotationOldCircuitTest().main()
