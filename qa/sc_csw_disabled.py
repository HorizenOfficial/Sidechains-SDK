#!/usr/bin/env python3
import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, stop_sc_node, start_sc_node, \
    wait_for_sc_node_initialization
from test_framework.util import assert_equal, assert_true, assert_false, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, certificate_field_config_csw_disabled

CSW_DISABLED_ERROR_CODE = "0707"

"""
Tests sidechain with CSW disabled.
Sidechain works and submits certificates until epoch 2 and then ceases.
Sidechain has ceased in 3 epochs (certificates appeared in epochs 1 and 2) - active certificate in epoch 1 presents.
Sidechain lifetime is full withdrawal epochs 0,1,2 + submission window of epoch 3 

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.
    SC node has ENABLED certificate submitter.

Test:
    For the SC node:
        - Send 2 FTs to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FTs in the sidechain. Check FTs and save their box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Generate MC and SC blocks to reach the end of the Withdrawal epoch 0.
        
        - Wait for certificate submission.
        - Generate 1 MC and 1 SC block including the certificate.
        - Send 2 more FTs to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FTs in the sidechain. Check FTs and save their box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Generate MC and SC blocks to reach the end of the Withdrawal epoch 1.
        
        - Wait for certificate submission.
        - Generate 1 MC and 1 SC block including the certificate.
        - Send 2 more FTs to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FTs in the sidechain. Check FTs and save their box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Generate MC and SC blocks to reach the end of the Withdrawal epoch 2.
        - Disable certificate submitter.
        
        - Stop and restart the sidechain, for verifying that there are no issue when it restarts.
        
        - Send 2 FTs to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FTs in the sidechain. Check FTs and save their box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Generate MC and SC blocks to reach the end of the submission window of epoch 3.
        
        - Check that sc has ceased.
        - Tests the CSW Http API
        - Stop and restart the sidechain, for verifying that there are no issue when it restarts.
"""


class SCCswDisabled(SidechainTestFramework):

    sidechain_id = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=True,  # enable submitter
            cert_signing_enabled=True  # enable signer
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 1000, self.sc_withdrawal_epoch_length, csw_enabled=False), sc_node_configuration)
        self.sidechain_id = bootstrap_sidechain_nodes(self.options, network).sidechain_id

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Checks that in Sidechain creation transaction CSW is disabled
        mc_block_height = mc_node.getblockcount()
        mc_block = mc_node.getblock(str(mc_block_height))
        mc_sc_creation_tx_id = mc_block["tx"][1]
        mc_sc_creation_tx = mc_node.getrawtransaction(mc_sc_creation_tx_id, 1)

        vsc_ccout_ = mc_sc_creation_tx["vsc_ccout"][0]
        assert_true(vsc_ccout_["vFieldElementCertificateFieldConfig"] == certificate_field_config_csw_disabled,
                    "Custom Field Elements Configuration in MC are wrong. Expected: " + format(certificate_field_config_csw_disabled) +
                    ", actual: " + format(vsc_ccout_["vFieldElementCertificateFieldConfig"]))
        assert_false("wCeasedVk" in vsc_ccout_, "CSW verification key should not be present")

        # Checks that Sidechain creation transaction is in SC block
        mc_block_hash = mc_sc_creation_tx["blockhash"]
        sc_block_id = sc_node.block_best()["result"]["block"]["id"]
        check_mcreference_presence(mc_block_hash, sc_block_id, sc_node)


        # ******************** EPOCH 0 START ********************
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1

        # create 2 FTs in the same MC block to SC
        sc_address_1 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_1 = 10
        mc_return_address_1 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_1, ft_amount_1, mc_return_address_1, generate_block=False)

        # Sleep for 1 second to let MC synchronize wallet
        time.sleep(1)

        sc_address_2 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_2 = 20
        mc_return_address_2 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_2, ft_amount_2, mc_return_address_2)
        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check FTs and get their box ids
        zen_boxes_req = {"boxTypeClass": "ZenBox"}
        zen_boxes = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"]

        ft_box_1 = zen_boxes[0]
        ft_box_2 = zen_boxes[1]

        # Spend one FT, send it to ourselves.
        utxo_address_1 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        raw_tx_res = sc_node.transaction_createCoreTransaction(json.dumps({
            "transactionInputs": [{"boxId": ft_box_1["id"]}],
            "regularOutputs": [{"publicKey": utxo_address_1, "value": ft_box_1["value"]}],
            "withdrawalRequests": [],
            "forgerOutputs": []
        }))["result"]

        res = sc_node.transaction_sendTransaction(json.dumps(raw_tx_res))["result"]

        # Check mempool
        assert_equal(1, len(sc_node.transaction_allTransactions()["result"]["transactions"]),
                     "FT spending Tx expected to be in the SC node mempool.")

        # Generate 1 SC block to include Tx.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new box (utxo) id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"]]}
        utxo_box_1 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Generate 8 more MC blocks to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[-1]
        logging.info("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        logging.info("End cum sc tx cum comm tree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        sc_block_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)

        # ******************** EPOCH 1 START ********************

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, sc_block_id, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Generate MC and SC blocks with Cert
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, sc_block_id, sc_node)

        # Checks that in certificate there are no custom field elements
        mc_block_height = mc_node.getblockcount()
        mc_block = mc_node.getblock(str(mc_block_height))

        mc_certificate_tx = mc_node.getrawtransaction(mc_block["cert"][0], 1)
        assert_true(len(mc_certificate_tx["cert"]["vFieldElementCertificateField"]) == 0,
                    "Custom Field Elements list in certificate should be empty")

        # Checks that MC block with certificate is included in SC block
        sc_block = sc_node.block_best()["result"]["block"]
        mc_block_hash = mc_certificate_tx["blockhash"]
        sc_block_id = sc_block["id"]
        check_mcreference_presence(mc_block_hash, sc_block_id, sc_node)

        sc_certificate = sc_block["mainchainBlockReferencesData"][0]["topQualityCertificate"]
        assert_true(len(sc_certificate["fieldElementCertificateFields"]) == 0,
                    "Custom Field Elements list should be empty")

        assert_true(mc_block["cert"][0] == sc_certificate["hash"], "Certificate in SC should be the same that in MC")

        # create new FT to SC
        sc_address_3 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_3 = 15
        mc_return_address_3 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_3, ft_amount_3, mc_return_address_3)

        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new FT box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"]]}
        ft_box_3 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Spend new FT, send it to ourselves.
        utxo_address_2 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        raw_tx_res = sc_node.transaction_createCoreTransaction(json.dumps({
            "transactionInputs": [{"boxId": ft_box_3["id"]}],
            "regularOutputs": [{"publicKey": utxo_address_2, "value": ft_box_3["value"]}],
            "withdrawalRequests": [],
            "forgerOutputs": []
        }))["result"]

        res = sc_node.transaction_sendTransaction(json.dumps(raw_tx_res))["result"]

        # Generate 1 SC block to include Tx.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new UTXO box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"]]}
        utxo_box_2 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Send one more FT to Sidechain
        sc_address_4 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_4 = 17
        mc_return_address_4 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_4, ft_amount_4, mc_return_address_4)
        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT and reach the end of the submission window.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new FT box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], utxo_box_2["id"]]}
        ft_box_4 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Generate more MC blocks to finish the second withdrawal epoch, then generate 1 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[-1]
        logging.info("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        logging.info("End cum sc tx cum comm tree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)
        sc_block_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we1_end_mcblock_hash, sc_block_id, sc_node)

        # ******************** EPOCH 2 START ********************

        # Generate first mc block of the next epoch
        we2_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_1_mcblock_hash, sc_block_id, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Generate MC and SC blocks with Cert
        we2_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_2_mcblock_hash, sc_block_id, sc_node)

        # Create new FT to SC
        sc_address_5 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_5 = 22
        mc_return_address_5 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_5, ft_amount_5, mc_return_address_5)

        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new FT box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], ft_box_4["id"], utxo_box_2["id"]]}
        ft_box_5 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Spend new FT, send it to ourselves.
        utxo_address_3 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        raw_tx_res = sc_node.transaction_createCoreTransaction(json.dumps({
            "transactionInputs": [{"boxId": ft_box_5["id"]}],
            "regularOutputs": [{"publicKey": utxo_address_3, "value": ft_box_5["value"]}],
            "withdrawalRequests": [],
            "forgerOutputs": []
        }))["result"]

        res = sc_node.transaction_sendTransaction(json.dumps(raw_tx_res))["result"]

        # Generate 1 SC block to include Tx.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new UTXO box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], ft_box_4["id"], utxo_box_2["id"]]}
        utxo_box_3 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Send one more FT to Sidechain
        sc_address_6 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_6 = 27
        mc_return_address_6 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_6, ft_amount_6, mc_return_address_6)
        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT and reach the end of the submission window.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new FT box id
        zen_boxes_req = {"boxTypeClass": "ZenBox",
                         "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], ft_box_4["id"], utxo_box_2["id"], utxo_box_3["id"]]}
        ft_box_6 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Disable certificate submitter and signer to prevent certificate creation
        sc_node.submitter_disableCertificateSubmitter()
        sc_node.submitter_disableCertificateSigner()

        # Generate more MC blocks to finish the second withdrawal epoch, then generate 1 more SC block to sync with MC.
        we2_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[-1]
        logging.info("End mc block hash in withdrawal epoch 2 = " + we2_end_mcblock_hash)
        we2_end_mcblock_json = mc_node.getblock(we2_end_mcblock_hash)
        we2_end_epoch_cum_sc_tx_comm_tree_root = we2_end_mcblock_json["scCumTreeHash"]
        logging.info("End cum sc tx cum comm tree root hash in withdrawal epoch 2 = " + we2_end_epoch_cum_sc_tx_comm_tree_root)
        sc_block_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we2_end_mcblock_hash, sc_block_id, sc_node)

        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length

        # Stop and restart SC, in order to verify there are no failing checks during restart
        logging.info("***** Restarting sidechain *******************")
        logging.info("Stopping SC")
        stop_sc_node(sc_node, 0)
        time.sleep(5)

        logging.info("Starting SC")
        start_sc_node(0, self.options.tmpdir)
        wait_for_sc_node_initialization(self.sc_nodes)
        time.sleep(2)

        # ******************** EPOCH 3 START ********************

        # Create new FT to SC
        sc_address_7 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_7 = 7
        mc_return_address_7 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_7, ft_amount_7, mc_return_address_7)

        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new FT box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], ft_box_4["id"], utxo_box_2["id"], ft_box_6["id"], utxo_box_3["id"]]}
        ft_box_7 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Spend new FT, send it to ourselves.
        utxo_address_4 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        raw_tx_res = sc_node.transaction_createCoreTransaction(json.dumps({
            "transactionInputs": [{"boxId": ft_box_7["id"]}],
            "regularOutputs": [{"publicKey": utxo_address_4, "value": ft_box_7["value"]}],
            "withdrawalRequests": [],
            "forgerOutputs": []
        }))["result"]

        res = sc_node.transaction_sendTransaction(json.dumps(raw_tx_res))["result"]

        # Generate 1 SC block to include Tx.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Send one more FT to Sidechain
        sc_address_8 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_8 = 8
        mc_return_address_8 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_8, ft_amount_8, mc_return_address_8)
        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT and reach the end of the submission window.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check sidechain status
        # From MC perspective SC has ceased, because it has reached the end of the submission window without any cert.
        sc_info = mc_node.getscinfo(self.sidechain_id)['items'][0]
        assert_equal("CEASED", sc_info['state'], "Sidechain expected to be ceased.")
        # Same for SC
        has_ceased = sc_node.csw_hasCeased()["result"]["state"]
        assert_true(has_ceased, "Sidechain expected to be ceased.")

        # Check CSW is disabled
        is_csw_enabled = sc_node.csw_isCSWEnabled()["result"]["cswEnabled"]
        assert_false(is_csw_enabled, "Ceased Sidechain Withdrawal expected to be disabled.")

        # Check that CSW API are not accessible
        error_code = sc_node.csw_cswBoxIds()["error"]["code"]
        assert_equal(CSW_DISABLED_ERROR_CODE, error_code, "Expected CSW disabled error code for cswBoxIds()")

        error_code = sc_node.csw_cswInfo()["error"]["code"]
        assert_equal(CSW_DISABLED_ERROR_CODE, error_code, "Expected CSW disabled error code for cswInfo()")

        error_code = sc_node.csw_nullifier()["error"]["code"]
        assert_equal(CSW_DISABLED_ERROR_CODE, error_code, "Expected CSW disabled error code for nullifier()")

        error_code = sc_node.csw_generateCswProof()["error"]["code"]
        assert_equal(CSW_DISABLED_ERROR_CODE, error_code, "Expected CSW disabled error code for generateCswProof()")


if __name__ == "__main__":
    SCCswDisabled().main()
