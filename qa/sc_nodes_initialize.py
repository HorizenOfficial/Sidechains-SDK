#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from util import assert_true, assert_equal
from SidechainTestFramework.scutil import connect_sc_nodes, sc_p2p_port, initialize_sc_chain_clean, start_sc_nodes, wait_for_next_sc_blocks
import time
import json

"""
    Setup 3 SC Nodes and connect them togheter. Check that each node is connected to the other and that their initial keys/boxes/balances are
    coherent with the default initialization
"""

class SidechainNodesInitializationTest(SidechainTestFramework):
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
        
    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        #Connect nodes toghether
        print("Connecting node0, node1 and node2...")
        connect_sc_nodes(self.sc_nodes[0], 1) #In Scorex, it is just needed to call connect on one of the two
        connect_sc_nodes(self.sc_nodes[1], 2)
        connect_sc_nodes(self.sc_nodes[0], 2)
        self.sc_sync_all()
    
    def sc_setup_nodes(self):
        return start_sc_nodes(3, self.options.tmpdir)
    
    def check_connections(self, node, nodename, other_nodes):
        print("Checking connections for {0}...".format(nodename))
        peers_node = []
        for peer in node.peers_connected():
            peers_node.append(peer["name"])
        print("-->Peers connected to {0}: {1}".format(nodename, json.dumps(peers_node)))
        for other_node in other_nodes:
            assert_true(other_node in peers_node, "{0} not connected to {1}".format(other_node, nodename))
        print("OK\n")
        
    def check_genesis_balances(self, node, nodename, nodenumber, expected_keys_boxes):
        print("Genesis checks for {0}...".format(nodename))
        print("-->Checking that each public key has a box assigned with a non-zero value... ")
        balances = node.wallet_balances()
        public_keys = balances["publicKeys"]
        boxes = balances["boxes"]
        assert_equal(expected_keys_boxes, len(public_keys), "Unexpected number of public keys")
        assert_equal(expected_keys_boxes, len(boxes), "Unexpected number of boxes")
        for key in public_keys:
            target = None
            for box in boxes:
                if box["publicKey"] == key:
                    target = box
                    assert_true(box["value"] > 0, "Non positive value for box: {0} with public key: {1}".format(box["id"], key))
                    break
            assert_true(target is not None, "Box related to public key: {0} not found".format(key))
        print("-->Checking genesis balance...")
        assert_equal(int(expected_keys_boxes*10e7), int(balances["totalBalance"]), "Unexpected balance")
        print("-->Total balance: {0}".format(json.dumps(balances["totalBalance"])))
        print("OK\n")
    
    def run_test(self):
        node0name = "node0"
        node1name = "node1"
        node2name = "node2"
        
        #Check that each node is connected to all the others
        self.check_connections(self.sc_nodes[0], node0name, [node1name, node2name])
        self.check_connections(self.sc_nodes[1], node1name, [node0name, node2name])
        self.check_connections(self.sc_nodes[2], node2name, [node0name, node1name])
        
        #Check default initialization success. That is: check that each public key of each node has a box and a positive balance associated with it
        self.check_genesis_balances(self.sc_nodes[0], node0name, 0, 20)
        self.check_genesis_balances(self.sc_nodes[1], node1name, 1, 20)
        self.check_genesis_balances(self.sc_nodes[2], node2name, 2, 10)
        
if __name__ == "__main__":
    SidechainNodesInitializationTest().main()