#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from util import assert_equal, assert_true, initialize_chain_clean, start_nodes, connect_nodes_bi, sync_mempools, sync_blocks
from SidechainTestFramework.scutil import initialize_sc_chain_clean, start_sc_nodes, connect_sc_nodes, connect_sc_nodes_bi, sync_sc_mempools, sync_sc_blocks, \
                                          wait_for_next_sc_blocks
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
        initialize_sc_chain_clean(self.options.tmpdir, 3, None)
        
    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        print("Connecting sidechain nodes node0, node1 and node2...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        connect_sc_nodes(self.sc_nodes[0], 2)
        connect_sc_nodes(self.sc_nodes[1], 2)
        print("Connections for node0: ", self.sc_nodes[0].peers_connected())
        print("Connections for node1: ", self.sc_nodes[1].peers_connected())
        print("Connections for node2: ", self.sc_nodes[2].peers_connected())
    
    def sc_setup_nodes(self):
        return start_sc_nodes(3, self.options.tmpdir)
    
    def create_tx(self, sender, receiver, amount, fee):
        j = {"amount": amount, "recipient": str(receiver), "fee": fee}
        txid = sender.wallet_transfer(json.dumps(j))["id"]
        print("--->SC Transaction ID: {0}".format(str(txid)))
        return txid
            
    def check_tx_in_block(self, node, nodename, txid):
        tx_list = []
        for tx in node.debug_info()["bestBlock"]["transactions"]:
            tx_list.append(tx["id"])
        assert_true(txid in tx_list, "Transaction {0} not included in the new block for SC {1}".format(str(txid), nodename))
    
    def check_tx_in_mempool(self, node, nodename, txid, mainchain = True):
        tx_list = []
        for tx in node.nodeView_pool()["transactions"]:
            tx_list.append(tx["id"])
        assert_true(txid in tx_list, "Transaction {0} not in mempool for SC {1}".format(str(txid), nodename))
    
    def run_test(self):
        
        #Saving basic information and setting tx fee and amount
        print("Nodes initialization...")
        scnode0name = "node0"
        scnode1name = "node1"
        scnode2name = "node2"
        scnode1address = self.sc_nodes[1].wallet_balances()["publicKeys"][0]
        scnode0balance = int(self.sc_nodes[0].wallet_balances()["totalBalance"])
        scnode1balance = int(self.sc_nodes[1].wallet_balances()["totalBalance"])
        scnode2balance = int(self.sc_nodes[2].wallet_balances()["totalBalance"])
        print("-->SC Node 0 balance: {0}".format(scnode0balance))
        print("-->SC Node 1 balance: {0}".format(scnode1balance))
        print("-->SC Node 2 balance: {0}".format(scnode2balance))
        sc_amount = random.randint(1, scnode0balance - 100)
        sc_fee = random.randint(1, 99)
        print("OK\n")
        
        #Node0 do a tx to Node1
        print("Sending transactions...")
        print("-->SC Node 0 sends to SC Node 1 address {0}, {1} coins with fee {2} coins...".format(str(scnode1address), sc_amount, sc_fee))
        sctxid = self.create_tx(self.sc_nodes[0], scnode1address, sc_amount, sc_fee)
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
        assert_equal("ok", str(self.sc_nodes[2].debug_startMining()["response"]), "SC Node 2 couldn't start mining")
        wait_for_next_sc_blocks(self.sc_nodes[2], 4, wait_for = 60)
        print("Synchronizing everything...")
        self.sc_sync_all()
        print("OK\n")
        
        #Check tx is in block for each node
        print("Checking block inclusion...")
        self.check_tx_in_block(self.sc_nodes[0], scnode0name, sctxid)
        self.check_tx_in_block(self.sc_nodes[1], scnode1name, sctxid)
        self.check_tx_in_block(self.sc_nodes[2], scnode2name, sctxid)
        print("OK\n")
        
        #Check that tx isn't in nodes' mempools anymore
        print("Checking mempools empty...")
        assert_equal(0, self.sc_nodes[0].nodeView_pool()["size"])
        assert_equal(0, self.sc_nodes[1].nodeView_pool()["size"])
        assert_equal(0, self.sc_nodes[2].nodeView_pool()["size"])
        print("OK\n")
        
        #Checking that node0 balance has decreased by amount-fee, node1 balance has increased by amount and node2 balance has increased of blockreward+txfee
        print("Checking balance changed...")
        scblockreward = 1
        node0newbalance = int(self.sc_nodes[0].wallet_balances()["totalBalance"])
        node1newbalance = int(self.sc_nodes[1].wallet_balances()["totalBalance"])
        node2newbalance = int(self.sc_nodes[2].wallet_balances()["totalBalance"])
        assert_equal(scnode0balance - (sc_amount+sc_fee), node0newbalance, "Coins sent/total sc_amount mismatch for Node0")
        assert_equal(scnode1balance + sc_amount, node1newbalance, "Coins received/total sc_amount mismatch for Node1")
        assert_equal(scnode2balance + sc_fee + scblockreward, node2newbalance, "Coins received/total sc_amount mismatch for Node2")
        print("-->Node 0 new balance: {0}".format(node0newbalance))
        print("-->Node 1 new balance: {0}".format(node1newbalance))
        print("-->Node 2 new balance: {0}".format(node2newbalance))
        print("OK\n")
        
        
if __name__ == "__main__":
    SidechainNodeBlockGenerationTest().main()
    