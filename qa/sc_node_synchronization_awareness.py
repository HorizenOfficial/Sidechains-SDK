import time

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    sc_create_multiple_nodes_network_unconnected, generate_next_blocks, connect_sc_nodes, disconnect_sc_nodes
from test_framework.util import initialize_chain_clean, start_nodes, separator


class SCNodeSynchronizationAwareness(SidechainTestFramework):
    # *************************************** ATTRIBUTES ************************************************
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 3
    sc_nodes_bootstrap_info = None

    # *** MC ***
    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    # *** SC ***
    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        network = sc_create_multiple_nodes_network_unconnected(mc_node_1, self.number_of_sidechain_nodes)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    # *************************************** RUN TEST ************************************************
    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        print "Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(
            self.number_of_mc_nodes))
        print "Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(
            self.number_of_sidechain_nodes))

        separator("generate blocks node 0")
        generate_next_blocks(sc_nodes[0], "sc_node_0", 3)

        separator("connecting node 1 to node 0")
        connect_sc_nodes(sc_nodes[1], 0)

        time.sleep(10)

        separator("first call to api")
        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']
        print "status:" + status
        print "height:" + str(height)
        separator()

        separator("disconnecting from 1 to node 0")
        disconnect_sc_nodes(sc_nodes[1], 0)

        separator("node 0 going to generate 150 blocks" )
        generate_next_blocks(sc_nodes[0], "sc_node_0", 150)
        separator("node 0 generated 150 blocks...")

        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']
        print "status:" + status
        print "height:" + str(height)

        time.sleep(1)

        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']
        print "status:" + status
        print "height:" + str(height)

        time.sleep(1)

        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']
        print "status:" + status
        print "height:" + str(height)

        time.sleep(10)

        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']
        print "status:" + status
        print "height:" + str(height)

        stay = True
        while stay:
            typed = input("come on...")
            if str(typed) == "stop":
                stay = False
            else:
                stay = True


if __name__ == "__main__":
    SCNodeSynchronizationAwareness().main()
