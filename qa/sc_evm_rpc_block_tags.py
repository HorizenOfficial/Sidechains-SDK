#!/usr/bin/env python3
import logging
from decimal import Decimal

from eth_utils import remove_0x_prefix, add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from SidechainTestFramework.account.ac_utils import deploy_smart_contract, contract_function_call, \
    contract_function_static_call
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.scutil import generate_next_block, \
    assert_true, generate_next_blocks

"""
# Test for block tags

Configuration:
    - 2 SC node (1 Forger, 1 non-Forger)
    - 1 MC node

Test:
    - Check all RPC functions that support block tags
      - with earliest, safe, finalized, pending, latest
    - Add some tx and check output for pending
    - Mine a block and re-check with latest
    - Deploy a smart contract, do some function and static calls and check output for pending
    - Mine a block and re-check with latest
    - Mine more blocks to reach > 100 Sidechain blocks and re-check output for safe / finalized
    
"""


class SCEvmRpcBlockTags(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=50)

    def __do_contract_call_tests(self, node, tag, secondAddress=None):
        if tag not in ['pending', 'latest']:
            raise Exception("Tag passed is invalid.")

        generate_next_block(node, "first node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        next_block = False if tag == 'pending' else True

        smart_contract_type = 'TestERC20'
        logging.info("Creating smart contract utilities for {}".format(smart_contract_type))
        smart_contract = SmartContract(smart_contract_type)
        logging.info(smart_contract)

        estimated_gas_pending = smart_contract.estimate_gas(node, 'constructor',
                                                            fromAddress=self.evm_address, tag='pending')
        estimated_gas_latest = smart_contract.estimate_gas(node, 'constructor',
                                                           fromAddress=self.evm_address, tag='latest')
        assert_true(estimated_gas_pending == estimated_gas_latest)

        assert_true(node.rpc_eth_getCode(self.evm_address, tag)['result'] == '0x')

        smart_contract_address = deploy_smart_contract(node=node, smart_contract=smart_contract,
                                                       from_address=self.evm_address,
                                                       next_block=next_block)

        assert_true(smart_contract.Bytecode[0:19] in node.rpc_eth_getCode(smart_contract_address, tag)['result'])

        initial_balance = 100
        method = 'totalSupply()'
        res = contract_function_static_call(node, smart_contract, smart_contract_address, self.evm_address, method,
                                            tag=tag)
        assert_true(res[0] == initial_balance)

        transfer_amount = 10
        method = 'transfer(address,uint256)'
        tx_hash = contract_function_call(node, smart_contract, smart_contract_address, self.evm_address, method,
                                         secondAddress, transfer_amount, tag=tag, overrideGas=500000)

        if next_block:
            generate_next_block(node, "first_node")
            self.sc_sync_all()
            assert_true(node.rpc_eth_getBlockByNumber(tag, True)['result']['transactions'][0]['hash'] == tx_hash)
        else:
            assert_true(node.rpc_eth_getBlockByNumber(tag, True)['result']['transactions'][1]['hash'] == tx_hash)

        method = 'balanceOf(address)'
        res = contract_function_static_call(node, smart_contract, smart_contract_address, secondAddress, method,
                                            secondAddress,
                                            tag='pending')
        assert_true(res[0] == transfer_amount)

    def __send_and_assert_tag_rpc_methods(self, tag, **kwargs):
        if tag not in ['earliest', 'safe', 'finalized', 'pending', 'latest']:
            raise Exception("Tag passed is invalid.")

        hasBlock = \
            False if (tag in ['safe', 'finalized'] and self.sc_nodes[0].block_best()['result']['height'] <= 100) \
                else True

        for node in self.sc_nodes:
            resBalance = node.rpc_eth_getBalance(self.evm_address, tag)
            resTxCount = node.rpc_eth_getTransactionCount(self.evm_address, tag)
            resBlockTxCount = node.rpc_eth_getBlockTransactionCountByNumber(tag)
            resBlock = node.rpc_eth_getBlockByNumber(tag, False)
            resTxByIdx = node.rpc_eth_getTransactionByBlockNumberAndIndex(tag, "0x0")

            if hasBlock:
                assert_true(resBalance['result'] == kwargs['Balance'])
                assert_true(resTxCount['result'] == kwargs['TransactionCount'])
                assert_true(resBlockTxCount['result'] == kwargs['BlockTransactionCount'])
                resBlock = resBlock['result']
                resTxByIdx = resTxByIdx['result']
                expTxByIdx = kwargs['TransactionByIndex']
                if resTxByIdx is None:
                    assert_true(resTxByIdx == expTxByIdx)
                else:
                    assert_true(resTxByIdx['transactionIndex'] == expTxByIdx)
                if tag == 'earliest':
                    assert_true(resBlock['parentHash'] == kwargs['Block'])
                else:
                    assert_true(resBlock['number'] == kwargs['Block'])
            else:
                assert_true(resBalance['error']['code'] == kwargs['Balance'])
                assert_true(resTxCount['error']['code'] == kwargs['TransactionCount'])
                assert_true(resBlockTxCount['error']['code'] == kwargs['BlockTransactionCount'])
                assert_true(resBlock['error']['code'] == kwargs['Block'])
                assert_true(resBlock['error']['code'] == kwargs['TransactionByIndex'])

    def run_test(self):
        sc_node_1 = self.sc_nodes[0]

        ft_amount_in_zen = Decimal('500.0')
        ft_amount_in_wei_hex_str = str(hex(convertZenToWei(ft_amount_in_zen)))

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        evm_address_sc1 = remove_0x_prefix(self.evm_address)
        evm_address_sc2 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        self.sc_sync_all()

        expResp = {'Balance': '0x0', 'TransactionCount': '0x0', 'BlockTransactionCount': '0x0',
                   'Block': '0x0000000000000000000000000000000000000000000000000000000000000000',
                   'TransactionByIndex': None}
        self.__send_and_assert_tag_rpc_methods('earliest', **expResp)

        expResp['Balance'], expResp['Block'] = ft_amount_in_wei_hex_str, '0x3'
        self.__send_and_assert_tag_rpc_methods('pending', **expResp)

        expResp['Block'] = '0x2'
        self.__send_and_assert_tag_rpc_methods('latest', **expResp)

        # safe / finalized will return -39001 Unknown Block error until we have 100 sidechain blocks
        expResp = {k: -39001 for k in expResp}
        self.__send_and_assert_tag_rpc_methods('safe', **expResp)
        self.__send_and_assert_tag_rpc_methods('finalized', **expResp)

        # Create some TX in mempool
        nonce_addr_1 = 0
        common_tx_list = []
        for i in range(4):
            common_tx_list.append(add_0x_prefix(
                createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                         nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000,
                                         maxFeePerGas=900000000, value=1)))
            nonce_addr_1 += 1

        # Check if tx hashes are included in pending block
        assert_true(common_tx_list == sc_node_1.rpc_eth_getBlockByNumber('pending', False)['result']['transactions'])

        # Calculate the balance for address in mempool
        tx = sc_node_1.rpc_eth_getBlockByNumber('pending', True)['result']['transactions'][0]
        gasPrice, value, gasForEoa2Eoa = int(tx['gasPrice'], 16), int(tx['value'], 16), 0x5208
        balanceUsed = ((gasPrice * gasForEoa2Eoa) + value) * len(common_tx_list)
        pendingBalance = str(hex((int(ft_amount_in_wei_hex_str, 16) - balanceUsed)))

        expResp = {'Balance': pendingBalance, 'TransactionCount': '0x4', 'BlockTransactionCount': '0x4',
                   'Block': '0x3', 'TransactionByIndex': '0x0'}
        self.__send_and_assert_tag_rpc_methods('pending', **expResp)

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Check latest block, should give same outputs as pending block before
        self.__send_and_assert_tag_rpc_methods('latest', **expResp)

        # Test contract deployment and contract calls with 'pending' tag
        self.__do_contract_call_tests(self.sc_nodes[0], 'pending', add_0x_prefix(evm_address_sc2))

        # Create tracer result for pending block after smart contract deployment was added to mempool
        traceBlock_pending = sc_node_1.rpc_debug_traceBlockByNumber('pending')['result']
        assert_true(len(traceBlock_pending) > 0)

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # Create tracer result for latest block - just mined - and compare with pending one before
        traceBlock_latest = sc_node_1.rpc_debug_traceBlockByNumber('latest')['result']
        assert_true(traceBlock_pending == traceBlock_latest)

        # Test contract deployment and contract calls with 'latest' tag
        self.__do_contract_call_tests(self.sc_nodes[0], 'latest', add_0x_prefix(evm_address_sc2))

        # Generate SC blocks to get the genesis block to be first safe / finalized block
        generate_next_blocks(sc_node_1, "first node", 101 - int(sc_node_1.rpc_eth_blockNumber()['result'], 16))
        expResp = {'Balance': '0x0', 'TransactionCount': '0x0', 'BlockTransactionCount': '0x0',
                   'Block': '0x1', 'TransactionByIndex': None}
        self.__send_and_assert_tag_rpc_methods('safe', **expResp)
        self.__send_and_assert_tag_rpc_methods('finalized', **expResp)


if __name__ == "__main__":
    SCEvmRpcBlockTags().main()
