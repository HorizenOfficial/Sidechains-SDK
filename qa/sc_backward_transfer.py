#!/usr/bin/env python2
import json
import time
import math

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_equal, assert_true, assert_false, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, check_box_balance, check_wallet_coins_balance, generate_next_blocks, generate_next_block
from SidechainTestFramework.sc_forging_util import *

"""
Check Certificate automatic creation and submission to MC:
1. Creation of Certificate with no backward transfers.
2. Creation of Certificate with multiple backward transfers.

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Test:
    For the SC node:
        - verify that all keys/boxes/balances are coherent with the default initialization
        - verify the MC block is included
        - create new forward transfer to sidechain
        - verify that all keys/boxes/balances are changed
        - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
        - generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
        - check epoch 0 certificate with not backward transfers in the MC mempool
        - mine 1 more MC block and forge 1 more SC block, check Certificate inclusion into SC block
        - make 2 different withdrawals from SC
        - reach next withdrawal epoch and verify that certificate for epoch 1 was added to MC mempool
          and then to MC/SC blocks.
        - verify epoch 1 certificate, verify backward transfers list    
"""
class SCBackwardTransfer(SidechainTestFramework):

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws',  '-logtimemicros=1', '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Check that MC block with sc creation tx is referenced in the genesis sc block
        mcblock_hash0 = mc_node.getbestblockhash()
        scblock_id0 = sc_node.block_best()["result"]["block"]["id"]
        check_mcreference_presence(mcblock_hash0, scblock_id0, sc_node)

        # Check that MC block with sc creation tx height is the same as in genesis info.
        sc_creation_mc_block_height = mc_node.getblock(mcblock_hash0)["height"]
        assert_equal(sc_creation_mc_block_height, self.sc_nodes_bootstrap_info.mainchain_block_height,
                     "Genesis info expected to have the same genesis mc block height as in MC node.")

        # check all keys/boxes/balances are coherent with the default initialization
        check_wallet_coins_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account_balance)
        check_box_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account, 3, 1,
                                 self.sc_nodes_bootstrap_info.genesis_account_balance)


        # create FT to SC to withdraw later
        sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_account = Account("", sc_address)
        ft_amount = 10
        mc_return_address = mc_node.getnewaddress()

        ft_args = [{
            "toaddress": sc_address,
            "amount": ft_amount,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }]
        mc_node.sc_send(ft_args)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block and check that FT appears in SC node wallet
        mcblock_hash1 = mc_node.generate(1)[0]
        scblock_id1 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node)

        # check all keys/boxes/balances are coherent with the default initialization
        check_wallet_coins_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account_balance + ft_amount)
        check_box_balance(sc_node, sc_account, 1, 1, ft_amount)

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        scblock_id2 = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Check that certificate generation skipped because mempool have certificate with same quality
        generate_next_blocks(sc_node, "first node", 1)[0]
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")

        # Get Certificate for Withdrawal epoch 0 and verify it
        we0_certHash = mc_node.getrawmempool()[0]
        print("Withdrawal epoch 0 certificate hash = " + we0_certHash)
        we0_cert = mc_node.getrawtransaction(we0_certHash, 1)
        we0_cert_hex = mc_node.getrawtransaction(we0_certHash)
        print("Withdrawal epoch 0 certificate hex = " + we0_cert_hex)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_cert["cert"]["scid"], "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")

        # Generate MC block and verify that certificate is present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 1 Certificate.")
        assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0], "MC block expected to contain certificate.")
        print("MC block with withdrawal certificate for epoch 0 = {0}\n".format(str(mc_node.getblock(we1_2_mcblock_hash, False))))

        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)

        # Check that certificate generation skipped because chain have certificate with same quality
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")

        # Verify Certificate for epoch 0 on SC side
        mbrefdata = sc_node.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]
        we0_sc_cert = mbrefdata["topQualityCertificate"]
        assert_equal(len(mbrefdata["lowerCertificateLeaves"]), 0)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_sc_cert["sidechainId"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_sc_cert["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_sc_cert["endCumulativeScTxCommitmentTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(0, len(we0_sc_cert["backwardTransferOutputs"]), "Backward transfer amount in certificate is wrong.")
        assert_equal(we0_certHash, we0_sc_cert["hash"], "Certificate hash is different to the one in MC.")



        # Try to withdraw coins from SC to MC: 2 withdrawals with the same amount
        mc_address1 = mc_node.getnewaddress()
        print("First BT MC public key address is {}".format(mc_address1))
        bt_amount1 = ft_amount - 3
        sc_bt_amount1 = bt_amount1 * 100000000 # in Satoshi
        withdrawal_request = {"outputs": [ \
                               { "mainchainAddress": mc_address1,
                                 "value": sc_bt_amount1 }
                              ]
                             }
        withdrawCoinsJson = sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))
        if "result" not in withdrawCoinsJson:
            fail("Withdraw coins failed: " + json.dumps(withdrawCoinsJson))
        else:
            print("Coins withdrawn: " + json.dumps(withdrawCoinsJson))

        # Generate SC block
        generate_next_blocks(sc_node, "first node", 1)

        mc_address2 = self.nodes[0].getnewaddress()
        print("Second BT MC public key address is {}".format(mc_address2))
        bt_amount2 = ft_amount - bt_amount1
        sc_bt_amount2 = bt_amount2 * 100000000  # in Satoshi
        withdrawal_request = {"outputs": [ \
                               { "mainchainAddress": mc_address2,
                                 "value": sc_bt_amount2 }
                              ]
                             }

        withdrawCoinsJson = sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))
        if "result" not in withdrawCoinsJson:
            fail("Withdraw coins failed: " + json.dumps(withdrawCoinsJson))
        else:
            print("Coins withdrawn: " + json.dumps(withdrawCoinsJson))

        sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))

        # Generate SC block
        generate_next_blocks(sc_node, "first node", 1)

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(8)[7]
        print("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)
        we1_end_scblock_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we1_end_mcblock_hash, we1_end_scblock_id, sc_node)

        # Generate first mc block of the next epoch
        we2_1_mcblock_hash = mc_node.generate(1)[0]
        we2_1_scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_1_mcblock_hash, we2_1_scblock_id, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Check that certificate generation skipped because mempool have certificate with same quality
        generate_next_blocks(sc_node, "first node", 1)[0]
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")

        # Get Certificate for Withdrawal epoch 1 and verify it
        we1_certHash = mc_node.getrawmempool()[0]
        print("Withdrawal epoch 1 certificate hash = " + we1_certHash)
        we1_cert = mc_node.getrawtransaction(we1_certHash, 1)
        we1_cert_hex = mc_node.getrawtransaction(we1_certHash)
        print("Withdrawal epoch 1 certificate hex = " + we1_cert_hex)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we1_cert["cert"]["scid"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(1, we1_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we1_end_epoch_cum_sc_tx_comm_tree_root, we1_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(bt_amount1 + bt_amount2, we1_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")


        # Generate MC block and verify that certificate is present
        we2_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we2_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we2_2_mcblock_hash)["cert"]),
                     "MC block expected to contain 1 Certificate.")
        assert_equal(we1_certHash, mc_node.getblock(we2_2_mcblock_hash)["cert"][0],
                     "MC block expected to contain certificate.")
        print("MC block with withdrawal certificate for epoch 1 = {0}\n".format(
            str(mc_node.getblock(we2_2_mcblock_hash, False))))

        # Check certificate BT entries
        assert_equal(bt_amount1, we1_cert["vout"][1]["value"], "First BT amount is wrong.")
        assert_equal(bt_amount2, we1_cert["vout"][2]["value"], "Second BT amount is wrong.")

        cert_address_1 = we1_cert["vout"][1]["scriptPubKey"]["addresses"][0]
        assert_equal(mc_address1, cert_address_1, "First BT standard address is wrong.")
        cert_address_2 = we1_cert["vout"][2]["scriptPubKey"]["addresses"][0]
        assert_equal(mc_address2, cert_address_2, "Second BT standard address is wrong.")

        # Generate SC block and verify that certificate is synced back
        scblock_id5 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_2_mcblock_hash, scblock_id5, sc_node)

        # Check that certificate generation skipped because chain have certificate with same quality
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"], "Expected certificate generation will be skipped.")

        # Verify Certificate for epoch 1 on SC side
        mbrefdata = sc_node.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]
        we1_sc_cert = mbrefdata["topQualityCertificate"]
        assert_equal(len(mbrefdata["lowerCertificateLeaves"]), 0)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we1_sc_cert["sidechainId"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(1, we1_sc_cert["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we1_end_epoch_cum_sc_tx_comm_tree_root, we1_sc_cert["endCumulativeScTxCommitmentTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(2, len(we1_sc_cert["backwardTransferOutputs"]),
                     "Backward transfer amount in certificate is wrong.")

        sc_pub_key_1 = we1_sc_cert["backwardTransferOutputs"][0]["address"]
        assert_equal(mc_address1, sc_pub_key_1, "First BT address is wrong.")
        assert_equal(sc_bt_amount1, we1_sc_cert["backwardTransferOutputs"][0]["amount"], "First BT amount is wrong.")

        sc_pub_key_2 = we1_sc_cert["backwardTransferOutputs"][1]["address"]
        assert_equal(mc_address2, sc_pub_key_2, "Second BT address is wrong.")
        assert_equal(sc_bt_amount2, we1_sc_cert["backwardTransferOutputs"][1]["amount"], "Second BT amount is wrong.")

        assert_equal(we1_certHash, we1_sc_cert["hash"], "Certificate hash is different to the one in MC.")


if __name__ == "__main__":
    SCBackwardTransfer().main()
