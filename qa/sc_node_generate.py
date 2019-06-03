from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from util import assert_equal, assert_true
from SidechainTestFramework.scutil import initialize_sc_chain_clean, start_sc_nodes, connect_sc_nodes, sync_sc_mempools, sync_sc_blocks, \
                                          wait_for_next_sc_blocks
import time
import json
import random

class SidechainNodeBlockGenerationTest(SidechainTestFramework):
    
    def add_options(self, parser):
        #empty implementation
        pass
    
    def setup_chain(self):
        #empty implementation
        pass
        
    def setup_network(self, split = False):
        #empty implementation
        pass
    
    def sc_setup_chain(self):
        initialize_sc_chain_clean(self.options.tmpdir, 3, None)
    
    def sc_setup_nodes(self):
        return start_sc_nodes(3, self.options.tmpdir)
    
    def check_tx_in_block(self, node, nodename, txid):
        tx_list = []
        for tx in node.debug_info()["bestBlock"]["transactions"]:
            tx_list.append(tx["id"])
        assert_true(txid in tx_list, "Transaction {0} not included in the new block for {1}".format(str(txid), nodename))
    
    def check_tx_in_mempool(self, node, nodename, txid):
        tx_list = []
        for tx in node.nodeView_pool()["transactions"]:
            tx_list.append(tx["id"])
        assert_true(txid in tx_list, "Transaction {0} not in mempool for node {1}".format(str(txid), nodename))
    
    def run_test(self):
        print("Connecting node0, node1 and node2...")
        node0name = "node0"
        node1name = "node1"
        node2name = "node2"
        connect_sc_nodes(self.sc_nodes[0], 1)
        connect_sc_nodes(self.sc_nodes[0], 2)
        connect_sc_nodes(self.sc_nodes[1], 2)
        node1address = self.sc_nodes[1].wallet_balances()["publicKeys"][0]
        node0balance = int(self.sc_nodes[0].wallet_balances()["totalBalance"])
        node1balance = int(self.sc_nodes[1].wallet_balances()["totalBalance"])
        node2balance = int(self.sc_nodes[2].wallet_balances()["totalBalance"])
        print("-->Node0 balance: {0}".format(node0balance))
        print("-->Node1 balance: {0}".format(node1balance))
        print("-->Node2 balance: {0}".format(node2balance))
        amount = random.randint(1, node0balance - 100)
        fee = random.randint(1, 99)
        print("OK\n")
        
        print("Node0 sends to node1 address {0}, {1} coins with fee {2} coins...".format(str(node1address), amount, fee))
        j = {"amount": amount, "recipient": str(node1address), "fee": fee}
        txid = self.sc_nodes[0].wallet_transfer(json.dumps(j))["id"]
        print("-->Transaction ID: {0}".format(str(txid)))
        print("OK\n")
        
        print("Synchronizing nodes' mempools and check tx is in all of them...")
        sync_sc_mempools(self.sc_nodes)
        self.check_tx_in_mempool(self.sc_nodes[0], node0name, txid)
        self.check_tx_in_mempool(self.sc_nodes[1], node1name, txid)
        self.check_tx_in_mempool(self.sc_nodes[2], node2name, txid)
        print("OK\n")
        
        print("Node 2 generates a block...")
        assert_equal(str(self.sc_nodes[2].debug_startMining()["response"]), "ok", "Node2 couldn't start mining")
        wait_for_next_sc_blocks(self.sc_nodes[2], 2, wait = 2)
        sync_sc_blocks(self.sc_nodes)
        print("OK\n")
        
        print("Checking tx is in new block...")
        self.check_tx_in_block(self.sc_nodes[0], node0name, txid)
        self.check_tx_in_block(self.sc_nodes[1], node1name, txid)
        self.check_tx_in_block(self.sc_nodes[2], node2name, txid)
        print("OK\n")
        
        print("Checking mempool empty...")
        assert_equal(self.sc_nodes[0].nodeView_pool()["size"], 0)
        assert_equal(self.sc_nodes[1].nodeView_pool()["size"], 0)
        assert_equal(self.sc_nodes[2].nodeView_pool()["size"], 0)
        print("OK\n")
        
        print("Checking balance changed...")
        blockreward = 1
        node0newbalance = int(self.sc_nodes[0].wallet_balances()["totalBalance"])
        node1newbalance = int(self.sc_nodes[1].wallet_balances()["totalBalance"])
        node2newbalance = int(self.sc_nodes[2].wallet_balances()["totalBalance"])
        assert_equal(node0newbalance, node0balance - (amount+fee), "Coins sent/total amount mismatch for Node0")
        '''Bug in HybridApp, using transfer api call, the fee actually goes to the receiver too even if it's not the miner/forger'''
        assert_equal(node1newbalance, node1balance + amount + fee, "Coins received/total amount mismatch for Node1") 
        assert_equal(node2newbalance, node2balance + fee + blockreward, "Coins received/total amount mismatch for Node2")
        print("-->Node 0 new balance: {0}".format(node0newbalance))
        print("-->Node 1 new balance: {0}".format(node1newbalance))
        print("-->Node 2 new balance: {0}".format(node2newbalance))
        print("OK\n")
        
if __name__ == "__main__":
    SidechainNodeBlockGenerationTest().main()
    