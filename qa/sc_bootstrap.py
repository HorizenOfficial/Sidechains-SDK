#!/usr/bin/env python2
import json

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes
from SidechainTestFramework.scutil import check_mainchan_block_inclusion

"""
Check the bootstrap feature.

Configuration: bootstrap 3 SC Nodes and start them with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 3 SC nodes
    - Extract genesis info
    - Start SC nodes with that genesis info

Test:
    -for each SC node verify:
        - all keys/boxes/balances are coherent with the default initialization
        - verify MC block is included inside all 3 SC nodes
"""
class SCBootstrap(SidechainTestFramework):

    number_of_sidechains = 3

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir, extra_args=["-websocket"])

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            mc_node,
            SCCreationInfo("1".zfill(64), 100, 1000),
            MCConnectionInfo()
        )
        sc_node_2_configuration = SCNodeConfiguration(
            mc_node,
            SCCreationInfo("2".zfill(64), 250, 1000),
            MCConnectionInfo()
        )
        sc_node_3_configuration = SCNodeConfiguration(
            mc_node,
            SCCreationInfo("3".zfill(64), 450, 1000),
            MCConnectionInfo()
        )
        network = SCNetworkConfiguration(sc_node_1_configuration, sc_node_2_configuration, sc_node_3_configuration)
        self.bootstrap_sidechain_nodes(network)

    def sc_setup_network(self, split=False):
        # SC network setup
        self.sc_nodes = self.sc_setup_nodes(self.number_of_sidechains)

    def check_genesis_balances(self, sc_node, sidechain_id, expected_keys_count, expected_boxes_count, expected_wallet_balance):
        print("Genesis checks for sidechain id {0}.".format(sidechain_id))
        print("Checking that each public key has a box assigned with a non-zero value.")
        response = sc_node.wallet_allPublicKeys()
        public_keys = response["result"]["propositions"]
        response = sc_node.wallet_allBoxes()
        boxes = response["result"]["boxes"]
        response = sc_node.wallet_balance()
        balance = response["result"]
        assert_equal(expected_keys_count, len(public_keys), "Unexpected number of public keys")
        assert_equal(expected_boxes_count, len(boxes), "Unexpected number of boxes")
        for key in public_keys:
            target = None
            for box in boxes:
                if box["proposition"]["publicKey"] == key["publicKey"]:
                    target = box
                    assert_true(box["value"] > 0, "Non positive value for box: {0} with public key: {1}".format(box["id"], key))
                    break
            assert_true(target is not None, "Box related to public key: {0} not found".format(key))
        print("Checking genesis balance.")
        assert_equal(expected_wallet_balance, int(balance["balance"]), "Unexpected balance")
        print("Total balance: {0}".format(json.dumps(balance["balance"])))

    def run_test(self):
        mc_nodes = self.nodes
        print "Number of MC nodes: {0}".format(len(mc_nodes))
        assert_equal(1, len(mc_nodes), "The number of MC nodes is grater than 1.")

        sc_nodes_info = self.sc_nodes_bootstrap_info
        assert_equal(self.number_of_sidechains, len(sc_nodes_info), "Not all sidechains have been successfully created.")
        print sc_nodes_info

        # Check validity of genesis information
        for i in range(self.number_of_sidechains):
            node = self.sc_nodes[i]
            node_info = sc_nodes_info[i]
            mc_block = mc_nodes[0].getblock(str(node_info.mainchain_block_height))
            # check all keys/boxes/balances are coherent with the default initialization
            self.check_genesis_balances(node, node_info.sidechain_id, 1, 1, node_info.wallet_balance*100000000)
            # verify MC block reference's inclusion
            check_mainchan_block_inclusion(node, node_info.sidechain_id, 1, 0, mc_block, [node_info.genesis_account[1]], [node_info.wallet_balance], True)


if __name__ == "__main__":
    SCBootstrap().main()
