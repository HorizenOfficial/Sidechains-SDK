#!/usr/bin/env python3
import time

from httpCalls.node.getCertificateSigners import http_get_certificate_signers
from httpCalls.node.getKeyRotationProof import http_get_key_rotation_proof
from httpCalls.node.signSchnorrPublicKey import http_sign_schnorr_publicKey

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, generate_cert_signer_secrets
from httpCalls.block.findBlockByID import http_block_findById
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

"""

def convertSecretToPrivateKey(secret):
    return secret[2:66]


class SCKeyRotationTest(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    cert_max_keys = 10
    cert_sig_threshold = 6
    circuit_type = KEY_ROTATION_CIRCUIT

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        # After bug spotted in 0.3.4 we test certificate generation with max keys number > 8
        
        mc_node = self.nodes[0]
        self.circuit_type = int(self.options.certcircuittype)
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            submitter_private_keys_indexes=list(range(self.cert_max_keys))  # SC node owns all schnorr private keys.
        )
        if (self.circuit_type == KEY_ROTATION_CIRCUIT):
            sc_creation_version = SC_CREATION_VERSION_2
        else:
            sc_creation_version = SC_CREATION_VERSION_1
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length,
                                                        cert_max_keys=self.cert_max_keys,
                                                        cert_sig_threshold=self.cert_sig_threshold,
                                                        circuit_type=self.circuit_type,
                                                        sc_creation_version=sc_creation_version,
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
        private_signing_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_signers_secrets
        private_master_keys = self.sc_nodes_bootstrap_info.certificate_proof_info.schnorr_masters_secrets


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
        sendCointsToMultipleAddress(sc_node, [sc_address_1 for _ in range (10)], [1000 for _ in range(10)], 0)

        self.sc_sync_all()
        generate_next_blocks(sc_node, "first node", 1)
        self.sc_sync_all()

        # Call getCertificateSigners endpoint
        certificate_signers_keys = http_get_certificate_signers(sc_node)["certifiersKeys"]
        assert_equal(len(certificate_signers_keys["signingKeys"]), self.cert_max_keys)
        if self.circuit_type == KEY_ROTATION_CIRCUIT:
            assert_equal(len(certificate_signers_keys["masterKeys"]), self.cert_max_keys)
        else:
            assert_equal(len(certificate_signers_keys["masterKeys"]), 0)

        # Call getKeyRotationProof endpoint and verify we don't have any KeyRotationProof
        if self.circuit_type == KEY_ROTATION_CIRCUIT:
            for i in range(self.cert_max_keys):
                signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 0)
                master_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, i, 1)
                assert_equal(signer_key_rotation_proof, {})
                assert_equal(master_key_rotation_proof, {})
        else:
            error = False
            try:
                http_get_key_rotation_proof(sc_node, 0, i, 0)
            except:
                error = True
            assert_true(error)

        # If the circuit doesn't support key rotation check that we don't support also the CertificateKeyRotationTransaction
        if (self.circuit_type == KEY_ROTATION_CIRCUIT):

            # Try to change the signing key 0
            old_signing_key = convertSecretToPrivateKey(private_signing_keys[0])
            old_master_key = convertSecretToPrivateKey(private_master_keys[0])
            new_signing_key = generate_cert_signer_secrets("random_seed", 1)[0]
            new_private_key = convertSecretToPrivateKey(new_signing_key.secret)
            new_public_key = new_signing_key.publicKey

            # Sign the new signing key with the old keys
            signing_signature = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key, key=old_signing_key)["signedMessage"]
            master_signature = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key, key=old_master_key)["signedMessage"]
            new_key_signature = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key, key=new_private_key)["signedMessage"]

            # NEGATIVE CASES

            # Pass wrong signer proof
            response = http_create_key_rotation_transaction(sc_node, 
                                                key_type=0,
                                                key_index=0,
                                                new_value_of_key=new_public_key,
                                                signing_key_signature=master_signature,
                                                master_key_signature=master_signature,
                                                new_key_signature=new_key_signature,
                                                format=True,
                                                automatic_send=True)
            assert_true("error" in response)
            assert_true("Signing key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

            # Pass wrong master proof
            response = http_create_key_rotation_transaction(sc_node, 
                                                key_type=0,
                                                key_index=0,
                                                new_value_of_key=new_public_key,
                                                signing_key_signature=signing_signature,
                                                master_key_signature=signing_signature,
                                                new_key_signature=new_key_signature,
                                                format=True,
                                                automatic_send=True)
            assert_true("error" in response)
            assert_true("Master key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

            # Pass wrong new key proof
            response = http_create_key_rotation_transaction(sc_node, 
                                                key_type=0,
                                                key_index=0,
                                                new_value_of_key=new_public_key,
                                                signing_key_signature=signing_signature,
                                                master_key_signature=master_signature,
                                                new_key_signature=master_signature,
                                                format=True,
                                                automatic_send=True)
            assert_true("error" in response)
            assert_true("New key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])    

            # Pass key_index out of range
            response = http_create_key_rotation_transaction(sc_node, 
                                                key_type=0,
                                                key_index=100,
                                                new_value_of_key=new_public_key,
                                                signing_key_signature=signing_signature,
                                                master_key_signature=master_signature,
                                                new_key_signature=new_key_signature,
                                                format=True,
                                                automatic_send=True)
            assert_true("error" in response)
            assert_true("Key index in CertificateKeyRotationTransaction is out of range" in response["error"]["detail"])         

            # Pass wrong key_index
            response = http_create_key_rotation_transaction(sc_node, 
                                                key_type=0,
                                                key_index=2,
                                                new_value_of_key=new_public_key,
                                                signing_key_signature=signing_signature,
                                                master_key_signature=master_signature,
                                                new_key_signature=new_key_signature,
                                                format=True,
                                                automatic_send=True)
            assert_true("error" in response)
            assert_true("Signing key signature in CertificateKeyRotationTransaction is not valid" in response["error"]["detail"])

            # Pass wrong key_type
            error = False
            try:
                response = http_create_key_rotation_transaction(sc_node, 
                                                    key_type=3,
                                                    key_index=0,
                                                    new_value_of_key=new_public_key,
                                                    signing_key_signature=signing_signature,
                                                    master_key_signature=master_signature,
                                                    new_key_signature=new_key_signature,
                                                    format=True,
                                                    automatic_send=True)
            except:
                error = True
            assert_true(error)

            # POSITIVE CASE

            # Change the signing key 0
            http_create_key_rotation_transaction(sc_node, 
                                                key_type=0,
                                                key_index=0,
                                                new_value_of_key=new_public_key,
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
                                                            new_value_of_key=new_public_key,
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
            signer_key_rotation_proof = http_get_key_rotation_proof(sc_node, 0, 0, 0)["keyRotationProof"]
            assert_equal(signer_key_rotation_proof["index"], 0)
            assert_equal(signer_key_rotation_proof["keyType"]["value"], "SigningKeyRotationProofType")
            assert_equal(signer_key_rotation_proof["masterKeySignature"]["signature"], master_signature)
            assert_equal(signer_key_rotation_proof["signingKeySignature"]["signature"], signing_signature)
            assert_equal(signer_key_rotation_proof["newValueOfKey"]["publicKey"], new_public_key)
  
            # Change again the same signature key
            new_signing_key_2 = generate_cert_signer_secrets("random_seed2", 1)[0]
            new_private_key_2 = convertSecretToPrivateKey(new_signing_key_2.secret)
            new_public_key_2 = new_signing_key_2.publicKey

            # Sign the new signing key with the old keys
            signing_signature_2= http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key_2, key=old_signing_key)["signedMessage"]
            master_signature_2 = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key_2, key=old_master_key)["signedMessage"]
            new_key_signature_2 = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key_2, key=new_private_key_2)["signedMessage"]

            # Try with old signatures
            error = False
            try:
                http_create_key_rotation_transaction(sc_node, 
                                    key_type=0,
                                    key_index=0,
                                    new_value_of_key=new_public_key_2,
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
                                                new_value_of_key=new_public_key_2,
                                                signing_key_signature=signing_signature_2,
                                                master_key_signature=master_signature_2,
                                                new_key_signature=new_key_signature_2,
                                                format=True,
                                                automatic_send=True)["result"]["transactionId"]

            self.sc_sync_all()
            generate_next_blocks(sc_node, "first node", 1)
            self.sc_sync_all()

            # Check that we have the keyRotationProof updated
            signer_key_rotation_proof_2 = http_get_key_rotation_proof(sc_node, 0, 0, 0)["keyRotationProof"]
            assert_equal(signer_key_rotation_proof_2["index"], 0)
            assert_equal(signer_key_rotation_proof_2["keyType"]["value"], "SigningKeyRotationProofType")
            assert_equal(signer_key_rotation_proof_2["masterKeySignature"]["signature"], master_signature_2)
            assert_equal(signer_key_rotation_proof_2["signingKeySignature"]["signature"], signing_signature_2)
            assert_equal(signer_key_rotation_proof_2["newValueOfKey"]["publicKey"], new_public_key_2)

            # Try to update the master key 0
            new_master_key = generate_cert_signer_secrets("random_seed3", 1)[0]
            new_private_key_3 = convertSecretToPrivateKey(new_master_key.secret)
            new_public_key_3 = new_master_key.publicKey

            # Sign the new signing key with the old keys
            signing_signature_3 = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key_3, key=old_signing_key)["signedMessage"]
            master_signature_3 = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key_3, key=old_master_key)["signedMessage"]
            new_key_signature_3 = http_sign_schnorr_publicKey(sc_node, message_to_sign=new_public_key_3, key=new_private_key_3)["signedMessage"]

            # Change the master key 0
            http_create_key_rotation_transaction(sc_node, 
                                                key_type=1,
                                                key_index=0,
                                                new_value_of_key=new_public_key_3,
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

            sc_block_id = generate_next_block(sc_node, "first node")
            block_json = http_block_findById(sc_node, sc_block_id)
            check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)  

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

        # Circuit with no key rotation
        else:
            response = http_create_key_rotation_transaction(sc_node, 
                            key_type=0,
                            key_index=0,
                            new_value_of_key="0",
                            signing_key_signature="0",
                            master_key_signature="0",
                            new_key_signature="0",
                            format=True,
                            automatic_send=True)

if __name__ == "__main__":
    SCKeyRotationTest().main()
