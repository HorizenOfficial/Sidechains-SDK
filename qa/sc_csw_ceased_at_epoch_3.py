#!/usr/bin/env python3
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_equal, assert_true, assert_false, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, if_csws_were_generated
from SidechainTestFramework.sc_forging_util import *
from decimal import Decimal

"""
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
        
        - Send 2 FTs to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FTs in the sidechain. Check FTs and save their box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Generate MC and SC blocks to reach the end of the submission window of epoch 3.
        
        - Check that sc has ceased.
        - Check the list of CSW on sc side. Active cert is present. Should consists of 6 FTs and 3 UTXOs:
         * 6 FTs for created during epochs 1, 2 and 3.
         * 3 UTXOs: ScCreation ForgerBox, FT_2 (epoch 0), UTXO_1 (epoch 0).
        - Create CSW proofs and send CSWs to MC. Check the results.
"""
class SCCswCeasedAtEpoch3(SidechainTestFramework):

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
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 1000, self.sc_withdrawal_epoch_length), sc_node_configuration)
        self.sidechain_id = bootstrap_sidechain_nodes(self.options, network).sidechain_id

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

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
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
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
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Generate MC and SC blocks with Cert
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, sc_block_id, sc_node)

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
        print("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)
        sc_block_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we1_end_mcblock_hash, sc_block_id, sc_node)

        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length

        # ******************** EPOCH 1 START ********************

        # Generate first mc block of the next epoch
        we2_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_1_mcblock_hash, sc_block_id, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
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
        print("End mc block hash in withdrawal epoch 2 = " + we2_end_mcblock_hash)
        we2_end_mcblock_json = mc_node.getblock(we2_end_mcblock_hash)
        we2_end_epoch_cum_sc_tx_comm_tree_root = we2_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 2 = " + we2_end_epoch_cum_sc_tx_comm_tree_root)
        sc_block_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we2_end_mcblock_hash, sc_block_id, sc_node)

        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length

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

        # Check new UTXO box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], ft_box_4["id"], utxo_box_2["id"], ft_box_6["id"], utxo_box_3["id"]]}
        utxo_box_4 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Send one more FT to Sidechain
        sc_address_8 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_8 = 8
        mc_return_address_8 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_8, ft_amount_8, mc_return_address_8)
        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT and reach the end of the submission window.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Check new FT box id
        zen_boxes_req = {"boxTypeClass": "ZenBox",
                         "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], ft_box_4["id"], utxo_box_2["id"], ft_box_6["id"], utxo_box_3["id"], utxo_box_4["id"]]}
        ft_box_8 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Check sidechain status
        # From MC perspective SC has ceased, because it has reached the end of the submission window without any cert.
        sc_info = mc_node.getscinfo(self.sidechain_id)['items'][0]
        assert_equal("CEASED", sc_info['state'], "Sidechain expected to be ceased.")
        # Same for SC
        has_ceased = sc_node.csw_hasCeased()["result"]["state"]
        assert_true("Sidechain expected to be ceased.", has_ceased)

        # Check SC node owned boxes:
        forger_boxes_req = {"boxTypeClass": "ForgerBox"}
        sc_cr_utxo = sc_node.wallet_allBoxes(json.dumps(forger_boxes_req))["result"]["boxes"][0]

        # 4 FTs must be spent from wallet perspective
        closed_box_ids = [ft_box_2["id"], ft_box_4["id"], ft_box_6["id"], ft_box_8["id"], utxo_box_1["id"], utxo_box_2["id"], utxo_box_3["id"], utxo_box_4["id"]]
        opened_box_ids = [ft_box_1["id"], ft_box_3["id"], ft_box_5["id"], ft_box_7["id"]]
        zen_boxes_req = {"boxTypeClass": "ZenBox"}
        all_zen_boxes = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"]


        for closed_box_id in closed_box_ids:
            assert_true(any(box["id"] == closed_box_id for box in all_zen_boxes),
                        "Closed box not found in the wallet: " + closed_box_id)

        for opened_box_id in opened_box_ids:
            assert_false(any(box["id"] == opened_box_id for box in all_zen_boxes),
                        "Opened box appeared in the wallet: " + opened_box_id)

        # Check CSW available boxes on SC node
        utxo_csw_boxes = [sc_cr_utxo, utxo_box_1, ft_box_2]
        ft_csw_boxes = [ft_box_3, ft_box_4, ft_box_5, ft_box_6, ft_box_7, ft_box_8]
        csw_boxes = utxo_csw_boxes + ft_csw_boxes
        actual_csw_box_ids = sc_node.csw_cswBoxIds()["result"]["cswBoxIds"]
        assert_equal(len(csw_boxes), len(actual_csw_box_ids), "Different CSW box ids found.")

        for box in csw_boxes:
            assert_true(box["id"] in actual_csw_box_ids, "CSW box id not found: " + box["id"])

        ceasing_cum_sc_tx_comm_tree = mc_node.getceasingcumsccommtreehash(self.sidechain_id)['ceasingCumScTxCommTree']

        for box in csw_boxes:
            # Check CSW nullifiers presence in MC
            req = json.dumps({"boxId": box["id"]})
            nullifier = sc_node.csw_nullifier(req)["result"]["nullifier"]
            is_present = mc_node.checkcswnullifier(self.sidechain_id, nullifier)["data"]
            assert_equal("false", is_present, "Nullifier must not be present in the MC.")

            # Check CSW info
            csw_info = sc_node.csw_cswInfo(req)["result"]["cswInfo"]
            if box in utxo_csw_boxes:
                assert_equal("UtxoCswData", csw_info["cswType"], "Type is different.")
            elif box in ft_csw_boxes:
                assert_equal("ForwardTransferCswData", csw_info["cswType"], "Type is different.")
            else:
                fail("Error in test flow.")
            assert_equal(box["value"], csw_info["amount"], "Amount is different.")
            assert_equal(self.sidechain_id, csw_info["scId"], "Sidechain id is different.")
            assert_equal(nullifier, csw_info["nullifier"], "Nullifier is different.")
            assert_true("activeCertData" in csw_info, "ActiveCertData must exist.")
            assert_equal(ceasing_cum_sc_tx_comm_tree, csw_info["ceasingCumScTxCommTree"], "CeasingCumScTxCommTree is different.")
            proof_info = csw_info["proofInfo"]
            assert_false("scProof" in proof_info, "scProof must not exist.")
            assert_false("receiverAddress" in proof_info, "receiverAddress must not exist.")
            assert_equal("Absent", proof_info["status"], "Proof must be absent.")

        # Generate 3 more MC blocks to make SC coins mature
        mc_node.generate(3)

        # Generate CSW proofs for all FTs and UTXOs
        csw_box_ids = list(map(lambda box: box["id"], csw_boxes))
        assert_true(len(csw_box_ids) > 0, "csw box ids expected to be defined.")
        for box_id in csw_box_ids:
            receiver_address = mc_node.getnewaddress()
            req = json.dumps({"boxId": box_id, "receiverAddress": receiver_address})
            state = sc_node.csw_generateCswProof(req)["result"]["state"]
            assert_equal("ProofGenerationStarted", state, "Different proof generation state found")

        # Wait for proofs generation completion.
        attempts = 200
        while not if_csws_were_generated(sc_node, csw_box_ids, allow_absent=True) and attempts > 0:
            print("Wait for CSW proofs creation completion...")
            time.sleep(10)
            attempts -= 1

        assert_true(if_csws_were_generated(sc_node, csw_box_ids, allow_absent=False),
                    "Some CSW proof was not generated.")

        # Send CSWs to MC
        mc_out_addr = mc_node.getnewaddress()
        csw_txs = 0

        for box_id in csw_box_ids:
            req = json.dumps({"boxId": box_id})
            csw_info = sc_node.csw_cswInfo(req)["result"]["cswInfo"]

            csw_amount = Decimal(csw_info["amount"]) / 100000000
            sc_csws = [{
                "amount": str(csw_amount),
                "senderAddress": csw_info["proofInfo"]["receiverAddress"],
                "scId": csw_info["scId"],
                "nullifier": csw_info["nullifier"],
                "activeCertData": csw_info["activeCertData"],
                "ceasingCumScTxCommTree": csw_info["ceasingCumScTxCommTree"],
                "scProof": csw_info["proofInfo"]["scProof"]
            }]

            # Recipient MC address
            sc_csw_tx_outs = {mc_out_addr: str(csw_amount)}

            # Sleep for 1 second to let MC synchronize wallet
            time.sleep(1)
            rawtx = mc_node.createrawtransaction([], sc_csw_tx_outs, sc_csws)
            funded_tx = mc_node.fundrawtransaction(rawtx)
            sigRawtx = mc_node.signrawtransaction(funded_tx['hex'], None, None, "NONE")
            finalRawtx = mc_node.sendrawtransaction(sigRawtx['hex'])

            print("sent csw 1 {} retrieving {} coins on MC node".format(finalRawtx, sc_csws[0]['amount']))

            print("Check csw is in mempool...")
            assert_true(finalRawtx in mc_node.getrawmempool())

            # MC has a limit per number of CSW in the mempool in regtest: ScMaxNumberOfCswInputsInMempool = 5
            csw_txs += 1
            if csw_txs % 5 == 0:
                mc_node.generate(1)

        # Generate mc block to include CSWs into the blockchain
        mc_node.generate(1)

        # Check CSW nullifiers presence in MC
        for box_id in csw_box_ids:
            req = json.dumps({"boxId": box_id})
            nullifier = sc_node.csw_nullifier(req)["result"]["nullifier"]
            is_present = mc_node.checkcswnullifier(self.sidechain_id, nullifier)["data"]
            assert_equal("true", is_present, "Nullifier for box id = " + box_id + " must be present in the MC.")


if __name__ == "__main__":
    SCCswCeasedAtEpoch3().main()
