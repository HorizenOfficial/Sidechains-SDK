#!/usr/bin/env python3
from eth_utils import to_checksum_address

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import format_evm, format_eoa, deploy_smart_contract
from SidechainTestFramework.account.utils import convertZenToWei, \
    NULL_ADDRESS, CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS, FORGER_POOL_RECIPIENT_ADDRESS
from SidechainTestFramework.scutil import generate_next_blocks
from test_framework.util import (assert_equal, forward_transfer_to_sidechain)

"""
Configuration: 
    - 1 SC node
    - 1 MC node

Tests:
    - Test 1 - FT to a native Smart Contract address
    - Test 2 - FT to a deployed Smart Contract address
    - Test 3 - negative - FT to a native Smart Contract address that cannot receive funds,
               check that the burn address is updated
"""


class SCEvmFtToNativeContract(AccountChainSetup):
    def __init__(self):
        super().__init__()

    def send_zen_to_address(self, address, amount):
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      format_eoa(address),
                                      amount,
                                      self.nodes[0].getnewaddress())
        self.sc_sync_all()
        generate_next_blocks(self.sc_nodes[0], "first node", 1)
        self.sc_sync_all()

    def check_ft_to_smart_contract(self, address):
        # check the balance of a smart contract is null
        nsc_bal = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(address), 'latest')['result'], 16)
        assert_equal(nsc_bal, 0)
        # send zen
        ft_amount = 1
        ft_amount_wei = convertZenToWei(ft_amount)
        self.send_zen_to_address(address, ft_amount)
        # check the balance of a smart contract is updated
        nsc_bal = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(address), 'latest')['result'], 16)
        assert_equal(nsc_bal, ft_amount_wei)

    def run_test(self):
        sc_node = self.sc_nodes[0]
        self.sc_ac_setup(forwardTransfer=10)

        # TEST 1 - ft to native SC
        self.check_ft_to_smart_contract(FORGER_POOL_RECIPIENT_ADDRESS)

        # TEST 2 - ft to deployed SC
        # Deploy Factory smart contract
        evm_hex_address = to_checksum_address(self.evm_address)
        smart_contract = 'BlockHash'
        smart_contract = SmartContract(smart_contract)
        smart_contract_address = deploy_smart_contract(sc_node, smart_contract, evm_hex_address)

        self.check_ft_to_smart_contract(smart_contract_address)

        # TEST 3 - ft to native SC that cannot receive funds
        # check that both native sc and burn address are at 0 balance
        address = CERTIFICATE_KEY_ROTATION_SMART_CONTRACT_ADDRESS
        sc_bal = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(NULL_ADDRESS), 'latest')['result'], 16)
        assert_equal(sc_bal, 0)
        sc_bal = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(address), 'latest')['result'], 16)
        assert_equal(sc_bal, 0)

        # send ft to a native sc that cannot receive funds
        ft_amount = 1
        ft_amount_wei = convertZenToWei(ft_amount)
        self.send_zen_to_address(address, ft_amount)
        # check the balance of native smart contract is not updated
        sc_bal = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(address), 'latest')['result'], 16)
        assert_equal(sc_bal, 0)
        # check the balance of burn address is updated
        sc_bal = int(
            self.sc_nodes[0].rpc_eth_getBalance(format_evm(NULL_ADDRESS), 'latest')['result'], 16)
        assert_equal(sc_bal, ft_amount_wei)


if __name__ == "__main__":
    SCEvmFtToNativeContract().main()
