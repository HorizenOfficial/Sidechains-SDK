#!/usr/bin/env python3

import logging

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.scutil import EVM_APP_SLOT_TIME, SLOTS_IN_EPOCH, generate_next_block
from test_framework.util import assert_equal, assert_false, fail


class SCEVMSCFork(AccountChainSetup):

    def __init__(self):
        # rewind genesis block by 20 epochs, so we jump ahead for testing purposes
        super().__init__(withdrawalEpochLength=80, block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 20)

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

    def assert_block_gas_limit(self, sc_node, expected_gas_limit):
        block = sc_node.rpc_eth_getBlockByNumber("latest", False)["result"]
        block_gas_limit = int(block["gasLimit"], 16)
        logging.info("block gas limit: {}".format(block_gas_limit))
        assert_equal(expected_gas_limit, block_gas_limit, "unexpected block gas limit")

    def deploy_with_gas_limit(self, sc_node, gas_limit, should_fail):
        smart_contract = SmartContract('TestERC20')
        try:
            smart_contract.deploy(sc_node, fromAddress=self.evm_address, gasLimit=gas_limit)
        except Exception as err:
            if should_fail:
                logging.info("invalid TX as expected: {}".format(err))
            else:
                fail("TX should be valid: {}".format(err))
        else:
            if should_fail:
                fail("TX should be invalid")

    def run_test(self):
        self.sc_ac_setup()

        sc_node = self.sc_nodes[0]

        # initially the block gas limit should default to 30 million
        self.assert_block_gas_limit(sc_node, 30000000)

        # it should remain 30 million until right before the fork at epoch 4
        self.advance_to_epoch(sc_node, 3)
        self.assert_block_gas_limit(sc_node, 30000000)

        # at epoch 4 the block gas limit should change to 20 million
        self.advance_to_epoch(sc_node, 4)
        self.assert_block_gas_limit(sc_node, 20000000)

        # submit TX with gas limit > block gas limit: should be rejected as invalid
        self.deploy_with_gas_limit(sc_node, 22000000, True)

        # at epoch 5 the block gas limit should change to 25 million
        self.advance_to_epoch(sc_node, 15)
        self.assert_block_gas_limit(sc_node, 25000000)

        # same TX as before, but now it should be valid as the block gas limit was increased
        self.deploy_with_gas_limit(sc_node, 22000000, False)

        # the block gas limit should remain unchanged now
        self.advance_to_epoch(sc_node, 16)
        self.assert_block_gas_limit(sc_node, 25000000)


if __name__ == "__main__":
    SCEVMSCFork().main()
