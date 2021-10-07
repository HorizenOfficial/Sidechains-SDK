#!/usr/bin/env python2
import json
import time
from string import upper

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SCMultiNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.sidechainauthproxy import SidechainAuthServiceProxy
from test_framework.util import assert_equal, initialize_chain_clean, start_nodes, \
    websocket_port_by_mc_node_index, connect_nodes_bi, assert_true, assert_false,assert_not_equal
from SidechainTestFramework.scutil import check_box_balance, connect_sc_nodes, \
    bootstrap_sidechain_nodes, start_sc_nodes, is_mainchain_block_included_in_sc_block, generate_next_blocks, \
    check_mainchain_block_reference_info, check_wallet_coins_balance, get_known_peers, sc_p2p_port, stop_sc_node, \
    start_sc_node, TimeoutException, WAIT_CONST

"""
Check that a node could response to api calls though if it is Syncronizing

Configuration: start 2 sidechain nodes and 1 Mainchain node, from which the sidechain is bootstrapped.

    Steps:
        - make  node 1 create 10000 blocks
        - make node 2 connectTo the node 1 ( it should start to Synchronize right after)



Test:
    - verify that:
        - node responses to API cll block/bestBlock
"""


def separator(name="", timeForWatching=0):
    if len(name) > 0:
        print "\n************************ " + upper(name) + " *****************************\n"
    else:
        print "\n*****************************************************\n"
    if (time > 0):
        time.sleep(timeForWatching)


def print_peers_addresses(node):
    for peer in node.node_connectedPeers()["result"]["peers"]:
        print peer['address'] + " - " + peer['connectionType']


def print_peers_connections(sc_nodes):
    print "\n"
    for i in range(len(sc_nodes)):
        print "\n****** " + str(i) + " ******"
        print "node " + "127.0.0.1:" + str(sc_p2p_port(i)) + " peers:"
        print_peers_addresses(sc_nodes[i])
    return ""


def sc_create_multiple_not_connected_nodes_network(mc_node_1, num_of_nodes_to_start):
    nodes_config = []
    for i in range(num_of_nodes_to_start):
        a_config = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            known_peers=[]
        )
        nodes_config.append(a_config)
    nodes_tuple = tuple(nodes_config)
    network = SCMultiNetworkConfiguration(SCCreationInfo(mc_node_1, 600, 1000), nodes_tuple)
    return network


def sync_sc_blocks(api_connections, wait_for=25, p=False):
    """
    Wait for maximum wait_for seconds for everybody to have the same block count
    """
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            separator("Exception coming...")
            print ("HERE I'm Raising the F***** Exception")
            raise TimeoutException("Syncing blocks")
        counts = [int(x.block_best()["result"]["height"]) for x in api_connections]
        if p:
            print (counts)
        if counts == [counts[0]] * len(counts):
            break
        time.sleep(WAIT_CONST)


# not using cache cause it need to be stressed
def fibonacci_of(n):
    if n in {0, 1}:  # Base case
        return n
    return fibonacci_of(n - 1) + fibonacci_of(n - 2)


FIB_WEIGHT = 23000


# depending on the computer power
def calculate_blocks_to_forge():
    return 700
    """
    start = time.time()
    numb = fibonacci_of(36)
    end = time.time()
    elapsed = end - start
    separator("fibonacci time")
    print (str(elapsed) + " s")
    power = int(FIB_WEIGHT / elapsed)
    print ("blocks: " + str(power) + " - " + " weight = " + str(FIB_WEIGHT))
    input("shall we go?")
    separator()
    return int(power)
    """


# **********************************************************************************************
# *************************************** CLASS ************************************************
# **********************************************************************************************


class SCNodeResponseAlongSync(SidechainTestFramework):
    # *************************************** ATTRIBUTES ************************************************
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2
    sc_nodes_bootstrap_info = None

    # *************************************** SETUP ************************************************

    # *** MC ***
    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    # *** SC ***
    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]

        # *********** multiple not connected nodes network ***********
        network = sc_create_multiple_not_connected_nodes_network(mc_node_1, self.number_of_sidechain_nodes)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network, 720 * 120 * 5)
        # self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options.tmpdir, network)

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

        node1 = self.sc_nodes[0]
        node1Name = "node1"
        node2 = self.sc_nodes[1]
        node2Name = "node2"

        blocks_numb = calculate_blocks_to_forge()
        separator()
        print "gonna forge " + str(blocks_numb) + " blocks"
        separator("", 2)
        # time.sleep(2)
        blocks = generate_next_blocks(node1, node1Name, blocks_numb)
        # ablock = generate_next_blocks(node2, node2Name, 1) # can't generate
        best1 = (node1.block_best()['result']['block']['id'])
        best2_0 = (node2.block_best()['result']['block']['id'])
        separator("best 1 - best 2_0", 2)
        print best1
        print best2_0
        separator()

        assert_not_equal(best1, best2_0, "They are same already, quite weird")

        separator("connecting the two nodes...", 2)
        connect_sc_nodes(node1, 1)

        """
        start = time.time()
        sync_sc_blocks(self.sc_nodes, 200, True)
        end = time.time()
        separator("time elapsed")
        print str(end - start)
        separator()
        """
        separator("sleeping 5 seconds", 5)
        # time.sleep(4)

        best2_1 = (node2.block_best()['result']['block']['id'])
        separator("MIDDLE CHECK")
        print("best 1:")
        print best1
        print ("best 2_0:")
        print best2_0
        print ("best 2_1:")
        print best2_1
        separator()

        assert_not_equal(best2_1, best2_0, "Sync has not started")
        assert_not_equal(best2_1, best1,
                    " Too fast to synchronize, thy are already sync")  # try to add more block forging??

        separator("middle check passed", 1)

        separator("PEERS CONNECTIONS")
        print_peers_connections(sc_nodes)
        separator("*****************", 1)
        sync_sc_blocks(self.sc_nodes, 200, True)





        best2_2 = (node2.block_best()['result']['block']['id'])
        separator("FINAL CHECK")
        print  ("best 1")
        print best1
        print("best 1_1:")
        print (node1.block_best()['result']['block']['id'])
        print ("best 2_2:")
        print best2_2

        assert_equal(best1, best2_2, "best 1 should be like best 2 at the end of synchronization")

        separator("final check passed", 1)


if __name__ == "__main__":
    SCNodeResponseAlongSync().main()
