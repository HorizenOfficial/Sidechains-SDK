import time

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, \
    sc_create_multiple_nodes_network_unconnected, generate_next_blocks, connect_sc_nodes, disconnect_sc_nodes, \
    assert_equal
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
        generate_next_blocks(sc_nodes[0], "sc_node_0", 350)

        # time.sleep(25)

        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']
        print "status:" + status
        print "height:" + str(height)
        time.sleep(2)

        separator("CONNECTING node 1 to node 0")
        connect_sc_nodes(sc_nodes[1], 0)
        time.sleep(5)

        separator("first call to api")
        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']

        print "status:" + status
        print "height:" + str(height)
        assert_equal(status, "Synchronizing", "status is not Synchronizing, but it should be")

        separator("Sleeping to make it finish")
        time.sleep(35)

        status = sc_nodes[1].node_syncStatus()['result']['syncStatus']['status']
        height = sc_nodes[1].node_syncStatus()['result']['syncStatus']['nodeHeight']

        print "status:" + status
        print "height:" + str(height)
        assert_equal(status, "Synchronized", "status is not Synchronized, but it should be")


if __name__ == "__main__":
    SCNodeSynchronizationAwareness().main()
