#!/usr/bin/env python3
import time

from SidechainTestFramework.account.httpCalls.transaction.createKeyRotationTransaction import \
    http_create_key_rotation_transaction_evm
from SidechainTestFramework.sc_boostrap_info import NO_KEY_ROTATION_CIRCUIT
from SidechainTestFramework.scutil import generate_next_blocks
from httpCalls.submitter.getCertifiersKeys import http_get_certifiers_keys
from httpCalls.submitter.getKeyRotationProof import http_get_key_rotation_proof
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from test_framework.util import assert_equal

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


class SCKeyRotationOldCircuitTest(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=10, circuittype_override=NO_KEY_ROTATION_CIRCUIT)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        cert_max_keys = 7
        sc_node = self.sc_nodes[0]

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        ft_amount_in_zen = 10
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Call getCertificateSigners endpoint
        certificate_signers_keys = http_get_certifiers_keys(sc_node, 0)["certifiersKeys"]
        assert_equal(len(certificate_signers_keys["signingKeys"]), cert_max_keys)
        assert_equal(len(certificate_signers_keys["masterKeys"]), 0)

        # Call getKeyRotationProof endpoint
        response = http_get_key_rotation_proof(sc_node, 0, 0, 0)
        assert ("error" in response)
        assert_equal(response["error"]["description"], "The current circuit doesn't support key rotation proofs!")

        # Circuit with no key rotation
        response = http_create_key_rotation_transaction_evm(sc_node,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key="0",
                                                            signing_key_signature="0",
                                                            master_key_signature="0",
                                                            new_key_signature="0")
        assert ("error" in response)
        assert_equal(response["error"]["description"], "The current circuit doesn't support key rotation transaction!")


if __name__ == "__main__":
    SCKeyRotationOldCircuitTest().main()
