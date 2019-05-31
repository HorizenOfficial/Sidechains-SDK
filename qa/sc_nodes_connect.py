from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from util import assert_true, assert_equal
from SidechainTestFramework.scutil import connect_sc_nodes, sc_p2p_port, initialize_sc_chain_clean, start_sc_nodes, getGenesisAddresses
import time

class SidechainNodesConnectionTest(SidechainTestFramework):
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
    
    def generateGenesisAddresses(self, client, nodeNumber):
        try:
            for i in range(0, getGenesisAddresses(nodeNumber)):
                client.wallet_generateSecret()
            return True
        except Exception as e:
            return False
    
    def check_connections(self, node, nodename, other_nodes):
        print("Checking connections for {0}...".format(nodename))
        peers_node = []
        for peer in node.peers_connected():
            peers_node.append(peer["name"])
        print("Peers connected to {0}: ".format(nodename), peers_node)
        for other_node in other_nodes:
            assert_true(other_node in peers_node, "{0} not connected to {1}".format(other_node, nodename))
        print("OK")
        
    def check_balances(self, node, nodename, nodenumber):
        print("Generating genesis addresses for {0}...".format(nodename))
        assert_true(self.generateGenesisAddresses(node, nodenumber))
        print("Checking that each public key has a box assigned with a non-zero value... ")
        balances = node.wallet_balances()
        public_keys = balances["publicKeys"]
        boxes = balances["boxes"]
        assert_equal(len(public_keys), len(boxes), "Number of public keys/boxes mismatch")
        for key in public_keys:
            target = None
            for box in boxes:
                if box["publicKey"] == key:
                    target = box
                    assert_true(box["value"] > 0, "Non positive value for box: {0} with public key: {1}".format(box["id"], key))
                    break
            assert_true(target is not None, "Box related to public key: {0} not found".format(key))
        print("OK")
        print("Total balance: ", balances["totalBalance"])
    
    def run_test(self):
        print("Connecting node0, node1 and node2...")
        connect_sc_nodes(self.sc_nodes[0], 1) #In Scorex, it is just needed to call connect on one of the two
        connect_sc_nodes(self.sc_nodes[1], 2)
        connect_sc_nodes(self.sc_nodes[0], 2)
        time.sleep(5)
        node0name = "node0"
        node1name = "node1"
        node2name = "node2"
        
        self.check_connections(self.sc_nodes[0], node0name, [node1name, node2name])
        self.check_connections(self.sc_nodes[1], node1name, [node0name, node2name])
        self.check_connections(self.sc_nodes[2], node2name, [node0name, node1name])
        
        self.check_balances(self.sc_nodes[0], node0name, 0)
        self.check_balances(self.sc_nodes[1], node1name, 1)
        self.check_balances(self.sc_nodes[2], node2name, 2)

        
if __name__ == "__main__":
    SidechainNodesConnectionTest().main()