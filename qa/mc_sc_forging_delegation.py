#!/usr/bin/env python3
import logging

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account, LARGE_WITHDRAWAL_EPOCH_LENGTH
from test_framework.util import assert_equal, assert_true, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    connect_sc_nodes, check_wallet_coins_balance, check_box_balance, generate_next_block
from SidechainTestFramework.sc_forging_util import *


"""
Check Latus forger behavior for:
1. Forging using delegated stake composed by several ForgerBoxes
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC nodes are connected to the MC node.
Test:
    - Do FT to the first SC node.
    - Sync SC and MC networks.
    - Delegate coins to forge to the second SC node via 2 ForgerBoxes from the first SC node.
    - Forge SC block by the first SC node for the next consensus epoch.
    - Forge SC block by the second SC node for the next consensus epoch. (ForgerBoxes must become active for this epoch).
    - Check forging stake info for the block generated by the second SC Node.
    - Spend the first SC node forging stake.
    - Check that for the next epoch the first SC node able to forge.
    - Check that in 2 epochs the first SC node NOT able to forge - no forging stake.
"""


class MCSCForgingDelegation(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self, split=False):
        # Setup nodes and connect them
        self.nodes = self.setup_nodes()

    def setup_nodes(self):
        # Start MC node
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        # Bootstrap new SC, specify SC nodes connection to MC node
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )

        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration, sc_node_2_configuration)
        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720*120*5)

    def sc_setup_nodes(self):
        # Start 2 SC nodes
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        # Do FT of 500 Zen to SC Node 1
        sc_node1_address = sc_node1.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_node1_account = Account("", sc_node1_address)
        ft_amount = 500 # Zen
        mc_return_address = mc_node.getnewaddress()
        ft_args = [{
            "toaddress": sc_node1_address,
            "amount": ft_amount,
            "scid": self.sc_nodes_bootstrap_info.sidechain_id,
            "mcReturnAddress": mc_return_address
        }]
        mc_node.sc_send(ft_args)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block and check that FT appears in SC node wallet
        mcblock_hash1 = mc_node.generate(1)[0]
        scblock_id1 = generate_next_block(sc_node1, "first node")
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node1)

        # check all keys/boxes/balances are coherent with the default initialization
        check_wallet_coins_balance(sc_node1, self.sc_nodes_bootstrap_info.genesis_account_balance + ft_amount)
        check_box_balance(sc_node1, sc_node1_account, "ZenBox", 1, ft_amount)

        # Delegate 300 Zen and 200 Zen to SC node 2 - expected stake is 500 Zen
        sc_node2_address = sc_node2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc_node2_vrf_address = sc_node2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        forgerStake1_amount = 300 # Zen
        forgerStake2_amount = ft_amount - forgerStake1_amount # Zen
        forgerStakes = {"outputs": [
                                {
                                    "publicKey": sc_node1_address, # SC node 1 is an owner
                                    "blockSignPublicKey": sc_node2_address,  # SC node 2 is a block signer
                                    "vrfPubKey": sc_node2_vrf_address,
                                    "value": forgerStake1_amount * 100000000  # in Satoshi
                                },
                                {
                                    "publicKey": sc_node1_address,  # SC node 1 is an owner
                                    "blockSignPublicKey": sc_node2_address,  # SC node 2 is a block signer
                                    "vrfPubKey": sc_node2_vrf_address,
                                    "value": forgerStake2_amount * 100000000  # in Satoshi
                                }
                            ]
                        }
        makeForgerStakeJsonRes = sc_node1.transaction_makeForgerStake(json.dumps(forgerStakes))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))

        # Generate SC block
        generate_next_block(sc_node1, "first node")

        # Sync SC nodes
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        # Check SC nodes balances
        # SC node 1 owns ForgerBoxes
        check_wallet_coins_balance(sc_node1, self.sc_nodes_bootstrap_info.genesis_account_balance + ft_amount)
        check_box_balance(sc_node1, sc_node1_account, "ForgerBox", 2, ft_amount)
        # SC node 2 doesn't own ForgerBoxes
        check_wallet_coins_balance(sc_node2, 0)

        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # Generate SC block on SC node 2 for the next consensus epoch
        scnode2_block_id = generate_next_block(sc_node2, "second node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        # Check ForgingStake for SC block
        res = sc_node2.block_findById(blockId=scnode2_block_id)
        stakeInfo = res["result"]["block"]["header"]["forgingStakeInfo"]
        logging.info("SC Node 2 forged block with forging info:\n" + json.dumps(stakeInfo, indent=4))
        assert_equal(stakeInfo["stakeAmount"], ft_amount * 100000000,
                     "Forging stake is wrong.")
        assert_equal(stakeInfo["blockSignPublicKey"]["publicKey"], sc_node2_address,
                     "Forging stake block sign key is wrong.")
        assert_equal(stakeInfo["vrfPublicKey"]["publicKey"], sc_node2_vrf_address,
                     "Forging stake vrf key is wrong.")

        # spend forger box of 100 Zen for SC node 1
        all_forger_boxes_req = {"boxTypeClass": "ForgerBox"}
        forger_box_id = sc_node1.wallet_allBoxes(json.dumps(all_forger_boxes_req))["result"]["boxes"][0]["id"]
        spend_forger_stakes_req = {
            "transactionInputs" : [
                {
                "boxId": forger_box_id
                }
            ],
            "regularOutputs": [
                {
                    "publicKey": sc_node1_address,
                    "value": self.sc_nodes_bootstrap_info.genesis_account_balance * 100000000  # in Satoshi
                }
            ],
            "forgerOutputs": []
        }

        tx_hex = sc_node1.transaction_spendForgingStake(json.dumps(spend_forger_stakes_req))
        if "result" not in tx_hex:
            fail("spend forger stake failed: " + json.dumps(tx_hex))
        else:
            logging.info("Forger stake was spend: " + json.dumps(tx_hex))

        # Generate one more SC block on SC node 1 to include transaction
        generate_next_block(sc_node1, "first node")

        # Generate SC block on SC node 1 for the next consensus epoch - must be successful
        generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)

        # Generate SC block on SC node 1 for the next consensus epoch.
        # Must fail, because of all forger stakes were spent 2 consensus epochs before.
        try:
            generate_next_block(sc_node1, "first node", force_switch_to_next_epoch=True)
        except:
            pass
        else:
            fail("No forging stakes expected for SC node 1.")

        # now spend all of the remaining forging stakes delegated by SC1 to SC2
        forger_boxes_1 = sc_node1.wallet_allBoxes(json.dumps(all_forger_boxes_req))["result"]["boxes"]
        for box in forger_boxes_1:
            spend_forger_stakes_req = {
                "transactionInputs": [{"boxId": box['id']}],
                "regularOutputs": [{"publicKey": sc_node1_address, "value": box['value']}],
                "forgerOutputs": []
            }

            sc_node1.transaction_spendForgingStake(json.dumps(spend_forger_stakes_req))
            self.sc_sync_all()

        response = sc_node1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(len(response['result']['transactionIds']), 2)

        # do not switch epoch yet
        generate_next_block(sc_node2, "second node", force_switch_to_next_epoch=False)
        self.sc_sync_all()

        # check we do not have any forging stake at all
        forger_boxes_1 = sc_node1.wallet_allBoxes(json.dumps(all_forger_boxes_req))["result"]["boxes"]
        forger_boxes_2 = sc_node2.wallet_allBoxes(json.dumps(all_forger_boxes_req))["result"]["boxes"]
        assert_true(len(forger_boxes_1) == 0)
        assert_true(len(forger_boxes_2) == 0)

        # Try to generate one more block switching epoch, that should fail because even if the forging itself would
        # take place (the SC2 forger info points to two epoch earlier, and back then we had stakes delegated by SC1
        # to him), yet the block will not be applied since consensus epoch info are not valid (empty list of stakes)
        try:
            generate_next_block(sc_node2, "second node", force_switch_to_next_epoch=True)
        except Exception as e:
            logging.info("We had an exception as expected: {}".format(str(e)))
        else:
            fail("Forging should not happen")



if __name__ == "__main__":
    MCSCForgingDelegation().main()
