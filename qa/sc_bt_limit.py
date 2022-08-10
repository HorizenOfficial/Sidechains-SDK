#!/usr/bin/env python3
from curses import raw
import time
import pprint

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_1
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block
from test_framework.util import fail, assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain, certificate_field_config_csw_enabled
from httpCalls.block.findBlockByID import http_block_findById
from httpCalls.transaction.withdrawCoins import withdrawMultiCoins
from httpCalls.block.forgingInfo import http_block_forging_info

"""
Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.
    SC node has ENABLED certificate submitter.
    WithdrawalEpochLength = 11
    WithdrawalRequestBox slots open per MC block reference = 3999 / (11 - 1) = 399

Test:
    For the SC node:
        - ############## EPOCH 0 #####################
        - Send 1 FT to SC node 1 on different addresses.
        - Generate 1 MC and 1 SC block to see FT in the sidechain.
        - Generate a transaction that has 999 WithdrawalRequestBoxes
        - Generate a SC block and verify that it includes this transaction since we didn't reach the SC fork 1

        - ############## EPOCH 1 #####################
        - Reach the SC fork 1
        - Generate a transaction that has 999 WithdrawalRequestBoxes
        - Generate a SC block and verify that it doesn't include this transaction (2 MC block reference included = 798 WBs slots)
        - Generate a new MC block to open other 399 slots
        - Generate a SC block that includes this transction (Slots left = 3*399 - 999 = 198)
        - Generate a transaction that creates 98 WBs
        - Generate a SC block that includes this transaction (verify that the slots take in account the already mined WBs) (Slots left = 100)
        - Generate 2 transaction that create 80 WBs each one
        - Generate a SC block and verify that it contains only one of these 2 transctions
        - Generate 2 transactions that generate 999 WBs each one
        - Generate MC blocks until the last block of the epoch 0.
        - Generate a SC block that inlcudes all the previous transactions (we opened up enought slots)
        - Create a transaction that generate the reamining WBs allowed in the epoch (3999 - alreadyMinedWbs = 824 WBs)
        - Generate another MC block and SC block to reach the end of the epoch

        - ############## EPOCH 2 #####################
        - Generate the first MC block of the next epoch and a SC block
        - Verify that this SC block contains the previously generated transaction containing the remaining WBs (824)
        - Wait for certificate submission.
        - Generate 1 MC and 1 SC block including the certificate.
        - Generate a transaction that creates 300 WBs
        - Forge a SC block and verify that it doesn't contain this transaction. We already filled remaining_wbs (824) in this epoch that only added 2 MC block references (=798 empty slots)
        - Generate MC blocks to reach the end of the Withdrawal epoch 1.
        - Generate a SC block that contains all of these MC blocks.
        
        - ############## EPOCH 3 #####################
        - Generate the first MC block of the next epoch and a SC block
        - Verify that we have the 300 WBs transaction included in this SC block. We couldn't add before because we opened all the needed slots in the last block of the epoch that can't include any transactions
        - Wait for certificate submission.

"""
class ScBtLimitTest(SidechainTestFramework):

    sidechain_id = None
    sc_withdrawal_epoch_length = 11
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
        self.sidechain_id = bootstrap_sidechain_nodes(self.options, network, 720*120*5).sidechain_id

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        MAX_WBS_PER_EPOCH = 3999

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
        print("******************** EPOCH 0 START ********************")

        #Verify we didn't reach the SC fork1 that includes BT limit
        consensusEpochData = http_block_forging_info(sc_node)
        assert_equal(consensusEpochData["bestEpochNumber"], 1)
        
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

        # Create a transaction that generates 999 WBs 
        bt_address = mc_node.getnewaddress()
        bt_addresses = [bt_address for i in range(999)]
        amounts = [54 for i in range(999)]
        withdrawMultiCoins(sc_node, bt_addresses, amounts)

        # Try to Generate 1 SC block.
        # Since we are in the consensus epoch 1 we still didn't activate the Fork1 that includes BT limitation
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        block_json = http_block_findById(sc_node, sc_block_id)
        assert_equal(len(block_json["block"]["sidechainTransactions"]), 1)

        # Generate MC blocks to reach the end of the epoch
        we0_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[0]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)

        sc_block_id = generate_next_block(sc_node, "first node")
        block_json = http_block_findById(sc_node, sc_block_id)
        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)

        # ******************** EPOCH 1 START ********************
        print("******************** EPOCH 1 START ********************")

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

        # Reach the SC fork 1
        generate_next_block(sc_node, "first node", force_switch_to_next_epoch = True)
        consensusEpochData = http_block_forging_info(sc_node)
        print(consensusEpochData)
        assert_equal(consensusEpochData["bestEpochNumber"], 3)

        # Based on withdrawalEpochLength = 10, maxBTsAllowedPerCertificate = 3999, we open 399 slots for WithdrawalBoxes for each MC block reference
        # 2 MC block mined = 399 * 2 = 798 WthdrawalBoxes allowed

        # Create a transaction that generates 999 WBs 
        bt_address = mc_node.getnewaddress()
        bt_addresses = [bt_address for i in range(999)]
        amounts = [54 for i in range(999)]
        withdrawMultiCoins(sc_node, bt_addresses, amounts)

        # Try to Generate 1 SC block.
        # Since we have only 798 slots opened, the forger should not include this transaction in the next block
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        block_json = http_block_findById(sc_node, sc_block_id)
        assert_equal(block_json["block"]["sidechainTransactions"], [])

        #Open another 399 slots buy mining a new MC block
        mc_node.generate(1)[0]
        epoch_mc_blocks_left -= 1
        # Try to Generate 1 SC block to include Tx.
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        block_json = http_block_findById(sc_node, sc_block_id)
        assert_equal(len(block_json["block"]["sidechainTransactions"]), 1)
        wbs_mined = 999

        # Slots left = 3*399 - 999 = 198
        # Create a transaction that generates 98 WBs.
        withdrawMultiCoins(sc_node, bt_addresses[:98], amounts[:98])
        # Try to Generate 1 SC block to include Tx.
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        block_json = http_block_findById(sc_node, sc_block_id)
        assert_equal(len(block_json["block"]["sidechainTransactions"]), 1)
        wbs_mined += 98

        # Slot left = 100
        # Create a transaction that generates 80 WBs
        withdrawMultiCoins(sc_node, bt_addresses[:80], amounts[:80])
        # Create a transaction that generates 80 WBs
        withdrawMultiCoins(sc_node, bt_addresses[:80], amounts[:80])

        # Generates 1 SC block and verify that it contains only 1 of the two transactions
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        block_json = http_block_findById(sc_node, sc_block_id)
        assert_equal(len(block_json["block"]["sidechainTransactions"]), 1)    
        wbs_mined += 80

        # Create Some WBs

        withdrawMultiCoins(sc_node, bt_addresses, amounts)
        wbs_mined += 999
        withdrawMultiCoins(sc_node, bt_addresses, amounts)
        wbs_mined += 999

        # Generate some MC blocks to open up slots
        mc_node.generate(epoch_mc_blocks_left - 1)
        sc_block_id = generate_next_block(sc_node, "first node")
        block_json = http_block_findById(sc_node, sc_block_id)

        # Generate 1 MC block to reach the end of the epoch
        we0_end_mcblock_hash = mc_node.generate(1)[0]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)

        #824
        remaining_wbs = MAX_WBS_PER_EPOCH - wbs_mined
        wbs_left_txid = withdrawMultiCoins(sc_node, bt_addresses[:remaining_wbs], amounts[:remaining_wbs])["result"]["transactionId"]

        sc_block_id = generate_next_block(sc_node, "first node")
        block_json = http_block_findById(sc_node, sc_block_id)
        check_mcreferencedata_presence(we0_end_mcblock_hash, sc_block_id, sc_node)


        # ******************** EPOCH 2 START ********************
        print("******************** EPOCH 2 START ********************")

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]

        # The tx with the reamining wbs is not added to the last epoch block but it should be added to the first block of the new epoch
        # Since we are not started to generate the certificate for the epoch 0 we still have the open slots from the previous epoch and 
        # we are able to include the tx in the block
        assert_equal(wbs_left_txid, http_block_findById(sc_node, sc_block_id)["block"]["sidechainTransactions"][0]["id"])

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

        # Create 300 BTs
        wbs_txid = withdrawMultiCoins(sc_node, bt_addresses[:300], amounts[:300])["result"]["transactionId"]

        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, sc_block_id, sc_node)

        # We already filled remaining_wbs (824) in this epoch that only added 2 MC block references (=798 empty slots)
        # this new 300 wbs transactions should not be incuded in the next block
        assert_equal(len(http_block_findById(sc_node, sc_block_id)["block"]["sidechainTransactions"]), 0)

        
        # Generate more MC blocks to finish the second withdrawal epoch, then generate 1 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(epoch_mc_blocks_left)[-1]
        print("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx cum comm tree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)

        sc_block_id = generate_next_block(sc_node, "first node")

        # We don't include the tx here since this is the last block of the epoch
        assert_equal(len(http_block_findById(sc_node, sc_block_id)["block"]["sidechainTransactions"]), 0)

        check_mcreferencedata_presence(we1_end_mcblock_hash, sc_block_id, sc_node)

        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length

        # ******************** EPOCH 3 START ********************
        print("******************** EPOCH 3 START ********************")

        # Generate first mc block of the next epoch
        we2_1_mcblock_hash = mc_node.generate(1)[0]
        epoch_mc_blocks_left = self.sc_withdrawal_epoch_length - 1
        sc_block_id = generate_next_blocks(sc_node, "first node", 1)[0]

        # Here we have the accumulated slots from the previous epoch since we didn't generate the certificate yet.
        assert_equal(wbs_txid, http_block_findById(sc_node, sc_block_id)["block"]["sidechainTransactions"][0]["id"])

        check_mcreference_presence(we2_1_mcblock_hash, sc_block_id, sc_node)

        # This is for an actual bug on ZEND that selectes to many inputs for the certificate
        mc_addr = mc_node.getnewaddress()
        mc_node.sendtoaddress(mc_addr, 10)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
        
if __name__ == "__main__":
    ScBtLimitTest().main()
