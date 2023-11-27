#!/usr/bin/env python3

from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa, deploy_smart_contract
from SidechainTestFramework.account.utils import convertZenToWei, \
    NULL_ADDRESS, FORGER_STAKE_SMART_CONTRACT_ADDRESS
from SidechainTestFramework.scutil import generate_next_blocks, generate_next_block, EVM_APP_SLOT_TIME, SLOTS_IN_EPOCH
from test_framework.util import (assert_equal, forward_transfer_to_sidechain, assert_false)

"""
Configuration: 
    - 1 SC node
    - 1 MC node

Tests:
    
    - TEST 1 - before the fork
        - 1.1 - ft to native SC - burned
        - 1.2 - ft to deployed SC - burned
        - 1.3 - FT to a random address - success
    - TEST 2 - after the fork
        - 2.1 - ft to native SC - success
        - 2.2 - ft to deployed SC - success
        - 2.3 - FT to a random address - success
"""


class SCEvmFtToNativeContract(AccountChainSetup):
    def __init__(self):
        super().__init__(block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 100)

    def advance_to_epoch(self, epoch_number):
        sc_node = self.sc_nodes[0]
        forging_info = sc_node.block_forgingInfo()
        current_epoch = forging_info["result"]["bestBlockEpochNumber"]
        # make sure we are not already passed the desired epoch
        assert_false(current_epoch > epoch_number, "unexpected epoch number")
        while current_epoch < epoch_number:
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()
            forging_info = sc_node.block_forgingInfo()
            current_epoch = forging_info["result"]["bestBlockEpochNumber"]

    def send_zen_to_address(self, address, amount):
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(address),
                                      amount,
                                      self.nodes[0].getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(self.sc_nodes[0], "first node", 1)
        self.sc_sync_all()

    def check_ft_to_smart_contract(self, address, success):
        # check the initial balance
        start_balance = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(address), 'latest')['result'], 16)
        start_burn_balance = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(NULL_ADDRESS), 'latest')['result'], 16)
        # send zen
        ft_amount = 1
        ft_amount_wei = convertZenToWei(ft_amount)
        self.send_zen_to_address(address, ft_amount)
        if success:
            # check the balance of a smart contract is updated
            updated_bal = int(
                self.sc_nodes[0].rpc_eth_getBalance(format_evm(address), 'latest')['result'], 16)
            updated_burn_bal = int(
                self.sc_nodes[0].rpc_eth_getBalance(format_evm(NULL_ADDRESS), 'latest')['result'], 16)
            assert_equal(updated_bal, start_balance + ft_amount_wei)
            assert_equal(updated_burn_bal, start_burn_balance)
        else:
            # check that funds are burned - send to 0 address
            updated_bal = int(
                self.sc_nodes[0].rpc_eth_getBalance(format_evm(address), 'latest')['result'], 16)
            updated_burn_bal = int(
                self.sc_nodes[0].rpc_eth_getBalance(format_evm(NULL_ADDRESS), 'latest')['result'], 16)
            assert_equal(updated_bal, start_balance)
            assert_equal(updated_burn_bal, start_burn_balance + ft_amount_wei)

    def run_test(self):
        sc_node = self.sc_nodes[0]
        self.sc_ac_setup(forwardTransfer=10)

        # TEST 1.1 - ft to native SC - burned
        self.check_ft_to_smart_contract(FORGER_STAKE_SMART_CONTRACT_ADDRESS, False)

        # TEST 1.2 - ft to deployed SC - burned
        # Deploy Factory smart contract
        evm_hex_address = to_checksum_address(self.evm_address)
        smart_contract_name = 'BlockHash'
        smart_contract = SmartContract(smart_contract_name)
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, evm_hex_address)

        self.check_ft_to_smart_contract(smart_contract_address, False)

        # TEST 1.3 - FT to a random address - success
        self.check_ft_to_smart_contract("0000000000000000000012341234123412341234", True)

        # ------------------------------------------------------------------------------
        # Advance to epoch 60, enabling fork that allows FT to smart contract addresses
        # Repeat the FTs, now they should work
        self.advance_to_epoch(60)

        # TEST 2.1 - ft to native SC - success
        self.check_ft_to_smart_contract(FORGER_STAKE_SMART_CONTRACT_ADDRESS, True)

        # TEST 2.2 - ft to deployed SC - success
        self.check_ft_to_smart_contract(smart_contract_address, True)

        # TEST 2.3 - FT to a random address - success
        self.check_ft_to_smart_contract("0000000000000000000012341234123412341234", True)


if __name__ == "__main__":
    SCEvmFtToNativeContract().main()
