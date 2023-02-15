#!/usr/bin/env python3
import json
import logging

from eth_utils import to_checksum_address
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_eoa
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block
from httpCalls.transaction.allTransactions import allTransactions
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei, NULL_ADDRESS
from test_framework.util import assert_equal, forward_transfer_to_sidechain

"""
Check that forward transfer to non-EOA account does not change balance.

Configuration: bootstrap 1 SC node and start it with genesis info extracted from a mainchain node.
    - Mine some blocks to reach hard fork
    - Create 1 SC node
    - Extract genesis info
    - Start SC node with that genesis info

Test:
    - Execute forward transfer to an EOA
    - Verify account balance
    - Verify forward transfer
    - Deploy Smart Contract
    - Execute forward transfer to the address of the smart contract
    - Verify balance of the smart contract account has not changed
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
        forward_transfer = sc_node.rpc_zen_getForwardTransfers("latest")['result']['forwardTransfers'][0]
        assert_equal(hex(ft_amount_in_wei), forward_transfer['value'])
        assert_equal(self.evm_address.lower(), forward_transfer['to'])

        # verify forward transfer is contained in block and contains given value and to address via api
        j = {
            "blockId": self.block_id
        }
        request = json.dumps(j)
        forward_transfer = sc_node.block_getForwardTransfers(request)['result']['forwardTransfers'][0]
        assert_equal(ft_amount_in_wei, int(forward_transfer['value']))
        assert_equal(format_eoa(self.evm_address), forward_transfer['to'])

        # Deploy Smart Contract
        smart_contract_type = 'StorageTestContract'
        logging.info(f"Creating smart contract utilities for {smart_contract_type}")
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)
        test_message = 'Initial message'
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, test_message,
                                                                fromAddress=self.evm_address,
                                                                gasLimit=10000000,
                                                                gasPrice=900000000)

        logging.info("Mempool node before")
        logging.info(allTransactions(sc_node))

        generate_next_blocks(sc_node, "first node", 1)
        logging.info("Mempool node after")
        logging.info(allTransactions(sc_node))

        # verify smart contract has a balance of zero
        balance = sc_node.rpc_eth_getBalance(smart_contract_address, "latest")
        logging.info(balance)
        assert_equal("0x0", balance["result"], "smart contract has non-zero balance")

        # execute forward transfer to the smart contract account
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(smart_contract_address),
                                      self.ft_amount_in_zen,
                                      self.mc_return_address)

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)

        # verify that the smart contract account balance has not changed
        balance = sc_node.rpc_eth_getBalance(smart_contract_address, "latest")
        logging.info(balance)
        assert_equal("0x0", balance["result"], "smart contract has non-zero balance")

        # verify that such amount has been burned, that means credited to 0xdead address
        balance = sc_node.rpc_eth_getBalance(to_checksum_address(NULL_ADDRESS), "latest")
        logging.info(balance)
        assert_equal(hex(int(forward_transfer['value'])), balance["result"], "dead address has zero balance")


if __name__ == "__main__":
    SCEvmForwardTransfer().main()
