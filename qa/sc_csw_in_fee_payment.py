#!/usr/bin/env python3
from curses import raw
import time
import pprint
from decimal import Decimal

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, if_csws_were_generated
from test_framework.util import fail, assert_equal, assert_true, assert_false, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, certificate_field_config_csw_enabled
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.wallet.allBoxes import http_wallet_allBoxes
from httpCalls.block.getFeePayments import http_block_getFeePayments

"""
Sidechain has ceased in 3 epochs (certificates appeared in epochs 1 and 2) - active certificate in epoch 1 presents.
Sidechain lifetime is full withdrawal epochs 0,1,2 + submission window of epoch 3 

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.
    SC node has ENABLED certificate submitter.

Test:
    For the SC node:
        - ############## EPOCH 0 #####################
        - Send 1 FT to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FT in the sidechain. Check FT and save its box ids.
        - Spend 1 FT sending coins to the SC node 1. Remember the resulting box id.
        - Send another transaction from SC node 1 to itself.
        - Generate MC and SC blocks to reach the end of the Withdrawal epoch 0.
        - Verify that we have the FeePaymentBox inside the last block of the epoch and save its box id.
        
        - ############## EPOCH 1 #####################
        - Wait for certificate submission.
        - Generate 1 MC and 1 SC block including the certificate.
        - Generate MC and SC blocks to reach the end of the Withdrawal epoch 1.
        
        - ############## EPOCH 2 #####################
        - Wait for certificate submission.
        - Generate 1 MC and 1 SC block including the certificate.
        - Generate MC and SC blocks to reach the end of the Withdrawal epoch 2.
        - Disable certificate submitter.

        - ############## EPOCH 3 #####################
        - Generate MC and SC blocks to reach the end of the submission window of epoch 3.  
        - Check that sc has ceased.
        - Create the CSW proof for the FeePaymentBox of the epoch0 and send CSW to MC. Check the results.
"""
class ScCSWInFeePaymentTest(SidechainTestFramework):

    sidechain_id = None
    sc_withdrawal_epoch_length = 10
    FEE = 5

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
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 1000, self.sc_withdrawal_epoch_length, sc_creation_version=SC_CREATION_VERSION_1, csw_enabled=True), sc_node_configuration)
        self.sidechain_id = bootstrap_sidechain_nodes(self.options, network).sidechain_id

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Check CSW is enabled on SC node
        is_csw_enabled = sc_node.csw_isCSWEnabled()["result"]["cswEnabled"]
        assert_true(is_csw_enabled, "Ceased Sidechain Withdrawal expected to be enabled.")

        # Checks that CSW is enabled in Sidechain creation transaction on Mainchain
        mc_block_height = mc_node.getblockcount()
        mc_block = mc_node.getblock(str(mc_block_height))
        mc_sc_creation_tx_id = mc_block["tx"][1]
        mc_sc_creation_tx = mc_node.getrawtransaction(mc_sc_creation_tx_id, 1)

        vsc_ccout_ = mc_sc_creation_tx["vsc_ccout"][0]
        assert_true(vsc_ccout_["vFieldElementCertificateFieldConfig"] == certificate_field_config_csw_enabled,
                    "Custom Field Elements Configuration in MC are wrong. Expected: " + format(certificate_field_config_csw_enabled) +
                    ", actual: " + format(vsc_ccout_["vFieldElementCertificateFieldConfig"]))
        assert_true("wCeasedVk" in vsc_ccout_, "CSW verification key should be present")

        # Checks that Sidechain creation transaction is in SC block
        mc_block_hash = mc_sc_creation_tx["blockhash"]
        sc_block_id = sc_node.block_best()["result"]["block"]["id"]
        check_mcreference_presence(mc_block_hash, sc_block_id, sc_node)

        # ******************** EPOCH 0 START ********************
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1

        # create 1 FTs in the same MC block to SC
        sc_address_1 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        ft_amount_1 = 100
        mc_return_address_1 = mc_node.getnewaddress()

        forward_transfer_to_sidechain(self.sidechain_id, mc_node,
                                      sc_address_1, ft_amount_1, mc_return_address_1)

        # Sleep for 1 second to let MC synchronize wallet
        time.sleep(1)

        epoch_mc_blocks_left -= 1

        # Generate 1 SC block to include FTs.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Spend the FT, send it to ourselves.
        amount = 20
        sc_address_2 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sendCoinsToAddress(sc_node, sc_address_2, amount, self.FEE)

        # Generate 1 SC block to include Tx.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Create another transaciton to ourselves.
        sc_address_3 = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        amount = 30
        sendCoinsToAddress(sc_node, sc_address_3, amount, self.FEE)

        # Generate 1 SC block to include Tx.
        generate_next_blocks(sc_node, "first node", 1)[0]

        # Generate 8 more MC blocks to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[-1]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        sc_block_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)

        # Verify that we have FeePayment Box in the last block and in the wallet
        block = http_block_findById(sc_node, sc_block_id)
        assert_false(block["block"]["header"]["feePaymentsHash"] == "0000000000000000000000000000000000000000000000000000000000000000")

        fee_payments = http_block_getFeePayments(sc_node, sc_block_id)
        assert_equal(len(fee_payments["feePayments"]), 1)
        fee_payment_box_id = fee_payments["feePayments"][0]["id"]

        all_boxes = http_wallet_allBoxes(sc_node)
        found = False
        for box in all_boxes:
            if (box["id"] == fee_payment_box_id):
                found = True
        assert_true(found)

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


        # Generate more MC blocks to finish the second withdrawal epoch, then generate 1 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[-1]
        print("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)
        sc_block_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we1_end_mcblock_hash, sc_block_id, sc_node)

        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length

        # ******************** EPOCH 2 START ********************

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

        # Generate 2 more MC blocks to let the sidechain ceases
        mc_node.generate(2)
        # Generate 1 SC block to include FT.
        generate_next_blocks(sc_node, "first node", 1)[0]

        time.sleep(3)

        # Check sidechain status
        # From MC perspective SC has ceased, because it has reached the end of the submission window without any cert.
        sc_info = mc_node.getscinfo(self.sidechain_id)['items'][0]
        assert_equal("CEASED", sc_info['state'], "Sidechain expected to be ceased.")
        # Same for SC
        has_ceased = sc_node.csw_hasCeased()["result"]["state"]
        assert_true(has_ceased, "Sidechain expected to be ceased.")

        # Check CSW is enabled on SC node
        is_csw_enabled = sc_node.csw_isCSWEnabled()["result"]["cswEnabled"]
        assert_true(is_csw_enabled, "Ceased Sidechain Withdrawal expected to be enabled.")

        # Check CSW available boxes on SC node and verify there is also the FeePayment Box
        actual_csw_box_ids = sc_node.csw_cswBoxIds()["result"]["cswBoxIds"]
        assert_true(fee_payment_box_id in actual_csw_box_ids)

        # Generate CSW proofs the FeePaymentBox
        receiver_address = mc_node.getnewaddress()
        req = json.dumps({"boxId": fee_payment_box_id, "receiverAddress": receiver_address})
        state = sc_node.csw_generateCswProof(req)["result"]["state"]
        assert_equal("ProofGenerationStarted", state, "Different proof generation state found")

        # Wait for proofs generation completion.
        attempts = 200
        while not if_csws_were_generated(sc_node, [fee_payment_box_id], allow_absent=True) and attempts > 0:
            print("Wait for CSW proofs creation completion...")
            time.sleep(10)
            attempts -= 1

        assert_true(if_csws_were_generated(sc_node, [fee_payment_box_id], allow_absent=False),
                    "Some CSW proof was not generated.")

        # Send CSWs to MC
        mc_out_addr = mc_node.getnewaddress()

        req = json.dumps({"boxId": fee_payment_box_id})
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
        sigRawtx = mc_node.signrawtransaction(rawtx, None, None, "NONE")
        finalRawtx = mc_node.sendrawtransaction(sigRawtx['hex'])

        print("sent csw 1 {} retrieving {} coins on MC node".format(finalRawtx, sc_csws[0]['amount']))

        print("Check csw is in mempool...")
        assert_true(finalRawtx in mc_node.getrawmempool())

        # Generate mc block to include CSWs into the blockchain
        mc_node.generate(1)

        # Check CSW nullifiers presence in MC
        nullifier = sc_node.csw_nullifier(req)["result"]["nullifier"]
        is_present = mc_node.checkcswnullifier(self.sidechain_id, nullifier)["data"]
        assert_equal("true", is_present, "Nullifier for box id = " + fee_payment_box_id + " must be present in the MC.")    

if __name__ == "__main__":
    ScCSWInFeePaymentTest().main()
