#!/usr/bin/env python3
import time

import requests

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, generate_cert_signer_secrets
from SidechainTestFramework.secure_enclave_http_api_server import SecureEnclaveApiServer
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.submitter.getCertifiersKeys import http_get_certifiers_keys
from httpCalls.submitter.getKeyRotationMessageToSign import http_get_key_rotation_message_to_sign_for_master_key
from httpCalls.submitter.getKeyRotationMessageToSign import http_get_key_rotation_message_to_sign_for_signing_key
from httpCalls.submitter.getKeyRotationProof import http_get_key_rotation_proof
from httpCalls.transaction.createKeyRotationTransaction import http_create_key_rotation_transaction
from httpCalls.transaction.sendCoinsToAddress import sendCointsToMultipleAddress
from httpCalls.wallet.createPrivateKey25519 import http_wallet_createPrivateKey25519
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain

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
    - Negative test for CertificateKeyRotationTransaction.
    - Create a keyRotationTransaction and change the signer key 0 (SK0 -> SK1).
    - Try to create another keyRotationTransaction that change the same signer key and verify that is not accepted into the mempool due to the transaction incompatibility checker (SK0 -> SK2).
    - Call the getKeyRotationProof and verify that we have a keyRotationProof for the signer key 0 (SK1).
    - Create a keyRotationTransaction that change again the signer key 0 (SK0 -> SK3).
    - Call the getKeyRotationProof and verify that we have a keyRotationProof for the signer key 0 (SK3).
    - Create a keyRotationTransaction and change the master key 0 (MK0 -> MK1).
    - End the WE and verify that the certificates is added to the MC and SC.
    ######## WITHDRAWAL EPOCH 1 ##########
    - Call the getKeyRotationProof endpoint and verify that we don't have any key rotation proof stored for epoch 1.
    - Call the getCertificateSigners endpoint and verify that the signers key 0 = SK3 and master key 0 = MK1.
    - Create a keyRotationTransaction and change the signer key 0 (SK3 -> SK4).
    - End the WE and verify that the certificates is added to the MC and SC.
    ######## WITHDRAWAL EPOCH 2 ##########
    - Call the getKeyRotationProof endpoint and verify that we don't have any key rotation proof stored for epoch 2.
    - Call the getCertificateSigners endpoint and verify that the signers key 0 = SK4.
    - Update ALL the signing and master keys.
    - Call the getKeyRotationProof endpoint and verify that we have a KeyRotationProof for each signing and master keys.
    - End the WE and verify that the certificates is added to the MC and SC.
     ######## WITHDRAWAL EPOCH 3 ##########
    - Call the getCertificateSigners endpoint and verify that all the signing and master keys are updated.
     ######## WITHDRAWAL EPOCH 4 ##########
    - Verify that certificate was created using all the new keys
"""


def convertSecretToPrivateKey(secret):
    return secret[2:66]


class SCKeyRotationTest(SidechainTestFramework):
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
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            remote_keys_manager_enabled=True
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length,
                                                        cert_max_keys=self.cert_max_keys,
                                                        circuit_type=KEY_ROTATION_CIRCUIT,
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

        if (public_key != ""):
            post_data["publicKey"] = public_key
        elif (key != ""):
            post_data["privateKey"] = key
        else:
            raise Exception("Either public key or private key should be provided to call createSignature")

        response = requests.post("http://127.0.0.1:5000/api/v1/createSignature", json=post_data)
        jsonResponse = json.loads(response.text)
        return jsonResponse

    def get_certificate_info(self, block_hash):
        cert = self.nodes[0].getblock(block_hash, 2)["cert"][0]
        cert_custom_field = cert["cert"]["vFieldElementCertificateField"][0]
        cert_quality = cert["cert"]["quality"]
        return cert_custom_field, cert_quality

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1

        private_signing_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_signers_secrets
        private_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_masters_secrets
        public_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.public_master_keys
        public_signing_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.public_signing_keys

        new_signing_key = generate_cert_signer_secrets("random_seed", 1)[0]
        new_public_key = new_signing_key.publicKey
        new_signing_key_2 = generate_cert_signer_secrets("random_seed2", 1)[0]
        new_public_key_2 = new_signing_key_2.publicKey
        new_master_key = generate_cert_signer_secrets("random_seed3", 1)[0]
        new_public_key_3 = new_master_key.publicKey
        new_signing_key_4 = generate_cert_signer_secrets("random_seed4", 1)[0]
        new_public_key_4 = new_signing_key_4.publicKey

        private_master_keys.append(new_signing_key.secret)
        public_master_keys.append(new_public_key)
        private_master_keys.append(new_signing_key_2.secret)
        public_master_keys.append(new_public_key_2)
        private_master_keys.append(new_master_key.secret)
        public_master_keys.append(new_public_key_3)
        private_master_keys.append(new_signing_key_4.secret)
        public_master_keys.append(new_public_key_4)

        # Change ALL the signing keys and ALL tee master keys
        new_signing_keys = []
        new_master_keys = []
        for i in range(self.cert_max_keys):
            new_s_key = generate_cert_signer_secrets(f"random_seed5{i}", 1)[0]
            new_signing_keys += [new_s_key]
            private_master_keys.append(new_s_key.secret)
            public_master_keys.append(new_s_key.publicKey)

            new_m_key = generate_cert_signer_secrets(f"random_seed6{i}", 1)[0]
            new_master_keys += [new_m_key]
            private_master_keys.append(new_m_key.secret)
            public_master_keys.append(new_m_key.publicKey)

        api_server = SecureEnclaveApiServer(
            private_master_keys,
            public_master_keys,
        )
        api_server.start()

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

        # Split the FT in multiple boxes
        sendCointsToMultipleAddress(sc_node, [sc_address_1 for _ in range(20)], [1000 for _ in range(20)], 0)

        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Call getCertificateSigners endpoint
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
        signing_key_message = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key, epoch)["keyRotationMessageToSign"]
        # Sign the new signing key with the old keys
        master_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                public_key=public_master_keys[0])["signature"]
        signing_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                key=private_signing_keys[0])["signature"]
        new_key_signature = self.secure_enclave_create_signature(message_to_sign=signing_key_message,
                                                                key=new_signing_key.secret)["signature"]

        # NEGATIVE CASES

        # Pass wrong signer proof
        response = http_create_key_rotation_transaction(sc_node,
                                                        key_type=0,
                                                        key_index=0,
                                                        new_key=new_public_key,
                                                        signing_key_signature=master_signature,
                                                        master_key_signature=master_signature,
                                                        new_key_signature=new_key_signature,
                                                        format=True,
                                                        automatic_send=True)
        assert_true("error" in response)
        assert_true(
            "Signing key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

        # Pass wrong master proof
        response = http_create_key_rotation_transaction(sc_node,
                                                        key_type=0,
                                                        key_index=0,
                                                        new_key=new_public_key,
                                                        signing_key_signature=signing_signature,
                                                        master_key_signature=signing_signature,
                                                        new_key_signature=new_key_signature,
                                                        format=True,
                                                        automatic_send=True)
        assert_true("error" in response)
        assert_true(
            "Master key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

        # Pass wrong new key proof
        response = http_create_key_rotation_transaction(sc_node,
                                                        key_type=0,
                                                        key_index=0,
                                                        new_key=new_public_key,
                                                        signing_key_signature=signing_signature,
                                                        master_key_signature=master_signature,
                                                        new_key_signature=master_signature,
                                                        format=True,
                                                        automatic_send=True)
        assert_true("error" in response)
        assert_true(
            "New key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

        # Pass key_index out of range
        response = http_create_key_rotation_transaction(sc_node,
                                                        key_type=0,
                                                        key_index=100,
                                                        new_key=new_public_key,
                                                        signing_key_signature=signing_signature,
                                                        master_key_signature=master_signature,
                                                        new_key_signature=new_key_signature,
                                                        format=True,
                                                        automatic_send=True)
        assert_true("error" in response)
        assert_true("Key index in CertificateKeyRotationTransaction is out of range" in response["error"]["detail"])

        # Pass key_type out of range
        response = http_create_key_rotation_transaction(sc_node,
                                                        key_type=356,
                                                        key_index=0,
                                                        new_key=new_public_key,
                                                        signing_key_signature=signing_signature,
                                                        master_key_signature=master_signature,
                                                        new_key_signature=new_key_signature,
                                                        format=True,
                                                        automatic_send=True)
        assert_true("error" in response)
        assert_true("Key type enumeration value should be valid!" in response["error"]["detail"])

        # Pass wrong key_index
        response = http_create_key_rotation_transaction(sc_node,
                                                        key_type=0,
                                                        key_index=2,
                                                        new_key=new_public_key,
                                                        signing_key_signature=signing_signature,
                                                        master_key_signature=master_signature,
                                                        new_key_signature=new_key_signature,
                                                        format=True,
                                                        automatic_send=True)
        assert_true("error" in response)
        assert_true(
            "Signing key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

        # Pass wrong message_to_sign: try to change signing_key with master_key_message_two_sign
        wrong_signing_key_message = http_get_key_rotation_message_to_sign_for_master_key(
            sc_node, new_public_key, epoch)["keyRotationMessageToSign"]
        wrong_master_signature = self.secure_enclave_create_signature(message_to_sign=wrong_signing_key_message,
                                                                public_key=public_master_keys[0])["signature"]
        wrong_signing_signature = self.secure_enclave_create_signature(message_to_sign=wrong_signing_key_message,
                                                                 key=private_signing_keys[0])["signature"]
        wrong_new_key_signature = self.secure_enclave_create_signature(message_to_sign=wrong_signing_key_message,
                                                                 key=new_signing_key.secret)["signature"]
        response = http_create_key_rotation_transaction(sc_node,
                                            key_type=0,
                                            key_index=0,
                                            new_key=new_public_key,
                                            signing_key_signature=wrong_signing_signature,
                                            master_key_signature=wrong_master_signature,
                                            new_key_signature=wrong_new_key_signature,
                                            format=True,
                                            automatic_send=True)
        assert_true("error" in response)
        assert_true("Signing key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

        # Pass wrong message_to_sign: wrong withdrawal epoch number
        wrong_signing_key_message = http_get_key_rotation_message_to_sign_for_signing_key(
            sc_node, new_public_key, epoch + 1)["keyRotationMessageToSign"]
        wrong_master_signature = self.secure_enclave_create_signature(message_to_sign=wrong_signing_key_message,
                                                                      public_key=public_master_keys[0])["signature"]
        wrong_signing_signature = self.secure_enclave_create_signature(message_to_sign=wrong_signing_key_message,
                                                                       key=private_signing_keys[0])["signature"]
        wrong_new_key_signature = self.secure_enclave_create_signature(message_to_sign=wrong_signing_key_message,
                                                                       key=new_signing_key.secret)["signature"]
        response = http_create_key_rotation_transaction(sc_node,
                                                        key_type=0,
                                                        key_index=0,
                                                        new_key=new_public_key,
                                                        signing_key_signature=wrong_signing_signature,
                                                        master_key_signature=wrong_master_signature,
                                                        new_key_signature=wrong_new_key_signature,
                                                        format=True,
                                                        automatic_send=True)
        assert_true("error" in response)
        assert_true(
            "Signing key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

        # POSITIVE CASE

        # Change the signing key 0
        http_create_key_rotation_transaction(sc_node,
                                             key_type=0,
                                             key_index=0,
                                             new_key=new_public_key,
                                             signing_key_signature=signing_signature,
                                             master_key_signature=master_signature,
                                             new_key_signature=new_key_signature,
                                             format=True,
                                             automatic_send=True)["result"]["transactionId"]

        # Try to send another CertificateKeyRotationTransaction pointing to the same key
        error = False
        try:
            http_create_key_rotation_transaction(sc_node,
                                                 key_type=0,
                                                 key_index=0,
                                                 new_key=new_public_key,
                                                 signing_key_signature=signing_signature,
                                                 master_key_signature=master_signature,
                                                 new_key_signature=new_key_signature,
                                                 format=True,
                                                 automatic_send=True)["result"]["transactionId"]
        except:
            error = True
        assert_true(error)

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
        new_signing_key_message_2 = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key_2, epoch)["keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_2 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_2,
                                                                public_key=public_master_keys[0])["signature"]
        signing_signature_2 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_2,
                                                                key=private_signing_keys[0])["signature"]
        new_key_signature_2 = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message_2,
                                                                key=new_signing_key_2.secret)["signature"]

        # Try with old signatures
        error = False
        try:
            http_create_key_rotation_transaction(sc_node,
                                                 key_type=0,
                                                 key_index=0,
                                                 new_key=new_public_key_2,
                                                 signing_key_signature=signing_signature,
                                                 master_key_signature=master_signature,
                                                 new_key_signature=new_key_signature_2,
                                                 format=True,
                                                 automatic_send=True)["result"]["transactionId"]
        except:
            error = True
        assert_true(error)

        # Use the new signatures
        http_create_key_rotation_transaction(sc_node,
                                             key_type=0,
                                             key_index=0,
                                             new_key=new_public_key_2,
                                             signing_key_signature=signing_signature_2,
                                             master_key_signature=master_signature_2,
                                             new_key_signature=new_key_signature_2,
                                             format=True,
                                             automatic_send=True)["result"]["transactionId"]

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

        # Try to update the master key 0
        new_master_key = generate_cert_signer_secrets("random_seed3", 1)[0]
        new_public_key_3 = new_master_key.publicKey
        master_key_message_3 = http_get_key_rotation_message_to_sign_for_master_key(sc_node, new_public_key_3, epoch)["keyRotationMessageToSign"]


        # Sign the new signing key with the old keys
        master_signature_3 = self.secure_enclave_create_signature(message_to_sign=master_key_message_3,
                                                                public_key=public_master_keys[0])["signature"]
        signing_signature_3 = self.secure_enclave_create_signature(message_to_sign=master_key_message_3,
                                                                key=private_signing_keys[0])["signature"]
        new_key_signature_3 = self.secure_enclave_create_signature(message_to_sign=master_key_message_3,
                                                                key=new_master_key.secret)["signature"]

        # Change the master key 0
        http_create_key_rotation_transaction(sc_node,
                                             key_type=1,
                                             key_index=0,
                                             new_key=new_public_key_3,
                                             signing_key_signature=signing_signature_3,
                                             master_key_signature=master_signature_3,
                                             new_key_signature=new_key_signature_3,
                                             format=True,
                                             automatic_send=True)["result"]["transactionId"]
        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]

        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]

        certificate_signers_keys = http_get_certifiers_keys(sc_node, -1)["certifiersKeys"]
        for i in range(len(certificate_signers_keys["signingKeys"])):
            assert_equal(certificate_signers_keys["signingKeys"][i]["publicKey"], public_signing_keys[i])
            assert_equal(certificate_signers_keys["masterKeys"][i]["publicKey"], public_master_keys[i])

        sc_block_id = generate_next_block(sc_node, "first node")
        block_json = http_block_findById(sc_node, sc_block_id)
        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)

        certificate_signers_keys = http_get_certifiers_keys(sc_node, 0)["certifiersKeys"]
        for i in range(len(certificate_signers_keys["signingKeys"])):
            if i == 0:
                assert_equal(certificate_signers_keys["signingKeys"][0]["publicKey"], new_public_key_2)
                assert_equal(certificate_signers_keys["masterKeys"][0]["publicKey"], new_public_key_3)
            else:
                assert_equal(certificate_signers_keys["signingKeys"][i]["publicKey"], public_signing_keys[i])
                assert_equal(certificate_signers_keys["masterKeys"][i]["publicKey"], public_master_keys[i])

        # ******************** WITHDRAWAL EPOCH 1 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 1 START ********************")

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]

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

        # Verify that we don't have any key rotation in this epoch
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 1, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})

        # Verify that we have the updated key
        certificate_signers_keys = http_get_certifiers_keys(sc_node, 0)
        assert_equal(certificate_signers_keys["certifiersKeys"]["signingKeys"][0]["publicKey"], new_public_key_2)
        assert_equal(certificate_signers_keys["certifiersKeys"]["masterKeys"][0]["publicKey"], new_public_key_3)

        cert_custom_field_epoch_0, cert_quality_epoch_0 = self.get_certificate_info(we1_2_mcblock_hash)
        assert_equal(cert_quality_epoch_0, self.cert_max_keys)
        assert_equal(cert_custom_field_epoch_0, certificate_signers_keys["keysRootHash"])

        # Update again the signing key 0
        epoch = get_withdrawal_epoch(sc_node)
        new_signing_key_4 = generate_cert_signer_secrets("random_seed4", 1)[0]
        new_public_key_4 = new_signing_key_4.publicKey
        signing_key_message_4 = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_public_key_4, epoch)["keyRotationMessageToSign"]

        # Sign the new signing key with the old keys
        master_signature_4 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_4,
                                                                key=new_master_key.secret)["signature"]
        signing_signature_4 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_4,
                                                                key=new_signing_key_2.secret)["signature"]
        new_key_signature_4 = self.secure_enclave_create_signature(message_to_sign=signing_key_message_4,
                                                                key=new_signing_key_4.secret)["signature"] 

        # Create the key rotation transacion
        http_create_key_rotation_transaction(sc_node,
                                             key_type=0,
                                             key_index=0,
                                             new_key=new_public_key_4,
                                             signing_key_signature=signing_signature_4,
                                             master_key_signature=master_signature_4,
                                             new_key_signature=new_key_signature_4,
                                             format=True,
                                             automatic_send=True)["result"]["transactionId"]

        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]

        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]

        sc_block_id = generate_next_block(sc_node, "first node")
        block_json = http_block_findById(sc_node, sc_block_id)
        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)

        # ******************** WITHDRAWAL EPOCH 2 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 2 START ********************")

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]

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

        # Verify that we don't have any key rotation in this epoch
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 2, i, 0)["result"]
            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 2, i, 1)["result"]
            assert_equal(signer_key_rotation_proof, {})
            assert_equal(master_key_rotation_proof, {})

        # Verify that we have the updated key
        certificate_signers_keys = http_get_certifiers_keys(sc_node, 1)
        assert_equal(certificate_signers_keys["certifiersKeys"]["signingKeys"][0]["publicKey"], new_public_key_4)

        cert_custom_field_epoch_1, cert_quality_epoch_1 = self.get_certificate_info(we1_2_mcblock_hash)
        assert_equal(cert_quality_epoch_1, self.cert_max_keys)
        assert_equal(cert_custom_field_epoch_1, certificate_signers_keys["keysRootHash"])

        # Change ALL the signing keys and ALL tee master keys
        epoch = get_withdrawal_epoch(sc_node)
        new_signing_keys = []
        new_master_keys = []
        for i in range(self.cert_max_keys):
            new_signing_key = generate_cert_signer_secrets("random_seed5", 1)[0]
            new_signing_keys += [new_signing_key]
            new_signing_key_message = http_get_key_rotation_message_to_sign_for_signing_key(sc_node, new_signing_key.publicKey, epoch)["keyRotationMessageToSign"]

            new_m_key = generate_cert_signer_secrets("random_seed6", 1)[0]
            new_master_keys += [new_m_key]
            new_master_key_message = http_get_key_rotation_message_to_sign_for_master_key(sc_node, new_m_key.publicKey, epoch)["keyRotationMessageToSign"]

            if (i == 0):
                # Signing key signatures
                new_sign_master_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                    key=new_master_key.secret)["signature"]
                new_sign_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                    key=new_signing_key_4.secret)["signature"] 

                # Master key signatures
                new_master_master_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_message,
                                                                    key=new_master_key.secret)["signature"]
                new_master_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_message,
                                                                    key=new_signing_key_4.secret)["signature"]                                                                 

            else:
                # Signing key signatures
                new_sign_master_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                    key=private_master_keys[i])["signature"]
                new_sign_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                    key=private_signing_keys[i])["signature"]  
                # Master key signatures
                new_master_master_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_message,
                                                                    key=private_master_keys[i])["signature"]
                new_master_signing_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_message,
                                                                    key=private_signing_keys[i])["signature"] 

            new_sign_key_signature = self.secure_enclave_create_signature(message_to_sign=new_signing_key_message,
                                                                key=new_signing_key.secret)["signature"]
            new_master_key_signature = self.secure_enclave_create_signature(message_to_sign=new_master_key_message,
                                                                key=new_m_key.secret)["signature"]                                                     
            
            # Create the key rotation transacion to change the signing key
            http_create_key_rotation_transaction(sc_node,
                                                 key_type=0,
                                                 key_index=i,
                                                 new_key=new_signing_key.publicKey,
                                                 signing_key_signature=new_sign_signing_signature,
                                                 master_key_signature=new_sign_master_signature,
                                                 new_key_signature=new_sign_key_signature,
                                                 format=True,
                                                 automatic_send=True)["result"]["transactionId"]

            # Create the key rotation transacion to change the master key
            http_create_key_rotation_transaction(sc_node,
                                                 key_type=1,
                                                 key_index=i,
                                                 new_key=new_m_key.publicKey,
                                                 signing_key_signature=new_master_signing_signature,
                                                 master_key_signature=new_master_master_signature,
                                                 new_key_signature=new_master_key_signature,
                                                 format=True,
                                                 automatic_send=True)["result"]["transactionId"]

        generate_next_blocks(sc_node, "first node", 1)[0]

        # Verify that we changed all the signing keys
        for i in range(self.cert_max_keys):
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 2, i, 0)["result"]["keyRotationProof"]
            assert_equal(signer_key_rotation_proof["index"], i)
            assert_equal(signer_key_rotation_proof["keyType"]["value"], "SigningKeyRotationProofType")
            assert_equal(signer_key_rotation_proof["newKey"]["publicKey"], new_signing_keys[i].publicKey)

            master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 2, i, 1)["result"]["keyRotationProof"]
            assert_equal(master_key_rotation_proof["index"], i)
            assert_equal(master_key_rotation_proof["keyType"]["value"], "MasterKeyRotationProofType")
            assert_equal(master_key_rotation_proof["newKey"]["publicKey"], new_master_keys[i].publicKey)

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]

        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]

        sc_block_id = generate_next_block(sc_node, "first node")
        block_json = http_block_findById(sc_node, sc_block_id)
        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)

        # ******************** WITHDRAWAL EPOCH 3 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 3 START ********************")

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]

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

        # Verify that we have all the singing keys updated
        certificate_signers_keys = http_get_certifiers_keys(sc_node, 2)
        for i in range(len(certificate_signers_keys["certifiersKeys"]["signingKeys"])):
            assert_equal(certificate_signers_keys["certifiersKeys"]["signingKeys"][i]["publicKey"],
                         new_signing_keys[i].publicKey)
            assert_equal(certificate_signers_keys["certifiersKeys"]["masterKeys"][i]["publicKey"],
                         new_master_keys[i].publicKey)

        cert_custom_field_epoch_2, cert_quality_epoch_2 = self.get_certificate_info(we1_2_mcblock_hash)
        assert_equal(cert_quality_epoch_2, self.cert_max_keys)
        assert_equal(cert_custom_field_epoch_2, certificate_signers_keys["keysRootHash"])

        # Generate enough MC blocks to reach the end of the withdrawal epoch
        mc_node.generate(epoch_mc_blocks_left)
        generate_next_block(sc_node, "first node")

        # ******************** WITHDRAWAL EPOCH 4 START ********************
        logging.info("******************** WITHDRAWAL EPOCH 4 START ********************")

        # Generate first mc block of the next epoch
        mc_node.generate(1)
        generate_next_blocks(sc_node, "first node", 1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Generate MC and SC blocks with Cert
        we1_2_mcblock_hash = mc_node.generate(1)[0]

        # Generate SC block and verify that certificate is synced back
        scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id, sc_node)

        cert_custom_field_epoch_3, cert_quality_epoch_3 = self.get_certificate_info(we1_2_mcblock_hash)
        assert_equal(cert_quality_epoch_3, self.cert_max_keys)
        assert_equal(cert_custom_field_epoch_3, certificate_signers_keys["keysRootHash"])


if __name__ == "__main__":
    SCKeyRotationTest().main()
