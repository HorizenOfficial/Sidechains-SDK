#!/usr/bin/env python3
import json
import pprint

import test_framework.util
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, Account, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_equal, assert_true, start_nodes, \
    websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, is_mainchain_block_included_in_sc_block, check_box_balance, \
    check_mainchain_block_reference_info, check_wallet_coins_balance, check_box_balance, get_lib_separator, \
    AccountModelBlockVersion, EVM_APP_BINARY, generate_next_blocks, generate_next_block

"""
Check the EVM bootstrap feature.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    For the SC node:
        - verify the MC block is included
"""
class SCEvmBootstrap(SidechainTestFramework):

    sc_nodes_bootstrap_info=None

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        print("...skip sync since it would timeout as of now")
        #self.sc_sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH), sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, block_timestamp_rewind=720*120*5, blockversion=AccountModelBlockVersion)


    def sc_setup_nodes(self):
        return start_sc_nodes(num_nodes=1, dirname=self.options.tmpdir, binary=[EVM_APP_BINARY])#, extra_args=['-agentlib'])

    def run_test(self):
        sc_node = self.sc_nodes[0]
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = self.nodes[0].getblock(mc_block["hash"], False)
        print("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node.block_best()["result"]

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        #input("\n\t======> Enter any input to continue generating a new sc block...")
        generate_next_blocks(sc_node, "first node", 1)[0]
        self.sc_sync_all()
        sc_best_block = sc_node.block_best()["result"]
        assert_equal(sc_best_block["height"], 2, "The best block has not the specified height.")

        #input("\n\t======> Enter any input to continue generating blocks till next consensus epoch...")
        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        #input("\n\t======> Enter any input to continue generating blocks till next consensus epoch...")
        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        sc_best_block = sc_node.block_best()["result"]
        pprint.pprint(sc_best_block)

        # send an eth tx to mempool
        amount = 1000
        j = {
            "to": "abcdeabcdeabcdeabcdeabcdeabcdeabcdeabcde", # must be 20 bytes
            "value": amount
        }
        request = json.dumps(j)
        response = sc_node.transaction_sendCoinsToAddress(request)
        print("tx sent:")
        pprint.pprint(response)

        # get mempool contents
        response = sc_node.transaction_allTransactions()
        print("mempool contents:")
        pprint.pprint(response)

        # request chainId via rpc route
        print("rpc response:")
        pprint.pprint(sc_node.rpc_eth_chainId())

        # request getBalance via rpc route
        print("rpc response:")
        pprint.pprint(sc_node.rpc_eth_getBalance("0x0", "1"))



if __name__ == "__main__":
    SCEvmBootstrap().main()
