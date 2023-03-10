#!/usr/bin/env python3
import time

import requests

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, generate_cert_signer_secrets, get_withdrawal_epoch
from SidechainTestFramework.secure_enclave_http_api_server import SecureEnclaveApiServer
from httpCalls.block.best import http_block_best
from httpCalls.submitter.getCertifiersKeys import http_get_certifiers_keys
from httpCalls.submitter.getKeyRotationMessageToSign import http_get_key_rotation_message_to_sign_for_signing_key
from httpCalls.submitter.getKeyRotationProof import http_get_key_rotation_proof
from httpCalls.transaction.allTransactions import allTransactions
from httpCalls.transaction.createKeyRotationTransaction import http_create_key_rotation_transaction
from httpCalls.transaction.sendTransaction import sendTransaction
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain

"""
Configuration:
    Start 1 MC node and 1 SC node.
    SC node 1 connected to the MC node 1.
Test:
    - Perform a FT.
    - Create a CertificateKeyRotationTransaction to change the signer key 0 (SK0 -> SK1)
    - Forge a SC block
    - Create a CertificateKeyRotationTransaction to change the signer key 0 (SK0 -> SK2)
    - Forge a SC block
    - Call getKeyRotationProof endpoint and verify that the signing key 0 is SK2
    - Generate enough MC block to reach the end of the Withdrawal Epoch -1
    - Create a CertificateKeyRotationTransaction (T3) to change the signer key 0 (SK0 -> SK3)
    - Create a CertificateKeyRotationTransaction (T4) to change the signer key 1 (SK_1 -> SK_1')
    - Generate a MC block and a SC block
    - Verify that T3 and T4 are not in the block since it is the last block of the epoch
    - Call the allTransactions endpoint and verify that T3 and T4 are still in the mempool
    - Generate one SC block and verify that it doesn't contain T3 and T4
    - Generate the first MC block of the new epoch
    - Try to generate a SC block and force the inclusion of T3 inside it and verify that the block is rejected
      because T3 tries to update a wrong/outdated key
    - Try to generate a SC block and force the inclusion of T4 inside it and verify that the block is rejected
      because T4 tries to update the proper key but with outdated withdrawal epoch number
    - Generate a valid SC block and that it doesn't contain T3 and T4
    - Call the allTransactions endpoint and verify that T3 is not more in the mempool
    - Wait the Certificate to be created and included both in MC and SC
    - Verify that the last SC block doesn't have any transaction
    - Call getKeyRotationProof endpoint and verify that the signing key 0 is SK2
    - Call getCertificateKeys endpoint and verify that the signing key 0 is SK2

"""


def convertSecretToPrivateKey(secret):
    return secret[2:66]


class SCKeyRotationAcrossEpochTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    cert_max_keys = 7
    remote_keys_host = "127.0.0.1"
    remote_keys_port = 5003
    remote_address = f"http://{remote_keys_host}:{remote_keys_port}"

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
                                                        circuit_type=KEY_ROTATION_CIRCUIT,
                                                        cert_max_keys=self.cert_max_keys,
                                                        sc_creation_version=SC_CREATION_VERSION_2,
                                                        csw_enabled=False), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 10)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def secure_enclave_create_signature(self, message_to_sign, public_key="", key=""):
        post_data = {
            "message": message_to_sign,
            "type": "schnorr"
        }

        if public_key != "":
            post_data["publicKey"] = public_key
        elif key != "":
            post_data["privateKey"] = key
        else:
            raise Exception("Either public key or private key should be provided to call createSignature")

        response = requests.post(f"{self.remote_address}/api/v1/createSignature", json=post_data)
        json_response = json.loads(response.text)
        return json_response

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1

        private_signing_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_signers_secrets
        private_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_masters_secrets
        public_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.public_master_keys
        api_server = SecureEnclaveApiServer(
            private_master_keys,
            public_master_keys,
            self.remote_keys_host,
            self.remote_keys_port
        )
        api_server.start()

        sc_address_1 = http_wallet_createPrivateKey25519(sc_node)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform
        # two forward transfers to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress(),
                                      generate_block=False)
        time.sleep(1)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      sc_address_1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        epoch_mc_blocks_left -= 1

        # Call getCertificateKeys endpoint
        certificate_signers_keys = http_get_certifiers_keys(sc_node, -1)["certifiersKeys"]
        assert_equal(len(certificate_signers_keys["signingKeys"]), self.cert_max_keys)
        assert_equal(len(certificate_signers_keys["masterKeys"]), self.cert_max_keys)

        # Call getKeyRotationProof endpoint and verify we don't have any KeyRotationProof
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})

        # Try to change the signing key 0
        new_signing_key = generate_cert_signer_secrets("random_seed", 1)[0]
        new_public_key = new_signing_key.publicKey
        epoch = get_withdrawal_epoch(sc_node)
        new_signing_key_message = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                public_key=public_master_keys[0])["signature"]
        signing_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                 key=private_signing_keys[0])["signature"]
        new_key_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                 key=new_signing_key.secret)["signature"]

        # Change the signing key 0
        http_create_key_rotation_transaction(sc_node,
                                             key_type=0,
                                             key_index=0,
                                             new_key=new_public_key,
                                             signing_key_signature=signing_signature,
                                             master_key_signature=master_signature,
                                             new_key_signature=new_key_signature,
                                             format=True,
                                             automatic_send=True)

        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Check that we have the keyRotationProof
        signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, 0, 0)["result"]["keyRotationProof"]
        assert_equal(signer_key_rotation_proof["index"], 0)
        assert_equal(signer_key_rotation_proof["keyType"]["value"], "SigningKeyRotationProofType")
        assert_equal(signer_key_rotation_proof["masterKeySignature"]["signature"], master_signature)
        assert_equal(signer_key_rotation_proof["signingKeySignature"]["signature"], signing_signature)
        assert_equal(signer_key_rotation_proof["newKey"]["publicKey"], new_public_key)

        # Change again the same signature key
        new_signing_key_2 = generate_cert_signer_secrets("random_seed2", 1)[0]
        new_public_key_2 = new_signing_key_2.publicKey
        new_signing_key_message_2 = \
        http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key_2, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_2 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_2,
                                                                  public_key=public_master_keys[0])["signature"]
        signing_signature_2 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_2,
                                                                   key=private_signing_keys[0])["signature"]
        new_key_signature_2 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_2,
                                                                   key=new_signing_key_2.secret)["signature"]

        # Change again the signing key 0
        http_create_key_rotation_transaction(sc_node,
                                             key_type=0,
                                             key_index=0,
                                             new_key=new_public_key_2,
                                             signing_key_signature=signing_signature_2,
                                             master_key_signature=master_signature_2,
                                             new_key_signature=new_key_signature_2,
                                             format=True,
                                             automatic_send=True)

        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Check that we have the keyRotationProof updated
        signer_key_rotation_proof_2 = http_get_key_rotation_proof(sc_node, 0, 0, 0)["result"]["keyRotationProof"]
        assert_equal(signer_key_rotation_proof_2["index"], 0)
        assert_equal(signer_key_rotation_proof_2["keyType"]["value"], "SigningKeyRotationProofType")
        assert_equal(signer_key_rotation_proof_2["masterKeySignature"]["signature"], master_signature_2)
        assert_equal(signer_key_rotation_proof_2["signingKeySignature"]["signature"], signing_signature_2)
        assert_equal(signer_key_rotation_proof_2["newKey"]["publicKey"], new_public_key_2)

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        mc_node.generate(epoch_mc_blocks_left - 1)

        generate_next_block(sc_node, "first node")

        # Try to update signing key 0
        new_signing_key_3 = generate_cert_signer_secrets("random_seed3", 1)[0]
        new_public_key_3 = new_signing_key_3.publicKey
        new_signing_key_message_3 = http_get_key_rotation_message_to_sign_for_signing_key(
            sc_node, new_public_key_3, epoch)["keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_3 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_3,
                                                                  public_key=public_master_keys[0])["signature"]
        signing_signature_3 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_3,
                                                                   key=private_signing_keys[0])["signature"]
        new_key_signature_3 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_3,
                                                                   key=new_signing_key_3.secret)["signature"]

        # Change the signer key 0
        across_epoch_txhex_t3 = http_create_key_rotation_transaction(sc_node,
                                                                     key_type=0,
                                                                     key_index=0,
                                                                     new_key=new_public_key_3,
                                                                     signing_key_signature=signing_signature_3,
                                                                     master_key_signature=master_signature_3,
                                                                     new_key_signature=new_key_signature_3,
                                                                     format=False,
                                                                     automatic_send=False)["result"]["transactionBytes"]

        across_epoch_txid_t3 = sendTransaction(sc_node, across_epoch_txhex_t3)["result"]["transactionId"]

        # Change the signer key 1 first time
        new_signing_key_idx_1 = generate_cert_signer_secrets("random_seed4", 1)[0]
        new_public_key_idx_1 = new_signing_key_idx_1.publicKey
        new_signing_key_message_4 = http_get_key_rotation_message_to_sign_for_signing_key(
            sc_node, new_public_key_idx_1, epoch)["keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_4 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_4,
                                                                  public_key=public_master_keys[1])["signature"]
        signing_signature_4 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_4,
                                                                   key=private_signing_keys[1])["signature"]
        new_key_signature_4 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_4,
                                                                   key=new_signing_key_idx_1.secret)["signature"]

        across_epoch_txhex_t4 = http_create_key_rotation_transaction(sc_node,
                                                                     key_type=0,
                                                                     key_index=1,
                                                                     new_key=new_public_key_idx_1,
                                                                     signing_key_signature=signing_signature_4,
                                                                     master_key_signature=master_signature_4,
                                                                     new_key_signature=new_key_signature_4,
                                                                     format=False,
                                                                     automatic_send=False)["result"]["transactionBytes"]

        across_epoch_txid_t4 = sendTransaction(sc_node, across_epoch_txhex_t4)["result"]["transactionId"]

        mc_node.generate(1)
        generate_next_block(sc_node, "first node")
        block_json = http_block_best(sc_node)
        assert_equal(len(block_json["sidechainTransactions"]), 0)

        # Verify that the transaction is still in the mempool
        mempool_tx_ids = allTransactions(sc_node, False)['transactionIds']
        assert_equal(2, len(mempool_tx_ids), "Different mempool size found")
        assert_true(across_epoch_txid_t3 in mempool_tx_ids)
        assert_true(across_epoch_txid_t4 in mempool_tx_ids)

        # ******************** WITHDRAWAL EPOCH 1 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 1 START ********************")

        generate_next_blocks(sc_node, "first node", 1)
        assert_equal(len(block_json["sidechainTransactions"]), 0)

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1

        # Try to force the inclusion of transaction T3 inside the next SC block and verify that we have an error
        try:
            generate_next_block(sc_node, "first node", 1, forced_tx=[across_epoch_txhex_t3])[0]
        except:
            pass
        else:
            fail("Exception expected")

        # Try to force the inclusion of transaction T4 inside the next SC block and verify that we have an error
        try:
            generate_next_block(sc_node, "first node", 1, forced_tx=[across_epoch_txhex_t4])[0]
        except:
            pass
        else:
            fail("Exception expected")

        # Generate a SC block
        generate_next_blocks(sc_node, "first node", 1)
        # Verify that the transaction in not in the block
        block_json = http_block_best(sc_node)
        assert_equal(len(block_json["sidechainTransactions"]), 0)

        # Verify that the transaction is not more in the mempool
        mempool = allTransactions(sc_node, True)["transactions"]
        assert_equal(len(mempool), 0)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Generate MC and SC blocks with Cert
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id, sc_node)

        block_json = http_block_best(sc_node)
        assert_true(len(block_json["sidechainTransactions"]) == 0)

        # Verify that we don't have any key rotation in this epoch
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})

        # Verify that we have the updated key
        certificate_signers_keys = http_get_certifiers_keys(sc_node, 0)["certifiersKeys"]
        assert_equal(certificate_signers_keys["signingKeys"][0]["publicKey"], new_public_key_2)


if __name__ == "__main__":
    SCKeyRotationAcrossEpochTest().main()
