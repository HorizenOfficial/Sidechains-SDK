#!/usr/bin/env python3
from decimal import Decimal

from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createRawEIP1559Transaction import createRawEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.scutil import generate_next_block, generate_next_blocks, assert_equal
from test_framework.util import (assert_true)

"""
Configuration: 
    - 1 SC node
    - 1 MC node

Test:
    - Setup MC and SC
    - Transfer some funds to one address
    - Check gasPrice = baseFee, because no transactions in blocks
    - Create some transactions, generate a block and check if they are present
    - Create more blocks and check that gasPrice = maxPriorityFeePerGas + baseFee
    - Create more blocks to get in extended range for suggestTipCap and check again
    - Create more blocks to get out of 40 block range and check that gasPrice = baseFee
    - Create txs that have 600 GWei as maxPriorityFeePerGas
    - Create a block and check that gasPrice returns maxPrice of 500 GWei
     
"""


class SCEvmGasPrice(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=80)

    def __do_send_raw_tx(self, raw_tx, evm_signer_address):
        sc_node = self.sc_nodes[0]
        signed_raw_tx = signTransaction(sc_node, fromAddress=evm_signer_address, payload=raw_tx)

        tx_hash = sendTransaction(sc_node, payload=signed_raw_tx)
        return tx_hash

    def __do_check_gas_price(self, minerFee, generate_blocks=1):
        maxPrice = 500000000000
        sc_node = self.sc_nodes[0]

        if generate_blocks > 0:
            generate_next_blocks(sc_node, "first node", generate_blocks)

        expected_price = int(sc_node.rpc_eth_getBlockByNumber('latest', True)['result']['baseFeePerGas'], 16)
        if minerFee  > maxPrice:
            expected_price += maxPrice
        else:
            expected_price += minerFee

        assert_equal(expected_price, int(sc_node.rpc_eth_gasPrice()['result'], 16))

    def run_test(self):
        ft_amount_in_zen = Decimal('1000.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)
        sc_node = self.sc_nodes[0]

        evm_address_1 = remove_0x_prefix(self.evm_address)
        evm_address_2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        # no txs available - return current base fee
        self.__do_check_gas_price(0, generate_blocks=1)

        nonce_addr_1 = 0
        maxPriorityFeePerGas = 1500000
        transferred_amount_in_zen = Decimal('1.2')

        # Create some txs for gasPrice calculation later
        for j in range(10):
            for i in range(5):
                raw_tx = createRawEIP1559Transaction(sc_node,
                                                     fromAddress=evm_address_1,
                                                     toAddress=evm_address_2,
                                                     value=convertZenToWei(transferred_amount_in_zen),
                                                     nonce=str(nonce_addr_1),
                                                     maxPriorityFeePerGas=maxPriorityFeePerGas)
                self.__do_send_raw_tx(raw_tx=raw_tx, evm_signer_address=evm_address_1)
                nonce_addr_1 += 1
            generate_next_block(sc_node, "first node")

        assert_true(len(sc_node.rpc_eth_getBlockByNumber('latest', False)['result']['transactions']) == 5)

        self.__do_check_gas_price(maxPriorityFeePerGas, generate_blocks=0)

        self.__do_check_gas_price(maxPriorityFeePerGas, generate_blocks=40)

        maxPriorityFeePerGas = 600000000000
        for j in range(10):
            for i in range(5):
                raw_tx = createRawEIP1559Transaction(sc_node,
                                                     fromAddress=evm_address_1,
                                                     toAddress=evm_address_2,
                                                     value=convertZenToWei(transferred_amount_in_zen),
                                                     nonce=str(nonce_addr_1),
                                                     maxFeePerGas=maxPriorityFeePerGas,
                                                     maxPriorityFeePerGas=maxPriorityFeePerGas)
                self.__do_send_raw_tx(raw_tx=raw_tx, evm_signer_address=evm_address_1)
                nonce_addr_1 += 1
                generate_next_block(sc_node, "first node")

        self.__do_check_gas_price(maxPriorityFeePerGas, generate_blocks=0)


if __name__ == "__main__":
    SCEvmGasPrice().main()
