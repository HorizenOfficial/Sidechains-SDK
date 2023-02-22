#!/usr/bin/env python3
import time

import requests

from SidechainTestFramework.account.httpCalls.transaction.createKeyRotationTransaction import \
    http_create_key_rotation_transaction_evm
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, generate_cert_signer_secrets, \
    get_withdrawal_epoch
from SidechainTestFramework.secure_enclave_http_api_server import SecureEnclaveApiServer
from httpCalls.block.best import http_block_best
from httpCalls.submitter.getCertifiersKeys import http_get_certifiers_keys
from httpCalls.submitter.getKeyRotationMessageToSign import http_get_key_rotation_message_to_sign_for_signing_key
from httpCalls.submitter.getKeyRotationProof import http_get_key_rotation_proof
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from httpCalls.block.findBlockByID import http_block_findById
from test_framework.util import assert_equal, assert_true

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
    - Generate a MC block and a SC block
    - Verify that T3 is not in the block since it is the last block of the epoch
    - Generate the first MC block of the new epoch
    - Generate a valid SC block and check that it contains T3, but it's now failed
    - Call the allTransactions endpoint and verify that T3 is not more in the mempool
    - Wait the Certificate to be created and included both in MC and SC
    - Verify that the last SC block doesn't have any transaction
    - Call getKeyRotationProof endpoint and verify that the signing key 0 is SK2
    - Call getCertificateKeys endpoint and verify that the signing key 0 is SK2

"""


def convertSecretToPrivateKey(secret):
    return secret[2:66]


class SCKeyRotationAcrossEpochTest(AccountChainSetup):
    def __init__(self):
        self.remote_keys_host = "127.0.0.1"
        self.remote_keys_port = 5001
        self.remote_keys_address = f"http://{self.remote_keys_host}:{self.remote_keys_port}"
        super().__init__(withdrawalEpochLength=10, circuittype_override=KEY_ROTATION_CIRCUIT)

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

        response = requests.post(f"{self.remote_keys_address}/api/v1/createSignature", json=post_data)
        json_response = json.loads(response.text)
        return json_response

    def run_test(self):
        time.sleep(0.1)
        cert_max_keys = 7

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        ft_amount_in_zen = 10
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1

        private_signing_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_signers_secrets
        private_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_masters_secrets
        public_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.public_master_keys
        SecureEnclaveApiServer(
            private_master_keys,
            public_master_keys,
            self.remote_keys_host,
            self.remote_keys_port
        ).start()

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        epoch_mc_blocks_left -= 1

        # Call getCertificateKeys endpoint
        certificate_signers_keys = http_get_certifiers_keys(sc_node, 0)["certifiersKeys"]
        assert_equal(len(certificate_signers_keys["signingKeys"]), cert_max_keys)
        assert_equal(len(certificate_signers_keys["masterKeys"]), cert_max_keys)

        # Call getKeyRotationProof endpoint and verify we don't have any KeyRotationProof
        for i in range(cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})

        # Try to change the signing key 0
        new_signing_key = generate_cert_signer_secrets("random_seed", 1)[0]
        new_public_key = new_signing_key.publicKey
        epoch = get_withdrawal_epoch(sc_node)
        signing_key_message = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                public_key=public_master_keys[0])["signature"]
        signing_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                 key=private_signing_keys[0])["signature"]
        new_key_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                 key=new_signing_key.secret)["signature"]

        # Change the signing key 0
        response = http_create_key_rotation_transaction_evm(sc_node,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key=new_public_key,
                                                            signing_key_signature=signing_signature,
                                                            master_key_signature=master_signature,
                                                            new_key_signature=new_key_signature,
                                                            nonce=0)

        generate_next_blocks(sc_node, "first node", 1)
        receipt = sc_node.rpc_eth_getTransactionReceipt("0x" + response['result']['transactionId'])
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")
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
        epoch = get_withdrawal_epoch(sc_node)
        signing_key_message_2 = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key_2, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_2 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_2,
                                                                  public_key=public_master_keys[0])["signature"]
        signing_signature_2 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_2,
                                                                   key=private_signing_keys[0])["signature"]
        new_key_signature_2 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_2,
                                                                   key=new_signing_key_2.secret)["signature"]

        # Change again the signing key 0
        response = http_create_key_rotation_transaction_evm(sc_node,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key=new_public_key_2,
                                                            signing_key_signature=signing_signature_2,
                                                            master_key_signature=master_signature_2,
                                                            new_key_signature=new_key_signature_2,
                                                            nonce=1)

        generate_next_blocks(sc_node, "first node", 1)
        receipt = sc_node.rpc_eth_getTransactionReceipt("0x" + response['result']['transactionId'])
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")
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
        epoch = get_withdrawal_epoch(sc_node)
        signing_key_message_3 = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key_3, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_3 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_3,
                                                                  public_key=public_master_keys[0])["signature"]
        signing_signature_3 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_3,
                                                                   key=private_signing_keys[0])["signature"]
        new_key_signature_3 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_3,
                                                                   key=new_signing_key_3.secret)["signature"]

        # Change the signer key 0
        response = http_create_key_rotation_transaction_evm(sc_node,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key=new_public_key_3,
                                                            signing_key_signature=signing_signature_3,
                                                            master_key_signature=master_signature_3,
                                                            new_key_signature=new_key_signature_3,
                                                            nonce=2)

        mc_node.generate(1)
        block_id = generate_next_block(sc_node, "first node")
        sc_txs = http_block_findById(sc_node, block_id)['block']['sidechainTransactions']
        assert_equal(0, len(sc_txs), "There should be no SC TXs in a block")
        # isWithdrawalEpochLastBlock condition is true, no SC TXs allowed

        # Verify that we have the updated key
        certificate_signers_keys = http_get_certifiers_keys(sc_node, 0)
        epoch_one_keys_root_hash = certificate_signers_keys["keysRootHash"]
        assert_equal(certificate_signers_keys["certifiersKeys"]["signingKeys"][0]["publicKey"], new_public_key_2)

        # ******************** WITHDRAWAL EPOCH 1 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 1 START ********************")
        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_block(sc_node, "first node")

        # assert that transaction generated and valid in previous epoch, now should be marked as failed
        receipt = sc_node.rpc_eth_getTransactionReceipt("0x" + response['result']['transactionId'])
        status = int(receipt['result']['status'], 16)
        assert_equal(0, status, "Wrong tx status in receipt")

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        assert_equal(epoch_one_keys_root_hash, cert['vFieldElementCertificateField'][0],
                     "Certificate Keys Root Hash incorrect")
        assert_equal(cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id, sc_node)

        block_json = http_block_best(sc_node)
        assert_true(len(block_json["sidechainTransactions"]) == 0)

        # Verify that we don't have any key rotation in this epoch
        for i in range(cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})


if __name__ == "__main__":
    SCKeyRotationAcrossEpochTest().main()
