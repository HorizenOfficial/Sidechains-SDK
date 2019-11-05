#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, initialize_chain_clean, start_nodes, connect_nodes_bi, sync_mempools, sync_blocks
from SidechainTestFramework.scutil import initialize_default_sc_chain_clean, start_sc_nodes, connect_sc_nodes, sync_sc_mempools, sync_sc_blocks, \
                                          wait_for_next_sc_blocks
import json
import random
import shutil
from decimal import Decimal

"""
    Setup 3 MC Nodes and connect them togheter. Let Node0 transfer some coins to Node1 and Node2 mine the new block. 
    Check that everything is consistent. Do the same for 3 SC Nodes.
"""

class MainchainSidechainNodeBlockGenerationTest(SidechainTestFramework):
    
    def add_options(self, parser):
        #empty implementation
        pass
    
    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, 3)
        
    def setup_network(self, split = False):
        print("Initializing Mainchain nodes...")
        self.nodes = self.setup_nodes()
        print("OK\n")
        print("Connecting mainchain nodes node0, node1 and node2...")
        connect_nodes_bi(self.nodes, 0, 1)
        connect_nodes_bi(self.nodes, 0, 2)
        connect_nodes_bi(self.nodes, 1, 2)
        print("OK\n")
        
    def setup_nodes(self):
        return start_nodes(3, self.options.tmpdir)
    
    def sc_setup_chain(self):
        initialize_default_sc_chain_clean(self.options.tmpdir, 3)
        
    def sc_setup_network(self, split = False):
        print("Initializing Sidechain nodes...")
        self.sc_nodes = self.sc_setup_nodes()
        print("OK\n")
        print("Connecting sidechain nodes node0, node1 and node2...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        connect_sc_nodes(self.sc_nodes[0], 2)
        connect_sc_nodes(self.sc_nodes[1], 2)
        self.sc_sync_all()
        print("OK\n")
    
    def sc_setup_nodes(self):
        return start_sc_nodes(3, self.options.tmpdir)
    
    def create_mc_tx(self, sender, receiver, amount):
        txid = sender.sendtoaddress(receiver, amount)
        print("--->MC Transaction ID: {0}".format(str(txid)))
        return txid
    
    def create_sc_tx(self, sender, receiver, amount, fee):
        j = {"amount": amount, "recipient": str(receiver), "fee": fee}
        txid = sender.wallet_transfer(json.dumps(j))["id"]
        print("--->SC Transaction ID: {0}".format(str(txid)))
        return txid
            
    def check_tx_in_mc_block(self, node, nodename, txid):
        block = node.getblock(node.getbestblockhash())
        assert_true(txid in block["tx"], "Transaction {0} not included in the new block for MC {1}".format(txid, nodename))
        
    def check_tx_in_sc_block(self, node, nodename, txid):
        tx_list = []
        for tx in node.debug_info()["bestBlock"]["transactions"]:
            tx_list.append(tx["id"])
        assert_true(txid in tx_list, "Transaction {0} not included in the new block for SC {1}".format(str(txid), nodename))
    
    def check_tx_in_mc_mempool(self, node, nodename, txid):
            assert_true(txid in node.getrawmempool(), "Transaction {0} not in mempool for MC {1}".format(txid, nodename))

    def check_tx_in_sc_mempool(self, node, nodename, txid):
        tx_list = []
        for tx in node.nodeView_pool()["transactions"]:
            tx_list.append(tx["id"])
        assert_true(txid in tx_list, "Transaction {0} not in mempool for SC {1}".format(str(txid), nodename))
    
    def run_test(self):
        #Generate 1-block coinbase for nodes 0 and 1 for simple test
        print("Generate 1-block coinbase for MC Nodes 0 and 1...")
        self.nodes[0].generate(1)
        self.sync_all()
        self.nodes[1].generate(1)
        self.sync_all()
        self.nodes[2].generate(100)
        self.sync_all()
        print("OK\n")
        
        #Saving MC Nodes information and setting tx amount
        print("Nodes initialization...")
        mcnode0name = "node0"
        mcnode1name = "node1"
        mcnode2name = "node2"
        mcnode1address = self.nodes[1].getnewaddress()
        mcnode0balance = self.nodes[0].getbalance()
        mcnode1balance = self.nodes[1].getbalance()
        print("-->MC Node 0 balance: {0}".format(mcnode0balance))
        print("-->MC Node 1 balance: {0}".format(mcnode1balance))
        mc_amount = Decimal("5.00000000")
        
        #Saving SC Nodes information and setting tx amount and fee
        scnode0name = "node0"
        scnode1name = "node1"
        scnode2name = "node2"
        scnode1address = self.sc_nodes[1].wallet_allPublicKeys()["result"]["propositions"][0]
        scnode0balance = int(self.sc_nodes[0].wallet_balance()["result"]["balance"])
        scnode1balance = int(self.sc_nodes[1].wallet_balance()["result"]["balance"])
        print("-->SC Node 0 balance: {0}".format(scnode0balance))
        print("-->SC Node 1 balance: {0}".format(scnode1balance))
        sc_amount = random.randint(1, scnode0balance - 100)
        sc_fee = random.randint(1, 99)
        print("OK\n")
        
        #MC Node0 sends tx to MC Node1. The same happens in SC side
        print("Sending transactions...")
        print("-->MC Node 0 sends to MC Node 1 address {0}, {1} coins...".format(str(mcnode1address), mc_amount))
        mctxid = self.create_mc_tx(self.nodes[0], mcnode1address, mc_amount)
        print("-->SC Node 0 sends to SC Node 1 address {0}, {1} coins with fee {2} coins...".format(str(scnode1address), sc_amount, sc_fee))
        sctxid = self.create_sc_tx(self.sc_nodes[0], scnode1address, sc_amount, sc_fee)
        print("OK\n")
        
        #Synchronizing MC nodes mempools and checks that the new transaction is in all of them. Do the same for SC nodes
        print("Synchronizing nodes' mempools and check tx is in all of them...")
        sync_mempools(self.nodes)
        self.check_tx_in_mc_mempool(self.nodes[0], mcnode0name, mctxid)
        self.check_tx_in_mc_mempool(self.nodes[1], mcnode1name, mctxid)
        self.check_tx_in_mc_mempool(self.nodes[2], mcnode2name, mctxid)
        sync_sc_mempools(self.sc_nodes)
        self.check_tx_in_sc_mempool(self.sc_nodes[0], scnode0name, sctxid)
        self.check_tx_in_sc_mempool(self.sc_nodes[1], scnode1name, sctxid)
        self.check_tx_in_sc_mempool(self.sc_nodes[2], scnode2name, sctxid)
        print("OK\n")
        
        #MC Node2 generate a new block, then sync all the nodes. Do the same for SC Node2.
        print("Generating new blocks...")
        print("-->MC Node 2 generates a block...")
        blocks = self.nodes[2].generate(1)
        assert_equal(len(blocks), 1, "MC Node2 couldn't generate a block")
        self.sync_all()
        print("-->SC Node 2 generates a block...")
        assert_equal("ok", str(self.sc_nodes[2].debug_startMining()["response"]), "SC Node 2 couldn't start mining")
        '''We wait for the new blocks to appear in Node 2 block count. This is not necessary in zend because the generate call is
        synchronous with respect to the effective block generation, while in Hybrid App, and also with our modification, it's not.'''
        wait_for_next_sc_blocks(self.sc_nodes[2], 4) 
        self.sc_sync_all()
        print("OK\n")
        
        #Checking that the tx is in the newly created block for both MC and SC
        print("Checking block inclusion...")
        self.check_tx_in_mc_block(self.nodes[0], mcnode0name, mctxid)
        self.check_tx_in_mc_block(self.nodes[1], mcnode1name, mctxid)
        self.check_tx_in_mc_block(self.nodes[2], mcnode2name, mctxid)
        self.check_tx_in_sc_block(self.sc_nodes[0], scnode0name, sctxid)
        self.check_tx_in_sc_block(self.sc_nodes[1], scnode1name, sctxid)
        self.check_tx_in_sc_block(self.sc_nodes[2], scnode2name, sctxid)
        print("OK\n")
        
        print("Checking mempools empty...")
        assert_equal(0, self.nodes[0].getmempoolinfo()["size"])
        assert_equal(0, self.nodes[1].getmempoolinfo()["size"])
        assert_equal(0, self.nodes[2].getmempoolinfo()["size"])
        assert_equal(0, self.sc_nodes[0].nodeView_pool()["size"])
        assert_equal(0, self.sc_nodes[1].nodeView_pool()["size"])
        assert_equal(0, self.sc_nodes[2].nodeView_pool()["size"])
        print("OK\n")
        
        #Checking that MC Node0 balance has decreased by amount-fee and that MC Node1 balance has increased of amount. Do the same for SC nodes 0 and 1
        print("Checking balance changed for MC & SC Nodes 0 and 1...")
        mcnode0newbalance = self.nodes[0].getbalance()
        mcnode1newbalance = self.nodes[1].getbalance()
        mc_fee = self.nodes[0].gettransaction(mctxid)["fee"]
        assert_equal(mcnode0balance - (mc_amount-mc_fee), mcnode0newbalance, "Coins sent/total amount mismatch for MC Node0")
        assert_equal(mcnode1balance + mc_amount, mcnode1newbalance, "Coins received/total amount mismatch for MC Node1")
        print("-->MC Node 0 new balance: {0}".format(mcnode0newbalance))
        print("-->MC Node 1 new balance: {0}".format(mcnode1newbalance))
        
        node0newbalance = int(self.sc_nodes[0].wallet_balances()["totalBalance"])
        node1newbalance = int(self.sc_nodes[1].wallet_balances()["totalBalance"])
        assert_equal(scnode0balance - (sc_amount+sc_fee), node0newbalance, "Coins sent/total sc_amount mismatch for Node0")
        assert_equal(scnode1balance + sc_amount, node1newbalance, "Coins received/total sc_amount mismatch for Node1")
        print("-->SC Node 0 new balance: {0}".format(node0newbalance))
        print("-->SC Node 1 new balance: {0}".format(node1newbalance))
        print("OK\n")
        
if __name__ == "__main__":
    MainchainSidechainNodeBlockGenerationTest().main()
    