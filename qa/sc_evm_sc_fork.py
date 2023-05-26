#!/usr/bin/env python3

import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import assert_equal, assert_false


class SCEVMSCFork(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=80)

    def advance_to_epoch(self, sc_node, epoch_number):
        forging_info = sc_node.block_forgingInfo()
        current_epoch = forging_info["result"]["bestEpochNumber"]
        # make sure we are not already passed the desired epoch
        assert_false(current_epoch > epoch_number, "unexpected epoch number")
        while current_epoch < epoch_number:
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()
            forging_info = sc_node.block_forgingInfo()
            current_epoch = forging_info["result"]["bestEpochNumber"]

    def check_block_gas_limit(self, sc_node, expected_gas_limit):
        block = sc_node.rpc_eth_getBlockByNumber("latest", False)["result"]
        block_gas_limit = int(block["gasLimit"], 16)
        logging.info("block gas limit: {}".format(block_gas_limit))
        assert_equal(expected_gas_limit, block_gas_limit, "unexpected block gas limit")

    def run_test(self):
        sc_node = self.sc_nodes[0]

        # initially the block gas limit should default to 30 million
        self.check_block_gas_limit(sc_node, 30000000)

        # it should remain 30 million until right before the fork at epoch 4
        self.advance_to_epoch(sc_node, 3)
        self.check_block_gas_limit(sc_node, 30000000)

        # at epoch 4 the block gas limit should change to 20 million
        self.advance_to_epoch(sc_node, 4)
        self.check_block_gas_limit(sc_node, 20000000)

        # at epoch 5 the block gas limit should change to 25 million
        self.advance_to_epoch(sc_node, 5)
        self.check_block_gas_limit(sc_node, 25000000)

        # the block gas limit should remain unchanged now
        self.advance_to_epoch(sc_node, 6)
        self.check_block_gas_limit(sc_node, 25000000)


if __name__ == "__main__":
    SCEVMSCFork().main()
