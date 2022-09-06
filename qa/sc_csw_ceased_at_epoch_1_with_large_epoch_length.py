#!/usr/bin/env python3
import logging
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_equal, assert_true, assert_false, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, if_csws_were_generated
from SidechainTestFramework.sc_forging_util import *
from decimal import Decimal

"""
Sidechain has ceased just after creation (no certificates appeared) - no active certificates.
Sidechain lifetime is full withdrawal epoch 0 + submission window of epoch 1

NOTE: Withdrawal epoch length is a large value to check that the bug with snark keys setup (segment size was not
specified explicitly) found in 0.3.4 has no impact on CSW generation/verification. 

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.
    SC node has DISABLED certificate submitter.
    CSW is enabled

Test:
    For the SC node:
        - Send 2 FTs to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FTs in the sidechain. Check FTs and save their box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Generate MC and SC blocks to reach the end of the Withdrawal epoch 0.
        
        - Send 2 more FTs to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FTs in the sidechain. Check FTs and save their box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Generate MC and SC blocks to reach the end of the submission window of epoch 1.
        
        - Check that sc has ceased.
        - Check the list of CSW on sc side. Should consists of 4 FTs and 0 UTXOs.
        - Create CSW proofs and send CSWs to MC. Check the results.
"""
class SCCswCeasedAtEpoch1WithLargeEpochLength(SidechainTestFramework):

    sidechain_id = None
    sc_withdrawal_epoch_length = LARGE_WITHDRAWAL_EPOCH_LENGTH

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=False,  # disable submitter
            cert_signing_enabled=False  # disable signer
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 1000, self.sc_withdrawal_epoch_length, csw_enabled=True), sc_node_configuration)
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

        # create 2 FTs to SC
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
        generate_next_blocks(sc_node, "first node", 1)

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
        generate_next_blocks(sc_node, "first node", 1)

        # Check new box (utxo) id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"]]}
        utxo_box_1 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Generate N more MC blocks to finish the first withdrawal epoch, then generate a few more SC blocks to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[-1]
        logging.info("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        logging.info("End cum sc tx cum comm tree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        generate_next_blocks(sc_node, "first node", max(1, int(epoch_mc_blocks_left / 10)))
        assert_equal(we0_end_mcblock_hash, sc_node.mainchain_bestBlockReferenceInfo()["result"]["blockReferenceInfo"]["hash"])

        # ******************** EPOCH 1 START ********************

        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length

        # Create new FT to SC
        sc_address_3 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_3 = 15
        mc_return_address_3 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_3, ft_amount_3, mc_return_address_3)

        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FT.
        generate_next_blocks(sc_node, "first node", 1)

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
        generate_next_blocks(sc_node, "first node", 1)

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
        generate_next_blocks(sc_node, "first node", 1)

        # Check new FT box id
        zen_boxes_req = {"boxTypeClass": "ZenBox", "excludeBoxIds": [ft_box_2["id"], utxo_box_1["id"], utxo_box_2["id"]]}
        ft_box_4 = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"][0]

        # Generate more MC and SC blocks to get out from the submission window and make sc ceased.
        mc_block_left_till_the_end_of_window = int(self.sc_withdrawal_epoch_length / 5) - self.sc_withdrawal_epoch_length + epoch_mc_blocks_left
        if mc_block_left_till_the_end_of_window > 0:
            # Generate block to get 1 block before the end of the window
            mc_node.generate(mc_block_left_till_the_end_of_window - 1)
            generate_next_blocks(sc_node, "first node", int(mc_block_left_till_the_end_of_window / 10))
            # Finish the window
            mc_node.generate(1)
            generate_next_block(sc_node, "first node")

        # Check sidechain status
        # From MC perspective SC has ceased, because it has reached the end of the submission window without any cert.
        sc_info = mc_node.getscinfo(self.sidechain_id)['items'][0]
        assert_equal("CEASED", sc_info['state'], "Sidechain expected to be ceased.")
        # Same for SC
        has_ceased = sc_node.csw_hasCeased()["result"]["state"]
        assert_true("Sidechain expected to be ceased.", has_ceased)

        # Check SC node owned boxes:
        # 2 FTs must be spent from wallet perspective
        closed_box_ids = [ft_box_2["id"], ft_box_4["id"], utxo_box_1["id"], utxo_box_2["id"]]
        opened_box_ids = [ft_box_1["id"], ft_box_3["id"]]
        zen_boxes_req = {"boxTypeClass": "ZenBox"}
        all_zen_boxes = sc_node.wallet_allBoxes(json.dumps(zen_boxes_req))["result"]["boxes"]

        for closed_box_id in closed_box_ids:
            assert_true(any(box["id"] == closed_box_id for box in all_zen_boxes),
                        "Closed box not found in the wallet: " + closed_box_id)

        for opened_box_id in opened_box_ids:
            assert_false(any(box["id"] == opened_box_id for box in all_zen_boxes),
                        "Opened box appeared in the wallet: " + opened_box_id)

        # Check CSW available boxes on SC node
        csw_boxes = [ft_box_1, ft_box_2, ft_box_3, ft_box_4]
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
            assert_equal("ForwardTransferCswData", csw_info["cswType"], "Type is different.")
            assert_equal(box["value"], csw_info["amount"], "Amount is different.")
            assert_equal(self.sidechain_id, csw_info["scId"], "Sidechain id is different.")
            assert_equal(nullifier, csw_info["nullifier"], "Nullifier is different.")
            assert_false("activeCertData" in csw_info, "ActiveCertData must not exist.")
            assert_equal(ceasing_cum_sc_tx_comm_tree, csw_info["ceasingCumScTxCommTree"], "CeasingCumScTxCommTree is different.")
            proof_info = csw_info["proofInfo"]
            assert_false("scProof" in proof_info, "scProof must not exist.")
            assert_false("receiverAddress" in proof_info, "receiverAddress must not exist.")
            assert_equal("Absent", proof_info["status"], "Proof must be absent.")

        # Generate CSW proofs for all FTs
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
            logging.info("Wait for CSW proofs creation completion...")
            time.sleep(10)
            attempts -= 1

        assert_true(if_csws_were_generated(sc_node, csw_box_ids, allow_absent=False), "Some CSW proof was not generated.")

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
                "activeCertData": "",
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

            logging.info("sent csw {} retrieving {} coins on MC node".format(finalRawtx, sc_csws[0]['amount']))

            logging.info("Check csw is in mempool...")
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
    SCCswCeasedAtEpoch1WithLargeEpochLength().main()
