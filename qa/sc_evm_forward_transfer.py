#!/usr/bin/env python3
import json
import logging

from eth_utils import add_0x_prefix, to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_eoa
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei, NULL_ADDRESS
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import assert_equal, forward_transfer_to_sidechain, assert_true

"""
Check forward transfer to EOA account.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Execute forward transfer to an EOA
    - Verify account balance
    - Verify forward transfer
"""


class SCEvmForwardTransfer(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=20)

    def run_test(self):
        sc_node = self.sc_nodes[0]

        self.sc_ac_setup()

        ft_amount_in_zennies = convertZenToZennies(self.ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        # verify forward transfer was received
        balance = sc_node.rpc_eth_getBalance(self.evm_address, "latest")
        logging.info(balance)
        assert_equal(hex(ft_amount_in_wei), balance["result"], "FT to EOA failed")

        # verify forward transfer is contained in block and contains given value and to address via rpc
        # Try with block hash
        forward_transfer = sc_node.rpc_zen_getForwardTransfers(add_0x_prefix(self.block_id))['result']['forwardTransfers'][0]
        assert_equal(hex(ft_amount_in_wei), forward_transfer['value'])
        assert_equal(self.evm_address.lower(), forward_transfer['to'])

        # Try with block number
        block_number = sc_node.block_best()["result"]["height"]
        forward_transfer = sc_node.rpc_zen_getForwardTransfers(block_number)['result']['forwardTransfers'][0]
        assert_equal(hex(ft_amount_in_wei), forward_transfer['value'])
        assert_equal(self.evm_address.lower(), forward_transfer['to'])

        # Try with tag
        result = sc_node.rpc_zen_getForwardTransfers("latest")
        assert_true("error" in result, "rpc_zen_getForwardTransfers should fail when using tag parameter")
        assert_true("Invalid block input parameter" in result["error"]["message"], "Wrong error")

        # verify forward transfer is contained in block and contains given value and to address via api
        j = {
            "blockId": self.block_id
        }
        request = json.dumps(j)
        forward_transfer = sc_node.block_getForwardTransfers(request)['result']['forwardTransfers'][0]
        assert_equal(ft_amount_in_wei, int(forward_transfer['value']))
        assert_equal(format_eoa(self.evm_address), forward_transfer['to'])


if __name__ == "__main__":
    SCEvmForwardTransfer().main()
