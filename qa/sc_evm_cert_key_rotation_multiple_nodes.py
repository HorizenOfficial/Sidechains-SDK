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
    Start 1 MC node and 3 SC node.
    SC node 1 connected to the MC node 1.
    SC nodes are connected between each other.
    Every SC node has its own remote key manager.
    Certificate signer/master keys pairs are split between all 3 SC nodes

Note:
    This test is a copy of "sc_evm_cert_key_rotation.py" with an adaptation to 3 SC nodes and without some negative cases.
    
Test:
    ######## WITHDRAWAL EPOCH 0 ##########
    - Perform a FT and split the FT box into multiple boxes.    
    - Call the getKeyRotationProof endpoint and verify that we don't have any key rotation proof stored.
    - Call the getCertificateSigners endpoint and verify that the signers and master keys correspond to the genesis ones.
    - Call the signSchnorrPublicKey endpoint and creates the signature necessary for the CertificateKeyRotationTransaction.
    - End the WE and verify that the certificates is added to the MC and SC.
    ######## WITHDRAWAL EPOCH 1 ##########
    - Verify that certificate was created using genesis keys
    - Create a keyRotationTransaction and change the signer key 0 (SK0 -> SK1).
    - Call the getKeyRotationProof and verify that we have a keyRotationProof for the signer key 0 (SK1).
    - Create a keyRotationTransaction that change again the signer key 0 (SK0 -> SK3).
    - Call the getKeyRotationProof and verify that we have a keyRotationProof for the signer key 0 (SK3).
    - Create a keyRotationTransaction and change the master key 0 (MK0 -> MK1).
    - End the WE and verify that the certificates is added to the MC and SC.
    ######## WITHDRAWAL EPOCH 2 ##########
    - Call the getCertificateSigners endpoint and verify that all the signing and master keys are updated.
    - End the WE and verify that the certificates is added to the MC and SC.
    ######## WITHDRAWAL EPOCH 3 ##########
    - Call the getCertificateSigners endpoint and verify that all the signing and master keys are updated.
    - End the WE and verify that the certificates is added to the MC and SC.
    - Verify that certificate was created using all new keys
    ######## WITHDRAWAL EPOCH 4 ##########
    - Call the getKeyRotationProof endpoint and verify that we don't have any key rotation proof stored for epoch 4.
    - Call the getCertificateSigners endpoint and verify that the signers key 0 = SK3 and master key 0 = MK1.
    - Create a keyRotationTransaction and change the signer key 0 (SK3 -> SK4).
    - End the WE and verify that the certificates is added to the MC and SC.
    ######## WITHDRAWAL EPOCH 5 ##########
    - Call the getKeyRotationProof endpoint and verify that we don't have any key rotation proof stored for epoch 5.
    - Call the getCertificateSigners endpoint and verify that the signers key 0 = SK4.
    - Update ALL the signing and master keys of all the nodes
    - Call the getKeyRotationProof endpoint and verify that we have a KeyRotationProof for each signing and master keys.
    - End the WE and verify that the certificates is added to the MC and SC.
     ######## WITHDRAWAL EPOCH 6 ##########
    - Call the getCertificateSigners endpoint and verify that all the signing and master keys are updated.
     ######## WITHDRAWAL EPOCH 7 ##########
    - Verify that certificate was created using all new keys
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


class SCKeyRotationTestMultipleNodes(AccountChainSetup):

    def __init__(self):
        number_of_sidechain_nodes = 3
        self.remote_keys_host = "127.0.0.1"
        self.remote_keys_ports = []
        remote_keys_addresses = []
        for x in range(number_of_sidechain_nodes):
            self.remote_keys_ports.append(5100 + x)
            remote_keys_addresses.append(f"http://{self.remote_keys_host}:{self.remote_keys_ports[x]}")

        cert_max_keys = 7
        cert_sig_threshold = 5

        self.first_node_first_key_idx = 0
        self.second_node_first_key_idx = 3
        self.third_node_first_key_idx = 6

        submitters_private_keys_indexes = [
            range(self.first_node_first_key_idx, self.second_node_first_key_idx),   # first node  -   3 keys
            range(self.second_node_first_key_idx, self.third_node_first_key_idx),   # second node -   3 keys
            range(self.third_node_first_key_idx, cert_max_keys)                     # thirst node -   2 keys
        ]

        # Uncomment for the test with a bit signers set.
        # Note: run the test with --restapitimeout=30 until https://horizenlabs.atlassian.net/browse/SDK-826 is done

        # cert_max_keys = 47
        # cert_sig_threshold = 24
        # self.first_node_first_key_idx = 0
        # self.second_node_first_key_idx = 15
        # self.third_node_first_key_idx = 30
        #
        # submitters_private_keys_indexes = [
        #     range(self.first_node_first_key_idx, self.second_node_first_key_idx),  # first node  -   15 keys
        #     range(self.second_node_first_key_idx, self.third_node_first_key_idx),  # second node -   15 keys
        #     range(self.third_node_first_key_idx, cert_max_keys)                    # thirst node -   17 keys
        # ]

        super().__init__(number_of_sidechain_nodes=number_of_sidechain_nodes,
                         withdrawalEpochLength=10,
                         circuittype_override=KEY_ROTATION_CIRCUIT,
                         remote_keys_manager_enabled=True,
                         remote_keys_server_addresses=remote_keys_addresses,
                         cert_max_keys=cert_max_keys,
                         cert_sig_threshold=cert_sig_threshold,
                         submitters_private_keys_indexes=submitters_private_keys_indexes)

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

        response = requests.post(f"{self.remote_keys_server_address[0]}/api/v1/createSignature",
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
        sc_node_1 = self.sc_nodes[0]

        epoch_mc_blocks_left = self.withdrawalEpochLength - 1

        # Configure secure enclaves
        # TODO: looks very messy, need to simplify names and flow
        all_signer_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_signers_secrets
        all_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_masters_secrets
        all_public_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.public_master_keys

        # First node:
        first_node_private_signing_keys = all_signer_keys[:self.second_node_first_key_idx]
        first_node_private_master_keys = all_master_keys[:self.second_node_first_key_idx]
        first_node_public_master_keys = all_public_master_keys[:self.second_node_first_key_idx]

        new_signing_key = generate_cert_signer_secrets("random_seed", 1)[0]
        new_public_key = new_signing_key.publicKey
        new_signing_key_2 = generate_cert_signer_secrets("random_seed2", 1)[0]
        new_public_key_2 = new_signing_key_2.publicKey
        new_master_key = generate_cert_signer_secrets("random_seed3", 1)[0]
        new_public_key_3 = new_master_key.publicKey
        new_signing_key_4 = generate_cert_signer_secrets("random_seed4", 1)[0]
        new_public_key_4 = new_signing_key_4.publicKey

        first_node_private_master_keys.append(new_signing_key.secret)
        first_node_public_master_keys.append(new_public_key)
        first_node_private_master_keys.append(new_signing_key_2.secret)
        first_node_public_master_keys.append(new_public_key_2)
        first_node_private_master_keys.append(new_master_key.secret)
        first_node_public_master_keys.append(new_public_key_3)
        first_node_private_master_keys.append(new_signing_key_4.secret)
        first_node_public_master_keys.append(new_public_key_4)

        # Second node:
        second_node_private_signing_keys = all_signer_keys[self.second_node_first_key_idx:self.third_node_first_key_idx]
        second_node_private_master_keys = all_master_keys[self.second_node_first_key_idx:self.third_node_first_key_idx]
        second_node_public_master_keys = all_public_master_keys[self.second_node_first_key_idx:self.third_node_first_key_idx]

        # Third node:
        third_node_private_signing_keys = all_signer_keys[self.third_node_first_key_idx:]
        third_node_private_master_keys = all_master_keys[self.third_node_first_key_idx:]
        third_node_public_master_keys = all_public_master_keys[self.third_node_first_key_idx:]

        # Change ALL the signing keys and ALL the master keys
        new_signing_keys = []
        new_master_keys = []
        for i in range(self.cert_max_keys):
            new_s_key = generate_cert_signer_secrets(f"random_seed5{i}", 1)[0]
            new_signing_keys += [new_s_key]

            new_m_key = generate_cert_signer_secrets(f"random_seed6{i}", 1)[0]
            new_master_keys += [new_m_key]

            # TODO: why all in master_?
            if i < self.second_node_first_key_idx:
                first_node_private_master_keys.append(new_s_key.secret)
                first_node_public_master_keys.append(new_s_key.publicKey)
                first_node_private_master_keys.append(new_m_key.secret)
                first_node_public_master_keys.append(new_m_key.publicKey)
            elif i < self.third_node_first_key_idx:
                second_node_private_master_keys.append(new_s_key.secret)
                second_node_public_master_keys.append(new_s_key.publicKey)
                second_node_private_master_keys.append(new_m_key.secret)
                second_node_public_master_keys.append(new_m_key.publicKey)
            else:
                third_node_private_master_keys.append(new_s_key.secret)
                third_node_public_master_keys.append(new_s_key.publicKey)
                third_node_private_master_keys.append(new_m_key.secret)
                third_node_public_master_keys.append(new_m_key.publicKey)

        # Start enclaves
        SecureEnclaveApiServer(first_node_private_master_keys, first_node_public_master_keys, self.remote_keys_host,
                               self.remote_keys_ports[0]).start()

        SecureEnclaveApiServer(second_node_private_master_keys, second_node_public_master_keys, self.remote_keys_host,
                               self.remote_keys_ports[1]).start()

        SecureEnclaveApiServer(third_node_private_master_keys, third_node_public_master_keys, self.remote_keys_host,
                               self.remote_keys_ports[2]).start()

        current_epoch_number = 0
        list_of_wr = all_withdrawal_requests(sc_node_1, current_epoch_number)
        assert_equal(0, len(list_of_wr))

        self.sc_sync_all()
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()
        epoch_mc_blocks_left -= 1

        # # Split the FT in multiple boxes
        # sendCoinsToMultipleAddress(sc_node, [sc_address_1 for _ in range(20)], [1000 for _ in range(20)], 0)

        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # ******************** WITHDRAWAL EPOCH 0 ********************

        # Call getCertificateSigners endpoint
        epoch_zero_keys_root_hashes = []
        for sc_node in self.sc_nodes:
            certificate_signers_keys = http_get_certifiers_keys(sc_node, -1)
            epoch_zero_keys_root_hash = certificate_signers_keys["keysRootHash"]
            assert_equal(len(certificate_signers_keys["certifiersKeys"]["signingKeys"]), self.cert_max_keys)
            assert_equal(len(certificate_signers_keys["certifiersKeys"]["masterKeys"]), self.cert_max_keys)
            epoch_zero_keys_root_hashes.append(epoch_zero_keys_root_hash)

        # Call getKeyRotationProof endpoint and verify we don't have any KeyRotationProof
        for sc_node in self.sc_nodes:
            for i in range(self.cert_max_keys):
                signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 0)["result"]
                master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 1)["result"]
                assert_equal(signer_key_rotation_proof, {})
                assert_equal(master_key_rotation_proof, {})

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]
        sc_block_id = generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node_1)

        # ******************** WITHDRAWAL EPOCH 1 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 1 START ********************")

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        # Check all nodes have the correct keys root hash
        for key_root_hash in epoch_zero_keys_root_hashes:
            assert_equal(key_root_hash, cert['vFieldElementCertificateField'][0],
                         "Certificate Keys Root Hash incorrect")
        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node_1, "first node", 1)[0]
        self.sc_sync_all()
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id, sc_node_1)

        # Try to change the signing key 0
        epoch = get_withdrawal_epoch(sc_node_1)
        signing_key_message = http_get_key_rotation_message_to_sign_for_signing_key(sc_node_1, new_public_key, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                public_key=first_node_public_master_keys[0])["signature"]
        signing_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                 key=first_node_private_signing_keys[0])["signature"]
        new_key_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                 key=new_signing_key.secret)["signature"]


        # Change the signing key 0
        response = http_create_key_rotation_transaction_evm(sc_node_1,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key=new_public_key,
                                                            signing_key_signature=signing_signature,
                                                            master_key_signature=master_signature,
                                                            new_key_signature=new_key_signature)
        assert_false("error" in response)
        generate_next_blocks(sc_node_1, "first node", 1)
        receipt = sc_node_1.rpc_eth_getTransactionReceipt("0x" + response['result']['transactionId'])
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")
        check_key_rotation_event(receipt['result']['logs'][0], 0, 0, new_public_key, epoch)
        self.sc_sync_all()

        # Check that we have the keyRotationProof
        for sc_node in self.sc_nodes:
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, 0, 0)["result"]["keyRotationProof"]
            assert_equal(signer_key_rotation_proof["index"], 0)
            assert_equal(signer_key_rotation_proof["keyType"]["value"], "SigningKeyRotationProofType")
            assert_equal(signer_key_rotation_proof["masterKeySignature"]["signature"], master_signature)
            assert_equal(signer_key_rotation_proof["signingKeySignature"]["signature"], signing_signature)
            assert_equal(signer_key_rotation_proof["newKey"]["publicKey"], new_public_key)

        # Change again the same signature key

        signing_key_message_2 = http_get_key_rotation_message_to_sign_for_signing_key(sc_node_1, new_public_key_2, epoch)[
            "keyRotationMessageToSign"]
        # Sign the new signing key with the old keys
        master_signature_2 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_2,
                                                                  public_key=first_node_public_master_keys[0])["signature"]
        signing_signature_2 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_2,
                                                                   key=first_node_private_signing_keys[0])["signature"]
        new_key_signature_2 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_2,
                                                                   key=new_signing_key_2.secret)["signature"]

        # Try with old signatures
        response = http_create_key_rotation_transaction_evm(sc_node_1,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key=new_public_key_2,
                                                            signing_key_signature=signing_signature,
                                                            master_key_signature=master_signature,
                                                            new_key_signature=new_key_signature_2)
        generate_next_blocks(sc_node_1, "first node", 1)
        receipt = sc_node_1.rpc_eth_getTransactionReceipt("0x" + response['result']['transactionId'])
        status = int(receipt['result']['status'], 16)
        assert_equal(0, status, "Wrong tx status in receipt")

        # Use the new signatures
        response = http_create_key_rotation_transaction_evm(sc_node_1,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key=new_public_key_2,
                                                            signing_key_signature=signing_signature_2,
                                                            master_key_signature=master_signature_2,
                                                            new_key_signature=new_key_signature_2)
        assert_false("error" in response)

        self.sc_sync_all()
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Check that we have the keyRotationProof updated
        for sc_node in self.sc_nodes:
            signer_key_rotation_proof_2 = http_get_key_rotation_proof(sc_node, epoch, 0, 0)["result"]["keyRotationProof"]
            assert_equal(signer_key_rotation_proof_2["index"], 0)
            assert_equal(signer_key_rotation_proof_2["keyType"]["value"], "SigningKeyRotationProofType")
            assert_equal(signer_key_rotation_proof_2["masterKeySignature"]["signature"], master_signature_2)
            assert_equal(signer_key_rotation_proof_2["signingKeySignature"]["signature"], signing_signature_2)
            assert_equal(signer_key_rotation_proof_2["newKey"]["publicKey"], new_public_key_2)

        # Try to update the master key 0
        signing_key_message_3 = http_get_key_rotation_message_to_sign_for_master_key(sc_node_1, new_public_key_3, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_3 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_3,
                                                                  public_key=first_node_public_master_keys[0])["signature"]
        signing_signature_3 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_3,
                                                                   key=first_node_private_signing_keys[0])["signature"]
        new_key_signature_3 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_3,
                                                                   key=new_master_key.secret)["signature"]

        # Change the master key 0
        response = http_create_key_rotation_transaction_evm(sc_node_1,
                                                            key_type=1,
                                                            key_index=0,
                                                            new_key=new_public_key_3,
                                                            signing_key_signature=signing_signature_3,
                                                            master_key_signature=master_signature_3,
                                                            new_key_signature=new_key_signature_3)
        assert_false("error" in response)
        generate_next_blocks(sc_node_1, "first node", 1)
        receipt = sc_node_1.rpc_eth_getTransactionReceipt("0x" + response['result']['transactionId'])
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")
        check_key_rotation_event(receipt['result']['logs'][0], 1, 0, new_public_key_3, epoch)
        self.sc_sync_all()

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we1_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]
        sc_block_id = generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        check_mcreferencedata_presence(we1_end_mcblock_hash, sc_block_id, sc_node_1)

        # Verify keys updated in epoch 1
        epoch_one_keys_root_hashes = []
        for sc_node in self.sc_nodes:
            certificate_signers_keys = http_get_certifiers_keys(sc_node, epoch)
            epoch_one_keys_root_hash = certificate_signers_keys["keysRootHash"]
            assert_equal(certificate_signers_keys["certifiersKeys"]["signingKeys"][0]["publicKey"], new_public_key_2)
            assert_equal(certificate_signers_keys["certifiersKeys"]["masterKeys"][0]["publicKey"], new_public_key_3)
            epoch_one_keys_root_hashes.append(epoch_one_keys_root_hash)


            # ******************** WITHDRAWAL EPOCH 2 START ( No Key Rotation) ********************
        logging.info("******************** WITHDRAWAL EPOCH 2 START ********************")

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        # Check all nodes have the correct keys root hash
        for key_root_hash in epoch_one_keys_root_hashes:
            assert_equal(key_root_hash, cert['vFieldElementCertificateField'][0],
                        "Certificate Keys Root Hash incorrect")

        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we2_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node_1, "first node", 1)[0]
        self.sc_sync_all()
        check_mcreference_presence(we2_2_mcblock_hash, scblock_id, sc_node_1)

        # Get current epoch
        epoch = get_withdrawal_epoch(sc_node_1)

        # Verify that we don't have any key rotation in this epoch
        for sc_node in self.sc_nodes:
            for i in range(self.cert_max_keys):
                signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 0)["result"]
                master_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 1)["result"]
                assert_equal(signer_key_rotation_proof, {})
                assert_equal(master_key_rotation_proof, {})

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we2_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]
        sc_block_id = generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        check_mcreferencedata_presence(we2_end_mcblock_hash, sc_block_id, sc_node_1)

        # Verify keys updated in epoch 2
        epoch_two_keys_root_hashes = []
        for sc_node in self.sc_nodes:
            certificate_signers_keys = http_get_certifiers_keys(sc_node, epoch)
            epoch_two_keys_root_hash = certificate_signers_keys["keysRootHash"]
            epoch_two_keys_root_hashes.append(epoch_two_keys_root_hash)

            # ******************** WITHDRAWAL EPOCH 3 START (No Key Rotation) ********************
        logging.info("******************** WITHDRAWAL EPOCH 3 START ********************")

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        # Check all nodes have the correct keys root hash
        for key_root_hash in epoch_two_keys_root_hashes:
            assert_equal(key_root_hash, cert['vFieldElementCertificateField'][0],
                         "Certificate Keys Root Hash incorrect")
        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we3_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node_1, "first node", 1)[0]
        self.sc_sync_all()
        check_mcreference_presence(we3_2_mcblock_hash, scblock_id, sc_node_1)

        # Get current epoch
        epoch = get_withdrawal_epoch(sc_node_1)

        # Verify that we don't have any key rotation in this epoch
        for sc_node in self.sc_nodes:
            for i in range(self.cert_max_keys):
                signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 0)["result"]
                master_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 1)["result"]
                assert_equal(signer_key_rotation_proof, {})
                assert_equal(master_key_rotation_proof, {})

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we3_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]
        sc_block_id = generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        check_mcreferencedata_presence(we3_end_mcblock_hash, sc_block_id, sc_node_1)

        # Get Epoch 3 Key Root Hashes
        epoch_three_keys_root_hashes = []
        for sc_node in self.sc_nodes:
            certificate_signers_keys = http_get_certifiers_keys(sc_node, epoch)
            epoch_three_keys_root_hash = certificate_signers_keys["keysRootHash"]
            epoch_three_keys_root_hashes.append(epoch_three_keys_root_hash)

        # ******************** WITHDRAWAL EPOCH 4 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 4 START ********************")

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        # Check all nodes have the correct keys root hash
        for key_root_hash in epoch_three_keys_root_hashes:
            assert_equal(key_root_hash, cert['vFieldElementCertificateField'][0],
                         "Certificate Keys Root Hash incorrect")
        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we4_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node_1, "first node", 1)[0]
        self.sc_sync_all()
        check_mcreference_presence(we4_2_mcblock_hash, scblock_id, sc_node_1)

        # Verify that we don't have any key rotation in this epoch
        for sc_node in self.sc_nodes:
            for i in range(self.cert_max_keys):
                signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 0)["result"]
                master_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 1)["result"]
                assert_equal(signer_key_rotation_proof, {})
                assert_equal(master_key_rotation_proof, {})

        epoch = get_withdrawal_epoch(sc_node_1)

        # Update again the signing key 0
        signing_key_message_4 = http_get_key_rotation_message_to_sign_for_signing_key(sc_node_1, new_public_key_4, epoch)[
            "keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_4 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_4,
                                                                  key=new_master_key.secret)["signature"]
        signing_signature_4 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_4,
                                                                   key=new_signing_key_2.secret)["signature"]
        new_key_signature_4 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_4,
                                                                   key=new_signing_key_4.secret)["signature"]

        # Create the key rotation transaction
        response = http_create_key_rotation_transaction_evm(sc_node_1,
                                                            key_type=0,
                                                            key_index=0,
                                                            new_key=new_public_key_4,
                                                            signing_key_signature=signing_signature_4,
                                                            master_key_signature=master_signature_4,
                                                            new_key_signature=new_key_signature_4)
        assert_false("error" in response)

        self.sc_sync_all()
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we4_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]
        sc_block_id = generate_next_block(sc_node_1, "first node")

        check_mcreferencedata_presence(we4_end_mcblock_hash, sc_block_id, sc_node_1)


        # Get and Verify Epoch 4 Key Root Hashes with new key
        epoch_four_keys_root_hashes = []
        for sc_node in self.sc_nodes:
            certificate_signers_keys = http_get_certifiers_keys(sc_node, epoch)
            assert_equal(certificate_signers_keys["certifiersKeys"]["signingKeys"][0]["publicKey"], new_public_key_4)
            epoch_four_keys_root_hash = certificate_signers_keys["keysRootHash"]
            epoch_four_keys_root_hashes.append(epoch_four_keys_root_hash)

        # ******************** WITHDRAWAL EPOCH 5 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 5 START ********************")
        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        # Check all nodes have the correct keys root hash
        for key_root_hash in epoch_four_keys_root_hashes:
            assert_equal(key_root_hash, cert['vFieldElementCertificateField'][0],
                         "Certificate Keys Root Hash incorrect")
        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we5_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node_1, "first node", 1)[0]
        check_mcreference_presence(we5_2_mcblock_hash, scblock_id, sc_node_1)

        # Get current Epoch
        epoch = get_withdrawal_epoch(sc_node_1)

        # Verify that we don't have any key rotation in this epoch
        for sc_node in self.sc_nodes:
            for i in range(self.cert_max_keys):
                signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 0)["result"]
                master_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 1)["result"]
                assert_equal(signer_key_rotation_proof, {})
                assert_equal(master_key_rotation_proof, {})

        n_private_signing_keys = all_signer_keys
        n_private_master_keys = all_master_keys
        for i in range(self.cert_max_keys):
            new_signing_key = new_signing_keys[i]
            new_signing_key_hash = \
                http_get_key_rotation_message_to_sign_for_signing_key(sc_node_1, new_signing_key.publicKey, epoch)[
                    "keyRotationMessageToSign"]

            new_m_key = new_master_keys[i]
            new_master_key_hash = \
                http_get_key_rotation_message_to_sign_for_master_key(sc_node_1, new_m_key.publicKey, epoch)[
                    "keyRotationMessageToSign"]

            if (i == 0):
                # Signing key signatures
                new_sign_master_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_hash,
                                                                                 key=new_master_key.secret)["signature"]
                new_sign_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_hash,
                                                                                  key=new_signing_key_4.secret)[
                    "signature"]

                # Master key signatures
                new_master_master_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_hash,
                                                                                   key=new_master_key.secret)[
                    "signature"]
                new_master_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_hash,
                                                                                    key=new_signing_key_4.secret)[
                    "signature"]

            else:
                # Signing key signatures
                new_sign_master_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_hash,
                                                                                 key=n_private_master_keys[i])[
                    "signature"]
                new_sign_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_hash,
                                                                                  key=n_private_signing_keys[i])[
                    "signature"]
                # Master key signatures
                new_master_master_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_hash,
                                                                                   key=n_private_master_keys[i])[
                    "signature"]
                new_master_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_hash,
                                                                                    key=n_private_signing_keys[i])[
                    "signature"]

            new_sign_key_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_hash,
                                                                          key=new_signing_key.secret)["signature"]
            new_master_key_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_hash,
                                                                            key=new_m_key.secret)["signature"]

            # Create the key rotation transaction to change the signing key
            response = http_create_key_rotation_transaction_evm(sc_node_1,
                                                                key_type=0,
                                                                key_index=i,
                                                                new_key=new_signing_key.publicKey,
                                                                signing_key_signature=new_sign_signing_signature,
                                                                master_key_signature=new_sign_master_signature,
                                                                new_key_signature=new_sign_key_signature)
            assert_false("error" in response)
            generate_next_blocks(sc_node_1, "first node", 1)

            # Create the key rotation transaction to change the master key
            response = http_create_key_rotation_transaction_evm(sc_node_1,
                                                                key_type=1,
                                                                key_index=i,
                                                                new_key=new_m_key.publicKey,
                                                                signing_key_signature=new_master_signing_signature,
                                                                master_key_signature=new_master_master_signature,
                                                                new_key_signature=new_master_key_signature)
            assert_false("error" in response)
            generate_next_blocks(sc_node_1, "first node", 1)

        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Verify that we changed all the signing keys
        for sc_node in self.sc_nodes:
            for i in range(self.cert_max_keys):
                signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 0)["result"]["keyRotationProof"]
                assert_equal(signer_key_rotation_proof["index"], i)
                assert_equal(signer_key_rotation_proof["keyType"]["value"], "SigningKeyRotationProofType")
                assert_equal(signer_key_rotation_proof["newKey"]["publicKey"], new_signing_keys[i].publicKey)

                master_key_rotation_proof = http_get_key_rotation_proof(sc_node, epoch, i, 1)["result"]["keyRotationProof"]
                assert_equal(master_key_rotation_proof["index"], i)
                assert_equal(master_key_rotation_proof["keyType"]["value"], "MasterKeyRotationProofType")
                assert_equal(master_key_rotation_proof["newKey"]["publicKey"], new_master_keys[i].publicKey)

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we5_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]

        sc_block_id = generate_next_block(sc_node_1, "first node")
        check_mcreferencedata_presence(we5_end_mcblock_hash, sc_block_id, sc_node_1)

        # Verify that we have all the singing keys updated
        certificate_signers_keys = http_get_certifiers_keys(sc_node_1, epoch)["certifiersKeys"]
        for i in range(len(certificate_signers_keys["signingKeys"])):
            assert_equal(certificate_signers_keys["signingKeys"][i]["publicKey"], new_signing_keys[i].publicKey)
            assert_equal(certificate_signers_keys["masterKeys"][i]["publicKey"], new_master_keys[i].publicKey)

        # Get Epoch 5 Key Root Hashes
        epoch_five_keys_root_hashes = []
        for sc_node in self.sc_nodes:
            certificate_signers_keys = http_get_certifiers_keys(sc_node, epoch)
            epoch_five_keys_root_hash = certificate_signers_keys["keysRootHash"]
            epoch_five_keys_root_hashes.append(epoch_five_keys_root_hash)

        # ******************** WITHDRAWAL EPOCH 6 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 6 START ********************")

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        epoch_mc_blocks_left = self.withdrawalEpochLength - 1
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        # Check all nodes have the correct keys root hash
        for key_root_hash in epoch_five_keys_root_hashes:
            assert_equal(key_root_hash, cert['vFieldElementCertificateField'][0],
                         "Certificate Keys Root Hash incorrect")
        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")

        # Generate MC and SC blocks with Cert
        we6_2_mcblock_hash = mc_node.generate(1)[0]

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node_1, "first node", 1)[0]
        check_mcreference_presence(we6_2_mcblock_hash, scblock_id, sc_node_1)
        # Generate enough MC blocks to reach the end of the withdrawal epoch
        mc_node.generate(epoch_mc_blocks_left - 1)
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # ******************** WITHDRAWAL EPOCH 7 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 7 START ********************")

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        generate_next_blocks(sc_node_1, "first node", 1)
        self.sc_sync_all()

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node_1.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node_1.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)['cert']
        # Check all nodes have the correct keys root hash
        for key_root_hash in epoch_five_keys_root_hashes:
            assert_equal(key_root_hash, cert['vFieldElementCertificateField'][0],
                         "Certificate Keys Root Hash incorrect")
        assert_equal(self.cert_max_keys, cert['quality'], "Certificate quality is wrong.")


if __name__ == "__main__":
    SCKeyRotationTestMultipleNodes().main()
