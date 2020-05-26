#!/usr/bin/env python2
import json
import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, is_mainchain_block_included_in_sc_block, check_box_balance, \
    check_mainchain_block_reference_info, check_wallet_balance, generate_next_blocks
from SidechainTestFramework.sc_forging_util import *

"""
Check the bootstrap feature.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the SC node:
        - verify that all keys/boxes/balances are coherent with the default initialization
        - verify the MC block is included
        - create new forward transfer to sidechain
        - verify that all keys/boxes/balances are changed
        - create two withdrawal requests in the sidechain
        - mine blocks to withdrawal epoch end
        - verify that backward transfer certificate is created in mainchain and then is returned back
          to sidechain as part of mainchain block        
"""
class SCBootstrap(SidechainTestFramework):

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10

    def setup_nodes(self):
        num_nodes = 1
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-logtimemicros=1']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, "1".zfill(64), 100, self.sc_withdrawal_epoch_length), sc_node_configuration)
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
        check_wallet_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account_balance)
        check_box_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account, 3, 1,
                                 self.sc_nodes_bootstrap_info.genesis_account_balance)


        # create FT to SC to withdraw later
        sc_address = sc_node.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_account = Account("", sc_address)
        ft_amount = 10
        mc_node.sc_send(sc_address, ft_amount, self.sc_nodes_bootstrap_info.sidechain_id)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block and check that FT appears in SC node wallet
        mcblock_hash1 = mc_node.generate(1)[0]
        scblock_id1 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node)

        # check all keys/boxes/balances are coherent with the default initialization
        check_wallet_balance(sc_node, self.sc_nodes_bootstrap_info.genesis_account_balance + ft_amount)
        check_box_balance(sc_node, sc_account, 1, 1, ft_amount)

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 3 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]
        scblock_id2 = generate_next_blocks(sc_node, "first node", 3)[2]
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)

        # Wait until Certificate will appear in MC node mempool
        attempts = 10
        while mc_node.getmempoolinfo()["size"] == 0 and attempts > 0:
            print("Wait for certificate in mc mempool...")
            time.sleep(1)
            attempts -= 1
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mmepool.")

        # Get Certificate for Withdrawal epoch 0 and verify it
        we0_certHash = mc_node.getrawmempool()[0]
        print("Withdrawal epoch 0 certificate hash = " + we0_certHash)
        we0_cert = mc_node.getrawcertificate(we0_certHash, 1)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_cert["cert"]["scid"], "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_mcblock_hash, we0_cert["cert"]["endEpochBlockHash"], "Sidechain endEpochBlockHash in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")

        # Generate MC block and verify that certificate is present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]), "MC block expected to contain 1 Certificate.")
        assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0], "MC block expected to contain certificate.")

        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)

        # Verify Certificate for epoch 0 on SC side
        we0_sc_cert = sc_node.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]["withdrawalEpochCertificate"]
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_sc_cert["sidechainId"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_sc_cert["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_mcblock_hash, we0_sc_cert["endEpochBlockHash"],
                     "Sidechain endEpochBlockHash in certificate is wrong.")
        assert_equal(0, len(we0_sc_cert["backwardTransferOutputs"]), "Backward transfer amount in certificate is wrong.")
        assert_equal(we0_certHash, we0_sc_cert["hash"], "Certificate hash is different to the one in MC.")

        return

        mc_address = self.nodes[0].getnewaddress("", True)

        withdrawal_request = {"outputs": [ \
                               { "publicKey": mc_address,
                                 "value": self.sc_nodes_bootstrap_info.genesis_account_balance / 2 }
                              ]
                             }
        withdrawCoinsJson = sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))
        if "result" not in withdrawCoinsJson:
            fail("Withdraw coins failed: " + json.dumps(withdrawCoinsJson))
        else:
            print("Coins withdrawn: " + json.dumps(withdrawCoinsJson))

        generate_next_blocks(sc_node, "first node", 1)
        mc_block_id = self.nodes[0].generate(1) #height 222

        withdrawal_request = {"outputs": [ \
                               { "publicKey": mc_address,
                                 "value": self.sc_nodes_bootstrap_info.genesis_account_balance / 2 }
                              ]
                             }

        withdrawCoinsJson = sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))
        if "result" not in withdrawCoinsJson:
            fail("Withdraw coins failed: " + json.dumps(withdrawCoinsJson))
        else:
            print("Coins withdrawn: " + json.dumps(withdrawCoinsJson))

        sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))

        generate_next_blocks(sc_node, "first node", 1)
        mc_block_id = self.nodes[0].generate(3) #height 225

        generate_next_blocks(sc_node, "first node", 1)

        generate_next_blocks(sc_node, "first node", 1)

        attempts = 10
        while (mc_node.getmempoolinfo()["size"] == 0 and attempts > 0):
            print("Wait for certificate in mc mempool...")
            time.sleep(1)
            attempts -= 1
        sc_best_block = sc_node.block_best()["result"]

        bt_certificate_exists = False

        for mc_bloc_reference_data in sc_best_block["block"]["mainchainBlockReferencesData"]:
            if "backwardTransferCertificate" in mc_bloc_reference_data:
                bt_certificate_exists = True

        assert_true(bt_certificate_exists, "Backward transfer certificate not found.")

if __name__ == "__main__":
    SCBootstrap().main()
