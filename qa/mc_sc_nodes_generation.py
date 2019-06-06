#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from util import assert_equal, assert_true, initialize_chain, start_nodes, connect_nodes_bi, sync_mempools, sync_blocks
from SidechainTestFramework.scutil import initialize_sc_chain_clean, start_sc_nodes, connect_sc_nodes, sync_sc_mempools, sync_sc_blocks, \
                                          wait_for_next_sc_blocks
import json
import random
import shutil
from decimal import Decimal

class MainchainSidechainNodeBlockGenerationTest(SidechainTestFramework):
    
    def add_options(self, parser):
        #empty implementation
        pass
    
    def setup_chain(self):
        initialize_chain(self.options.tmpdir)
        shutil.rmtree("./cache")
        
    def setup_network(self, split = False):
        self.nodes = self.setup_nodes()
        print("Connecting mainchain nodes node0, node1 and node2...")
        connect_nodes_bi(self.nodes, 0, 1)
        connect_nodes_bi(self.nodes, 0, 2)
        connect_nodes_bi(self.nodes, 1, 2)
        #Generate immature blocks...
        for i in range(0, 3):
            self.nodes[i].generate(25)
        self.sync_all()
        
    def setup_nodes(self):
        return start_nodes(3, self.options.tmpdir)
    
    def sc_setup_chain(self):
        initialize_sc_chain_clean(self.options.tmpdir, 3, None)
        
    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        print("Connecting sidechain nodes node0, node1 and node2...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        connect_sc_nodes(self.sc_nodes[0], 2)
        connect_sc_nodes(self.sc_nodes[1], 2)
    
    def sc_setup_nodes(self):
        return start_sc_nodes(3, self.options.tmpdir)
    
    def create_tx(self, sender, receiver, amount, fee, mainchain = True):
        txid = None
        if mainchain:
            txid = sender.sendtoaddress(receiver, amount)
            print("--->MC Transaction ID: {0}".format(str(txid)))
        else:
            j = {"amount": amount, "recipient": str(receiver), "fee": fee}
            txid = sender.wallet_transfer(json.dumps(j))["id"]
            print("--->SC Transaction ID: {0}".format(str(txid)))
        return txid
            
    def check_tx_in_block(self, node, nodename, txid, mainchain = True):
        if mainchain:
            block = node.getblock(node.getbestblockhash())
            assert_true(txid in block["tx"], "Transaction {0} not included in the new block for MC {1}".format(txid, nodename))
        else:
            tx_list = []
            for tx in node.debug_info()["bestBlock"]["transactions"]:
                tx_list.append(tx["id"])
            assert_true(txid in tx_list, "Transaction {0} not included in the new block for SC {1}".format(str(txid), nodename))
    
    def check_tx_in_mempool(self, node, nodename, txid, mainchain = True):
        tx_list = []
        if mainchain:
            assert_true(txid in node.getrawmempool(), "Transaction {0} not in mempool for MC {1}".format(txid, nodename))
        else:
            for tx in node.nodeView_pool()["transactions"]:
                tx_list.append(tx["id"])
            assert_true(txid in tx_list, "Transaction {0} not in mempool for SC {1}".format(str(txid), nodename))
    
    def run_test(self):
        print("Nodes initialization...")
        mcnode0name = "node0"
        mcnode1name = "node1"
        mcnode2name = "node2"
        mcnode1address = self.nodes[1].getnewaddress()
        self.nodes[2].getnewaddress()
        mcnode0balance = self.nodes[0].getbalance()
        mcnode1balance = self.nodes[1].getbalance()
        mcnode2balance = self.nodes[2].getbalance()
        print("-->MC Node 0 balance: {0}".format(mcnode0balance))
        print("-->MC Node 1 balance: {0}".format(mcnode1balance))
        print("-->MC Node 2 balance: {0}".format(mcnode2balance))
        mc_amount = random.randint(1, round(mcnode0balance))
        
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
        
        print("Sending transactions...")
        print("-->MC Node 0 sends to MC Node 1 address {0}, {1} coins...".format(str(mcnode1address), mc_amount))
        mctxid = self.create_tx(self.nodes[0], mcnode1address, mc_amount, 0)
        print("-->SC Node 0 sends to SC Node 1 address {0}, {1} coins with fee {2} coins...".format(str(scnode1address), sc_amount, sc_fee))
        sctxid = self.create_tx(self.sc_nodes[0], scnode1address, sc_amount, sc_fee, mainchain = False)
        print("OK\n")
        
        print("Synchronizing nodes' mempools and check tx is in all of them...")
        sync_mempools(self.nodes)
        self.check_tx_in_mempool(self.nodes[0], mcnode0name, mctxid)
        self.check_tx_in_mempool(self.nodes[1], mcnode1name, mctxid)
        self.check_tx_in_mempool(self.nodes[2], mcnode2name, mctxid)
        sync_sc_mempools(self.sc_nodes)
        self.check_tx_in_mempool(self.sc_nodes[0], scnode0name, sctxid, mainchain = False)
        self.check_tx_in_mempool(self.sc_nodes[1], scnode1name, sctxid, mainchain = False)
        self.check_tx_in_mempool(self.sc_nodes[2], scnode2name, sctxid, mainchain = False)
        print("OK\n")
        
        print("Generating new blocks...")
        print("-->MC Node 2 generates a block...")
        blocks = self.nodes[2].generate(1)
        assert_equal(len(blocks), 1, "MC Node2 couldn't generate a block")
        self.sync_all()
        print("-->SC Node 2 generates a block...")
        assert_equal(str(self.sc_nodes[2].debug_startMining()["response"]), "ok", "SC Node 2 couldn't start mining")
        wait_for_next_sc_blocks(self.sc_nodes[2], 2, wait = 2)
        self.sc_sync_all()
        print("OK\n")
        
        print("Checking block inclusion...")
        self.check_tx_in_block(self.nodes[0], mcnode0name, mctxid)
        self.check_tx_in_block(self.nodes[1], mcnode1name, mctxid)
        self.check_tx_in_block(self.nodes[2], mcnode2name, mctxid)
        self.check_tx_in_block(self.sc_nodes[0], scnode0name, sctxid, mainchain = False)
        self.check_tx_in_block(self.sc_nodes[1], scnode1name, sctxid, mainchain = False)
        self.check_tx_in_block(self.sc_nodes[2], scnode2name, sctxid, mainchain = False)
        print("OK\n")
        
        print("Checking mempools empty...")
        assert_equal(self.nodes[0].getmempoolinfo()["size"], 0)
        assert_equal(self.nodes[1].getmempoolinfo()["size"], 0)
        assert_equal(self.nodes[2].getmempoolinfo()["size"], 0)
        assert_equal(self.sc_nodes[0].nodeView_pool()["size"], 0)
        assert_equal(self.sc_nodes[1].nodeView_pool()["size"], 0)
        assert_equal(self.sc_nodes[2].nodeView_pool()["size"], 0)
        print("OK\n")
        
        print("Checking balance changed...")
        mcreward = Decimal("8.75000000")
        mcnode0newbalance = self.nodes[0].getbalance()
        mcnode1newbalance = self.nodes[1].getbalance()
        mcnode2newbalance = self.nodes[2].getbalance()
        mc_fee = self.nodes[0].gettransaction(mctxid)["fee"]
        assert_equal(mcnode0newbalance, mcnode0balance - (mc_amount-mc_fee), "Coins sent/total amount mismatch for MC Node0")
        assert_equal(mcnode1newbalance, mcnode1balance + mc_amount, "Coins received/total amount mismatch for MC Node1")
        assert_equal(mcnode2newbalance, mcnode2balance + mcreward, "Coins received/total amount mismatch for MC Node2")
        print("-->MC Node 0 new balance: {0}".format(mcnode0newbalance))
        print("-->MC Node 1 new balance: {0}".format(mcnode1newbalance))
        print("-->MC Node 2 new balance: {0}".format(mcnode2newbalance))
        scblockreward = 1
        node0newbalance = int(self.sc_nodes[0].wallet_balances()["totalBalance"])
        node1newbalance = int(self.sc_nodes[1].wallet_balances()["totalBalance"])
        node2newbalance = int(self.sc_nodes[2].wallet_balances()["totalBalance"])
        assert_equal(node0newbalance, scnode0balance - (sc_amount+sc_fee), "Coins sent/total sc_amount mismatch for Node0")
        #Bug in HybridApp, using transfer api call, the sc_fee actually goes to the receiver too even if it's not the miner/forger
        assert_equal(node1newbalance, scnode1balance + sc_amount, "Coins received/total sc_amount mismatch for Node1") #Expect to fail
        assert_equal(node2newbalance, scnode2balance + sc_fee + blockreward, "Coins received/total sc_amount mismatch for Node2")
        print("-->Node 0 new balance: {0}".format(node0newbalance))
        print("-->Node 1 new balance: {0}".format(node1newbalance))
        print("-->Node 2 new balance: {0}".format(node2newbalance))
        print("OK\n")
        
if __name__ == "__main__":
    MainchainSidechainNodeBlockGenerationTest().main()
    