#!/usr/bin/env python2
import json

from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, initialize_chain_clean, start_nodes, websocket_port
from SidechainTestFramework.scutil import create_websocket_configuration, check_mainchan_block_inclusion

class MCSCConnectedNodes(SidechainTestFramework):

    number_of_mc_nodes = 2
    number_of_sidechains = 3
    ws_port_mc_0 = websocket_port(0)
    ws_port_mc_1 = websocket_port(1)
    mainchain_websocket_confs = {
        0: ws_port_mc_0,
        1: ws_port_mc_1
    }

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes, self.mainchain_websocket_confs)

    def setup_network(self, split = False):
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_chain(self):
        network = {
            self.nodes[0]:{
                0: [1, 1000, create_websocket_configuration("ws://localhost:{0}".format(self.ws_port_mc_0))],
                1: [2, 1000, create_websocket_configuration("ws://localhost:{0}".format(self.ws_port_mc_0))],
            },
            self.nodes[1]:{
                0: [3, 1000, create_websocket_configuration("ws://localhost:{0}".format(self.ws_port_mc_1))]
            }
        }
        self.bootstrap_sidechain(network)

    def sc_setup_network(self, split=False):
        # SC network setup
        self.sc_nodes = self.sc_setup_nodes(self.number_of_sidechains)

    def run_test(self):
        mc_nodes = self.nodes
        sc_nodes = self.sc_nodes
        assert_equal(self.number_of_mc_nodes, len(mc_nodes))
        assert_equal(self.number_of_sidechains, len(sc_nodes))
        print "Number of started mc nodes: {0}".format(len(mc_nodes), "The number of MC nodes is not {0}.".format(self.number_of_mc_nodes))
        print "Number of started sc nodes: {0}".format(len(sc_nodes), "The number of SC nodes is not {0}.".format(self.number_of_sidechains))

        mc_0 = mc_nodes[0]
        mc_1 = mc_nodes[1]

        sc_0 = sc_nodes[0]
        sc_1 = sc_nodes[1]
        sc_2 = sc_nodes[2]

        sc_nodes_info = self.sc_nodes_bootstrap_info
        assert_equal(self.number_of_sidechains, len(sc_nodes_info), "Not all sidechains have been successfully created.")
        print sc_nodes_info

        sc_info_0 = sc_nodes_info[0]
        sc_info_1 = sc_nodes_info[1]
        sc_info_2 = sc_nodes_info[2]

        # check mainchain block inclusion for sidechain nodes 1 and 2
        check_mainchan_block_inclusion(sc_0, sc_info_0[0], 1, 0, mc_0.getblock(str(sc_info_0[3])), sc_info_0[1], True)
        check_mainchan_block_inclusion(sc_1, sc_info_1[0], 1, 0, mc_0.getblock(str(sc_info_1[3])), sc_info_1[1], True)

        # check mainchain block inclusion for sidechain node 3
        check_mainchan_block_inclusion(sc_2, sc_info_2[0], 1, 0, mc_1.getblock(str(sc_info_2[3])), sc_info_2[1], True)

        block_hash = mc_0.generate(1)
        mc_0_new_block = mc_0.getblock(block_hash[0])
        sc_0.block_generate(json.dumps({"number":1}))
        sc_1.block_generate(json.dumps({"number":1}))

        check_mainchan_block_inclusion(sc_0, sc_info_0[0], 2, 1, mc_0_new_block, sc_info_0[1], False)
        check_mainchan_block_inclusion(sc_1, sc_info_1[0], 2, 0, mc_0_new_block, sc_info_1[1], False)

        try:
            check_mainchan_block_inclusion(sc_2, sc_info_2[0], 1, 0, mc_0_new_block, sc_info_2[1], False)
            # SC node 2 should not to know information about block generated from MC node 0
            # Therefore the test must to fail
            assert_equal(1, 2)
        except AssertionError:
            print "SC node 2 doesn't include block generated from MC node 0."
            block_hash = mc_1.generate(1)
            mc_1_new_block = mc_1.getblock(block_hash[0])
            sc_2.block_generate(json.dumps({"number":1}))
            check_mainchan_block_inclusion(sc_2, sc_info_2[0], 2, 0, mc_1_new_block, sc_info_2[1], False)
            try:
                check_mainchan_block_inclusion(sc_0, sc_info_0[0], 2, 1, mc_1_new_block, sc_info_0[1], False)
                check_mainchan_block_inclusion(sc_1, sc_info_1[0], 2, 0, mc_1_new_block, sc_info_1[1], False)
                # SC nodes 0 and 1 should not to know information about block generated from MC node 1
                # Therefore the test must to fail
                assert_equal(1, 2)
            except AssertionError:
                print "SC nodes 0 and 1 don't include block generated from MC node 1."
                # Here the test must to success
                assert_equal(1, 1)



if __name__ == "__main__":
    MCSCConnectedNodes().main()