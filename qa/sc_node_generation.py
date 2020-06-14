#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, initialize_chain_clean, start_nodes, connect_nodes_bi, sync_mempools, sync_blocks
from SidechainTestFramework.scutil import initialize_default_sc_chain_clean, start_sc_nodes, connect_sc_nodes, connect_sc_nodes_bi, sync_sc_mempools, sync_sc_blocks, \
                                          wait_for_next_sc_blocks, generate_next_blocks
import time
import json
import random

"""
    Setup 3 SC Nodes and connect them togheter. Let Node0 transfer some coins to Node1 and Node2 mine the new block. Check that everything is consistent.
"""
class SidechainNodeBlockGenerationTest(SidechainTestFramework):
    
    def add_options(self, parser):
        #empty implementation
        pass
    
    def setup_chain(self):
        pass
        
    def setup_network(self, split = False):
        pass
    
    def sc_setup_chain(self):
        initialize_default_sc_chain_clean(self.options.tmpdir, 3)
        
    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        print("Connecting sidechain nodes node0, node1 and node2...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        connect_sc_nodes(self.sc_nodes[0], 2)
        connect_sc_nodes(self.sc_nodes[1], 2)
        self.sc_sync_all()
    
    def sc_setup_nodes(self):
        return start_sc_nodes(3, self.options.tmpdir)

    def send_forger_stake_coins(self, sender_node, input_box, receiver, forger_box_receiver_pub_key, forger_box_receiver_vrf_pub_key, amount, total_box_value):
        j = {"transactionInputs": [ { \
            "boxId": input_box \
            } ], \
            "regularOutputs": [ { \
            "publicKey": receiver, \
            "value": amount \
            } ], \
            "forgerOutputs": [ { \
            "publicKey": forger_box_receiver_pub_key, \
            "blockSignPublicKey": forger_box_receiver_pub_key, \
            "vrfPubKey": forger_box_receiver_vrf_pub_key, \
            "value": total_box_value - amount \
            } ], \
            "format": True
            #            "format": "true"
        }
        print j
        request = json.dumps(j)
        response = sender_node.transaction_spendForgingStake(request)
        txid = response["result"]["transaction"]["id"]
        print(txid)
        print("--->SC Transaction ID: {0}".format(str(txid)))
        return txid

    def send_coins(self, sender, receiver, amount, fee):
        j = {"outputs": [ {\
                "publicKey": receiver, \
                "value": amount \
                } ], \
            "fee": fee, \
#            "format": "true"
            }
        request = json.dumps(j)
        txid = sender.transaction_sendCoinsToAddress(request)["result"]["transactionId"]
        print("--->SC Transaction ID: {0}".format(str(txid)))
        return txid


    def check_tx_in_block(self, node, nodename, block_id, tx_id):
        tx_list = []
        j = {"blockId": block_id}
        request = json.dumps(j)
        block = node.block_findById(request)
        assert_true(block.has_key("result"), "Error during getBlock for SC {0}".format(nodename))
        for tx in block["result"]["block"]["sidechainTransactions"]:
            tx_list.append(tx["id"])
        assert_true(tx_id in tx_list, "Transaction {0} not included in the new block for SC {1}".format(str(tx_id), nodename))
    
    def check_tx_in_mempool(self, node, nodename, txid, mainchain = True):
        tx_list = []
        for tx in node.transaction_allTransactions()["result"]["transactions"]:
            tx_list.append(tx["id"])
        assert_true(txid in tx_list, "Transaction {0} not in mempool for SC {1}".format(str(txid), nodename))
    
    def run_test(self):
        
        #Saving basic information and setting tx fee and amount
        print("Nodes initialization...")
        scnode0name = "node0"
        scnode1name = "node1"
        scnode2name = "node2"
        scnodeadresses = self.sc_nodes[1].wallet_allPublicKeys()
        scnode1address = self.sc_nodes[1].wallet_allPublicKeys()["result"]["propositions"][0]["publicKey"]

        boxes_request_on_regular_boxes = json.dumps({"boxType": "com.horizen.box.RegularBox"})
        sc_node_0_regular_box_balance = int(self.sc_nodes[0].wallet_balance(boxes_request_on_regular_boxes)["result"]["balance"])
        sc_node_1_regular_box_balance = int(self.sc_nodes[1].wallet_balance(boxes_request_on_regular_boxes)["result"]["balance"])
        sc_node_2_regular_box_balance = int(self.sc_nodes[2].wallet_balance(boxes_request_on_regular_boxes)["result"]["balance"])
        print("-->SC Node 0 regular boxes balance: {0}".format(sc_node_0_regular_box_balance))
        print("-->SC Node 1 regular boxes balance: {0}".format(sc_node_1_regular_box_balance))
        print("-->SC Node 2 regular boxes balance: {0}".format(sc_node_2_regular_box_balance))

        balance_request_on_forger_boxes = json.dumps({"boxType": "com.horizen.box.ForgerBox"})
        sc_node_0_forger_box_balance = int(self.sc_nodes[0].wallet_balance(balance_request_on_forger_boxes)["result"]["balance"])
        sc_node_1_forger_box_balance = int(self.sc_nodes[1].wallet_balance(balance_request_on_forger_boxes)["result"]["balance"])
        sc_node_2_forger_box_balance = int(self.sc_nodes[2].wallet_balance(balance_request_on_forger_boxes)["result"]["balance"])
        print("-->SC Node 0 forger boxes balance: {0}".format(sc_node_0_forger_box_balance))
        print("-->SC Node 1 forger boxes balance: {0}".format(sc_node_1_forger_box_balance))
        print("-->SC Node 2 forger boxes balance: {0}".format(sc_node_2_forger_box_balance))
        sc_amount = random.randint(sc_node_0_forger_box_balance - 1000, sc_node_0_forger_box_balance - 100)
        sc_fee = 0
        print("OK\n")
        
        #Node0 do a tx to Node1
        print("Sending transactions...")
        boxes_request_on_forger_boxes = json.dumps({"boxTypeClass": "com.horizen.box.ForgerBox"})
        sc_node_0_first_forger_box = self.sc_nodes[0].wallet_allBoxes(boxes_request_on_forger_boxes)["result"]["boxes"][0]
        sc_node_0_first_forger_box_id = sc_node_0_first_forger_box["id"]
        sc_node_0_first_forger_box_pub_key = sc_node_0_first_forger_box["proposition"]["publicKey"]
        sc_node_0_first_forger_box_vrf_pub_key = sc_node_0_first_forger_box["vrfPubKey"]["publicKey"]

        print ("Found forger box with id {forgerBxId} for node 0".format(forgerBxId = sc_node_0_first_forger_box_id))
        print("-->SC Node 0 sends to SC Node 1 address {0}, {1} coins with fee {2} coins...".format(str(scnode1address), sc_amount, sc_fee))
        sctxid = self.send_forger_stake_coins(self.sc_nodes[0], sc_node_0_first_forger_box_id, scnode1address, sc_node_0_first_forger_box_pub_key, sc_node_0_first_forger_box_vrf_pub_key, sc_amount, sc_node_0_forger_box_balance)
        print("OK\n")
        
        #Check tx appears in all nodes' mempools
        print("Synchronizing nodes' mempools and check tx is in all of them...")
        sync_sc_mempools(self.sc_nodes)
        self.check_tx_in_mempool(self.sc_nodes[0], scnode0name, sctxid)
        self.check_tx_in_mempool(self.sc_nodes[1], scnode1name, sctxid)
        self.check_tx_in_mempool(self.sc_nodes[2], scnode2name, sctxid)
        print("OK\n")
        
        #Node 2 generates a block, then checking that block appeared in chain and synchronize everything
        print("Generating new blocks...")
        print("-->SC Node 2 generates a block...")
        blocks = generate_next_blocks(self.sc_nodes[2], scnode2name, 1)
        #TODO check implementation
        #wait_for_next_sc_blocks(self.sc_nodes[2], 4, wait_for = 60)
        print("Synchronizing everything...")
        self.sc_sync_all()
        print("OK\n")
        
        #Check tx is in block for each node
        print("Checking block inclusion...")
        #TODO change implementation to support multiple blocks
        for block in blocks:
            self.check_tx_in_block(self.sc_nodes[0], scnode0name, block, sctxid)
            self.check_tx_in_block(self.sc_nodes[1], scnode1name, block, sctxid)
            self.check_tx_in_block(self.sc_nodes[2], scnode2name, block, sctxid)
        print("OK\n")
        
        #Check that tx isn't in nodes' mempools anymore
        print("Checking mempools empty...")
        assert_equal(0, len(self.sc_nodes[0].transaction_allTransactions()["result"]["transactions"]))
        assert_equal(0, len(self.sc_nodes[1].transaction_allTransactions()["result"]["transactions"]))
        assert_equal(0, len(self.sc_nodes[2].transaction_allTransactions()["result"]["transactions"]))
        print("OK\n")
        #Checking that node0 balance has decreased by amount-fee, node1 balance has increased by amount and node2 balance has increased of blockreward+txfee
        print("Checking balance changed...")
        scblockreward = 1
        node_0_new_forger_balance = int(self.sc_nodes[0].wallet_balance(balance_request_on_forger_boxes)["result"]["balance"])
        node_1_new_forger_balance = int(self.sc_nodes[1].wallet_balance(balance_request_on_forger_boxes)["result"]["balance"])
        node_2_new_forger_balance = int(self.sc_nodes[2].wallet_balance(balance_request_on_forger_boxes)["result"]["balance"])
        assert_equal(sc_node_0_forger_box_balance - (sc_amount+sc_fee), node_0_new_forger_balance, "Coins sent/total sc_amount mismatch for Node0")
        #@TODO verify that balance forger boxes for node 1 and node 2 are not changed after node 0 forger box spent spent
        print("-->Node 0 new forger boxes balance: {0}".format(node_0_new_forger_balance))
        print("-->Node 1 new forger boxes balance: {0}".format(node_1_new_forger_balance))
        print("-->Node 2 new forger boxes balance: {0}".format(node_2_new_forger_balance))
        print("OK\n")

        node_0_new_regular_balance = int(self.sc_nodes[0].wallet_balance(boxes_request_on_regular_boxes)["result"]["balance"])
        node_1_new_regular_balance = int(self.sc_nodes[1].wallet_balance(boxes_request_on_regular_boxes)["result"]["balance"])
        node_2_new_regular_balance = int(self.sc_nodes[2].wallet_balance(boxes_request_on_regular_boxes)["result"]["balance"])
        assert_equal(sc_node_0_regular_box_balance, node_0_new_regular_balance, "Coins sent/total sc_amount mismatch for Node0")
        assert_equal(sc_node_1_regular_box_balance + sc_amount, node_1_new_regular_balance, "Coins received/total sc_amount mismatch for Node1")
        assert_equal(sc_node_2_regular_box_balance, node_2_new_regular_balance, "Coins received/total sc_amount mismatch for Node2")
        print("-->Node 0 new regular boxes balance: {0}".format(node_0_new_regular_balance))
        print("-->Node 1 new regular boxes balance: {0}".format(node_1_new_regular_balance))
        print("-->Node 2 new regular boxes balance: {0}".format(node_2_new_regular_balance))
        
        
if __name__ == "__main__":
    SidechainNodeBlockGenerationTest().main()
    