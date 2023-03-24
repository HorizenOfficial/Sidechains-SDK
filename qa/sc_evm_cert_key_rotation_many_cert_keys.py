#!/usr/bin/env python3
import time
from binascii import hexlify

import requests
from eth_abi import decode
from eth_utils import event_signature_to_log_topic, encode_hex

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.allWithdrawRequests import all_withdrawal_requests
from SidechainTestFramework.account.httpCalls.transaction.createKeyRotationTransaction import \
    http_create_key_rotation_transaction_evm
from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, generate_cert_signer_secrets, \
    get_withdrawal_epoch
from SidechainTestFramework.secure_enclave_http_api_server import SecureEnclaveApiServer
from httpCalls.submitter.getCertifiersKeys import http_get_certifiers_keys
from httpCalls.submitter.getKeyRotationMessageToSign import http_get_key_rotation_message_to_sign_for_signing_key, \
    http_get_key_rotation_message_to_sign_for_master_key
from httpCalls.submitter.getKeyRotationProof import http_get_key_rotation_proof
from test_framework.util import assert_equal, assert_true, assert_false, hex_str_to_bytes

"""
Configuration:
    Start 1 MC node and 1 SC node.
    SC node 1 connected to the MC node 1.

Test:
    ######## WITHDRAWAL EPOCH 0 ##########
    - Perform a FT and split the FT box into multiple boxes.    
    - Call the getKeyRotationProof endpoint and verify that we don't have any key rotation proof stored.
    - Call the getCertificateSigners endpoint and verify that the signers and master keys correspond to the genesis ones.
    - Call the signSchnorrPublicKey endpoint and creates the signature necessary for the CertificateKeyRotationTransaction.
    - Create a keyRotationTransaction and change all signer keys 
    - Call the getKeyRotationProof and verify that we have a keyRotationProof for all signer keys
    - End the WE and verify that the certificates is added to the MC and SC.
    ######## WITHDRAWAL EPOCH 1 ##########
    - Call the getKeyRotationProof endpoint and verify that we don't have any key rotation proof stored for epoch 1.
    - Call the getCertificateSigners endpoint and verify that the signers keys match the new keys
    - End the WE and verify that the certificates is added to the MC and SC.
"""


def convertSecretToPrivateKey(secret):
    return secret[2:66]


def check_key_rotation_event(event, key_type, key_index, key_value, epoch):
    assert_equal(3, len(event['topics']), "Wrong number of topics in event")
    event_id = event['topics'][0]

    event_signature = encode_hex(event_signature_to_log_topic('SubmitKeyRotation(uint32,uint32,bytes32,bytes1,uint32)'))
    assert_equal(event_signature, event_id, "Wrong event signature in topics")

    key_type_actual = decode(['uint32'], hex_str_to_bytes(event['topics'][1][2:]))[0]
    assert_equal(key_type, key_type_actual, "Wrong key type in topics")

    key_index_actual = decode(['uint32'], hex_str_to_bytes(event['topics'][2][2:]))[0]
    assert_equal(key_index, key_index_actual, "Wrong key index in topics")

    (key_value_part1, key_value_part2, epoch_actual) = decode(['bytes32', 'bytes1', 'uint32'],
                                                              hex_str_to_bytes(event['data'][2:]))
    assert_equal(key_value, hexlify(key_value_part1 + key_value_part2).decode('ascii'), "Wrong key value in event")
    assert_equal(epoch, epoch_actual, "Wrong epoch in event")


class SCKeyRotationManyKeysTest(AccountChainSetup):

    def __init__(self):
        self.remote_keys_host = "127.0.0.1"
        self.remote_keys_port = 5200
        self.remote_keys_address = f"http://{self.remote_keys_host}:{self.remote_keys_port}"
        self.cert_max_keys = 7
        self.submitter_private_keys_indexes = list(range(self.cert_max_keys))
        self.cert_sig_threshold = 5
        super().__init__(withdrawalEpochLength=10, circuittype_override=KEY_ROTATION_CIRCUIT,
                         remote_keys_manager_enabled=True, remote_keys_server_addresses=[self.remote_keys_address],
                         cert_max_keys=self.cert_max_keys, cert_sig_threshold=self.cert_sig_threshold,
                         submitters_private_keys_indexes=[self.submitter_private_keys_indexes])

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

        response = requests.post(f"{self.remote_keys_address}/api/v1/createSignature",
                                 json=post_data)
        json_response = json.loads(response.text)
        return json_response

    def run_test(self):
        time.sleep(0.1)

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

        # Generate new signing keys and master keys
        new_signing_keys = []
        new_master_keys = []
        for i in range(self.cert_max_keys):
            # Generate new signer keys
            new_s_key = generate_cert_signer_secrets(f"random_seed_new_signer{i}", 1)[0]
            new_signing_keys.append(new_s_key)

            # Generate new master keys
            new_m_key = generate_cert_signer_secrets(f"random_seed_new_master{i}", 1)[0]
            new_master_keys.append(new_m_key)

        SecureEnclaveApiServer(private_master_keys, public_master_keys, self.remote_keys_host,
                               self.remote_keys_port).start()

        current_epoch_number = 0
        list_of_wr = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(0, len(list_of_wr))

        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()
        epoch_mc_blocks_left -= 1

        # # Split the FT in multiple boxes
        # sendCoinsToMultipleAddress(sc_node, [sc_address_1 for _ in range(20)], [1000 for _ in range(20)], 0)

        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Call getCertificateSigners endpoint
        certificate_signers_keys = http_get_certifiers_keys(sc_node, -1)
        epoch_zero_keys_root_hash = certificate_signers_keys["keysRootHash"]
        assert_equal(len(certificate_signers_keys["certifiersKeys"]["signingKeys"]), self.cert_max_keys)
        assert_equal(len(certificate_signers_keys["certifiersKeys"]["masterKeys"]), self.cert_max_keys)

        # Call getKeyRotationProof endpoint and verify we don't have any KeyRotationProof
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})

        # Get messages to sign
        epoch = get_withdrawal_epoch(sc_node)
        signing_key_messages = []

        logging.debug("Creating " + str(len(new_signing_keys)) + " Signing Key Messages...")
        for index, key in enumerate(new_signing_keys):
            message = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, key.publicKey, epoch)[
                "keyRotationMessageToSign"]
            signing_key_messages.append(message)
        logging.debug("Successfully Created " + str(len(signing_key_messages)) + " Signing Key Messages.")

        # Sign the new signing key with the old keys
        master_signatures = []
        signing_signatures = []
        new_key_signatures = []

        for index, message in enumerate(signing_key_messages):
            logging.debug("Signing Message: Master Signature Index - " + str(index))
            master_signatures.append(self.secure_enclave_create_signature(message_to_sign=message,
                                                                          public_key=public_master_keys[index])[
                                         "signature"])
            logging.debug("Signing Message: signing_signatures Index - " + str(index))
            signing_signatures.append(self.secure_enclave_create_signature(message_to_sign=message,
                                                                           key=private_signing_keys[index])[
                                          "signature"])
            logging.debug("Signing Message: new_key_signatures Index - " + str(index))
            new_key_signatures.append(self.secure_enclave_create_signature(message_to_sign=message,
                                                                           key=new_signing_keys[index].secret)[
                                          "signature"])

        # POSITIVE CASE

        nonce = 0

        # Change all signing keys

        responses = []
        for index, new_signing_key in enumerate(new_signing_keys):
            if index > 0:
                nonce += 1

            logging.debug(f"new_signing_key.publicKey: {new_signing_key.publicKey}")
            logging.debug(f"signing_signatures[{index}]: {signing_signatures[index]}")
            logging.debug(f"master_signatures[{index}]: {master_signatures[index]}")
            logging.debug(f"new_key_signatures[{index}]: {new_key_signatures[index]}")
            response = http_create_key_rotation_transaction_evm(sc_node,
                                                                key_type=0,
                                                                key_index=index,
                                                                nonce=nonce,
                                                                new_key=new_signing_key.publicKey,
                                                                signing_key_signature=signing_signatures[index],
                                                                master_key_signature=master_signatures[index],
                                                                new_key_signature=new_key_signatures[index])
            responses.append(response)

        for response in responses:
            logging.debug(response)

        for index, response in enumerate(responses):
            assert_false("error" in response, "Response " + str(index) + "errored.")

        generate_next_blocks(sc_node, "first node", 1)

        for index, response in enumerate(responses):
            logging.debug(f"Getting rpc_eth_getTransactionReceipt: {index}")
            receipt = sc_node.rpc_eth_getTransactionReceipt("0x" + response['result']['transactionId'])
            logging.debug(f"Getting status: {index}")
            status = int(receipt['result']['status'], 16)
            logging.debug(f"Asserting status is correct: {index}")
            assert_equal(1, status, "Wrong tx status in receipt " + "Response: " + str(index))
            logging.debug(f"Checking Key Rotation Event: {index}")
            check_key_rotation_event(receipt['result']['logs'][0], 0, index, new_signing_keys[index].publicKey, 0)

        self.sc_sync_all()

        # Check that we have the keyRotationProof

        for index, key in enumerate(new_signing_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, index, 0)["result"]["keyRotationProof"]

            assert_equal(signer_key_rotation_proof["index"], index)
            assert_equal(signer_key_rotation_proof["keyType"]["value"], "SigningKeyRotationProofType")
            assert_equal(signer_key_rotation_proof["masterKeySignature"]["signature"], master_signatures[index])
            assert_equal(signer_key_rotation_proof["signingKeySignature"]["signature"], signing_signatures[index])
            assert_equal(signer_key_rotation_proof["newKey"]["publicKey"], key.publicKey)

        # # Generate enough MC blocks to reach the end of the withdrawal epoch

        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]
        sc_block_id = generate_next_block(sc_node, "first node")

        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)

        # # Verify keys updated in epoch 0

        certificate_signers_keys = http_get_certifiers_keys(sc_node, 0)
        epoch_one_keys_root_hash = certificate_signers_keys["keysRootHash"]

        for index, key in enumerate(new_signing_keys):
            assert_equal(certificate_signers_keys["certifiersKeys"]["signingKeys"][index]["publicKey"], key.publicKey)


        # ******************** WITHDRAWAL EPOCH 1 START ********************

        logging.info("******************** WITHDRAWAL EPOCH 1 START ********************")

        # Generate first mc block of the next epoch

        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_blocks(sc_node, "first node", 1)

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
        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id, sc_node)

        # Verify that we don't have any key rotation in this epoch
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})


if __name__ == "__main__":
    SCKeyRotationManyKeysTest().main()
