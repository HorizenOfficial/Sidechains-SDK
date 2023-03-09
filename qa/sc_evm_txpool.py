#!/usr/bin/env python3

from decimal import Decimal
from eth_utils import remove_0x_prefix
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createRawEIP1559Transaction import createRawEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.sendTransaction import sendTransaction
from SidechainTestFramework.account.httpCalls.transaction.signTransaction import signTransaction
from SidechainTestFramework.account.utils import convertZenToWei
from SidechainTestFramework.scutil import generate_next_block
from SidechainTestFramework.account.ac_use_smart_contract import SmartContract
from test_framework.util import (assert_true)

"""
Configuration: 
    - 1 SC node
    - 1 MC node

Test the RPC methods of the txpool namespace:
    - txpool_status
    - txpool_content
    - txpool_contentFrom
    - txpool_inspect
     
"""

class SCEvmTxPool(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=80)

    def do_send_raw_tx(self, raw_tx, evm_signer_address):
        sc_node = self.sc_nodes[0]
        signed_raw_tx = signTransaction(sc_node, fromAddress=evm_signer_address, payload=raw_tx)

        tx_hash = sendTransaction(sc_node, payload=signed_raw_tx)
        return tx_hash

    def run_test(self):

        ft_amount_in_zen = Decimal('1000.0')

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)
        sc_node = self.sc_nodes[0]

        evm_address_1 = remove_0x_prefix(self.evm_address)
        evm_address_2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        nonce_addr_1 = 0
        maxPriorityFeePerGas = 1500000
        transferred_amount_in_zen = Decimal('1.2')

        # create and send to the mempool some transactions from evm_address_1
        for i in range(3):
            raw_tx = createRawEIP1559Transaction(sc_node,
                                                 fromAddress=evm_address_1,
                                                 toAddress=evm_address_2,
                                                 value=convertZenToWei(transferred_amount_in_zen),
                                                 nonce=str(nonce_addr_1),
                                                 maxPriorityFeePerGas=maxPriorityFeePerGas)
            self.do_send_raw_tx(raw_tx=raw_tx, evm_signer_address=evm_address_1)
            nonce_addr_1 += 1

        # txpool status
        # 3 pending transactions are expected
        status_res = sc_node.rpc_txpool_status()['result']
        assert_true(status_res['pending'] == 3)
        assert_true(status_res['queued'] == 0)

        # txpool content
        # 3 pending transactions from evm_address_1 with increasing nonce are expected
        content_res = sc_node.rpc_txpool_content()['result']
        mempool_tx1 = content_res['pending']['0x'+evm_address_1]['0']
        assert_true(mempool_tx1['nonce'] == '0x0')
        assert_true(mempool_tx1['from'] == '0x'+evm_address_1)
        assert_true(mempool_tx1['to'] == '0x'+evm_address_2)
        assert_true(mempool_tx1['value'] == hex(convertZenToWei(transferred_amount_in_zen)))
        mempool_tx2 = content_res['pending']['0x'+evm_address_1]['1']
        assert_true(mempool_tx2['nonce'] == '0x1')
        assert_true(mempool_tx2['from'] == '0x'+evm_address_1)
        assert_true(mempool_tx2['to'] == '0x'+evm_address_2)
        assert_true(mempool_tx2['value'] == hex(convertZenToWei(transferred_amount_in_zen)))
        mempool_tx3 = content_res['pending']['0x'+evm_address_1]['2']
        assert_true(mempool_tx3['nonce'] == '0x2')
        assert_true(mempool_tx3['from'] == '0x'+evm_address_1)
        assert_true(mempool_tx3['to'] == '0x'+evm_address_2)
        assert_true(mempool_tx3['value'] == hex(convertZenToWei(transferred_amount_in_zen)))
        assert_true(len(content_res['queued']) == 0)

        # txpool content from
        # 3 pending transactions from evm_address_1 with increasing nonce are expected
        content_from_res = sc_node.rpc_txpool_contentFrom('0x'+evm_address_1)['result']
        assert_true(len(content_from_res['pending']['0x'+evm_address_1]) == 3)
        assert_true(len(content_from_res['queued']) == 0)
        # no transactions from evm_address_2 are expected
        content_from_res = sc_node.rpc_txpool_contentFrom('0x'+evm_address_2)['result']
        assert_true(len(content_from_res['pending']) == 0)
        assert_true(len(content_from_res['queued']) == 0)

        # txpool inspect
        inspect_expected_result = ('0x'+evm_address_2 + ': ' + str(convertZenToWei(transferred_amount_in_zen))
            + ' wei + ' + str(int(mempool_tx1['gas'],16)) + ' gas × ' + str(int(mempool_tx1['gasPrice'],16)) + ' wei')
        inspect_res = sc_node.rpc_txpool_inspect()['result']
        assert_true(inspect_res['pending']['0x'+evm_address_1]['0'] == inspect_expected_result)
        assert_true(inspect_res['pending']['0x'+evm_address_1]['1'] == inspect_expected_result)
        assert_true(inspect_res['pending']['0x'+evm_address_1]['2'] == inspect_expected_result)
        assert_true(len(content_res['queued']) == 0)

        # generate block and finalize the transactions in the mempool
        generate_next_block(sc_node, "first node")

        # --------------------------------------------------------------------------------------------------------------
        # now send transactions to the mempool from different address and check that the txpool rpc methods work as expected

        # send transaction from evm_address_1
        raw_tx = createRawEIP1559Transaction(sc_node,
                                             fromAddress=evm_address_1,
                                             toAddress=evm_address_2,
                                             value=convertZenToWei(transferred_amount_in_zen),
                                             nonce=str(3),
                                             maxPriorityFeePerGas=maxPriorityFeePerGas)
        self.do_send_raw_tx(raw_tx=raw_tx, evm_signer_address=evm_address_1)

        # send transaction from evm_address_2
        raw_tx = createRawEIP1559Transaction(sc_node,
                                             fromAddress=evm_address_2,
                                             toAddress=evm_address_1,
                                             value=convertZenToWei(transferred_amount_in_zen),
                                             nonce=str(0),
                                             maxPriorityFeePerGas=maxPriorityFeePerGas)
        self.do_send_raw_tx(raw_tx=raw_tx, evm_signer_address=evm_address_2)

        # send transaction from evm_address_1 that can't be directly accepted (nonce too high)
        raw_tx = createRawEIP1559Transaction(sc_node,
                                             fromAddress=evm_address_1,
                                             toAddress=evm_address_2,
                                             value=convertZenToWei(transferred_amount_in_zen),
                                             nonce=str(6),
                                             maxPriorityFeePerGas=maxPriorityFeePerGas)
        self.do_send_raw_tx(raw_tx=raw_tx, evm_signer_address=evm_address_1)

        # txpool status
        # 2 pending and 1 queued transactions are expected
        status_res = sc_node.rpc_txpool_status()['result']
        assert_true(status_res['pending'] == 2)
        assert_true(status_res['queued'] == 1)

        # txpool content
        content_res = sc_node.rpc_txpool_content()['result']
        # pending transaction from evm_address_1 with nonce 3
        mempool_tx1 = content_res['pending']['0x'+evm_address_1]['3']
        assert_true(mempool_tx1['nonce'] == '0x3')
        assert_true(mempool_tx1['from'] == '0x'+evm_address_1)
        assert_true(mempool_tx1['to'] == '0x'+evm_address_2)
        assert_true(mempool_tx1['value'] == hex(convertZenToWei(transferred_amount_in_zen)))
        # pending transaction from evm_address_2 with nonce 0
        mempool_tx2 = content_res['pending']['0x'+evm_address_2]['0']
        assert_true(mempool_tx2['nonce'] == '0x0')
        assert_true(mempool_tx2['from'] == '0x'+evm_address_2)
        assert_true(mempool_tx2['to'] == '0x'+evm_address_1)
        assert_true(mempool_tx2['value'] == hex(convertZenToWei(transferred_amount_in_zen)))
        # queued transaction from evm_address_1 with nonce 6
        mempool_tx3 = content_res['queued']['0x'+evm_address_1]['6']
        assert_true(mempool_tx3['nonce'] == '0x6')
        assert_true(mempool_tx3['from'] == '0x'+evm_address_1)
        assert_true(mempool_tx3['to'] == '0x'+evm_address_2)
        assert_true(mempool_tx3['value'] == hex(convertZenToWei(transferred_amount_in_zen)))

        # txpool content from
        # 1 pending and 1 queued transactions are expected from evm_address_1
        content_from_res = sc_node.rpc_txpool_contentFrom('0x'+evm_address_1)['result']
        assert_true(len(content_from_res['pending']['0x'+evm_address_1]) == 1)
        assert_true(len(content_from_res['queued']['0x'+evm_address_1]) == 1)
        # 1 pending and no queued transactions are expected from evm_address_2
        content_from_res = sc_node.rpc_txpool_contentFrom('0x'+evm_address_2)['result']
        assert_true(len(content_from_res['pending']['0x'+evm_address_2]) == 1)
        assert_true(len(content_from_res['queued']) == 0)

        # txpool inspect
        inspect_expected_result_address1 = ('0x'+evm_address_2 + ': ' + str(convertZenToWei(transferred_amount_in_zen))
            + ' wei + ' + str(int(mempool_tx1['gas'],16)) + ' gas × ' + str(int(mempool_tx1['gasPrice'],16)) + ' wei')
        inspect_expected_result_address2 = ('0x'+evm_address_1 + ': ' + str(convertZenToWei(transferred_amount_in_zen))
            + ' wei + ' + str(int(mempool_tx1['gas'],16)) + ' gas × ' + str(int(mempool_tx1['gasPrice'],16)) + ' wei')
        inspect_res = sc_node.rpc_txpool_inspect()['result']
        assert_true(inspect_res['pending']['0x'+evm_address_1]['3'] == inspect_expected_result_address1)
        assert_true(inspect_res['pending']['0x'+evm_address_2]['0'] == inspect_expected_result_address2)
        assert_true(inspect_res['queued']['0x'+evm_address_1]['6'] == inspect_expected_result_address1)

        # generate block and finalize the transactions in the mempool
        generate_next_block(sc_node, "first node")

        # --------------------------------------------------------------------------------------------------------------
        # deploy smart contract and check that the txpool rpc methods work as expected. There will be a non-executable
        # transaction from the previous step, check if still present

        # deploy smart contract
        smart_contract_type = 'StorageTestContract'
        smart_contract = SmartContract(smart_contract_type)
        test_message = 'Initial message'
        tx_hash, smart_contract_address = smart_contract.deploy(sc_node, test_message,
                                                                fromAddress=self.evm_address,
                                                                gasLimit=10000000,
                                                                gasPrice=900000000)

        # txpool content
        content_res = sc_node.rpc_txpool_content()['result']
        # pending transaction from evm_address_1 with nonce 4
        mempool_tx2 = content_res['pending']['0x'+evm_address_1]['4']
        assert_true(mempool_tx2['nonce'] == '0x4')
        assert_true(mempool_tx2['from'] == '0x'+evm_address_1)
        assert_true(mempool_tx2['to'] == None)
        assert_true(mempool_tx2['value'] == hex(0))
        # queued transaction from evm_address_1 with nonce 6
        mempool_tx2 = content_res['queued']['0x'+evm_address_1]['6']
        assert_true(mempool_tx2['nonce'] == '0x6')
        assert_true(mempool_tx2['from'] == '0x'+evm_address_1)
        assert_true(mempool_tx2['to'] == '0x'+evm_address_2)
        assert_true(mempool_tx2['value'] == hex(convertZenToWei(transferred_amount_in_zen)))

        # txpool inspect
        inspect_expected_result_address1_smart_contract = ('contract creation: 0 wei + 10000000 gas × '
            + str(int(mempool_tx1['gasPrice'],16)) + ' wei')
        inspect_expected_result_address1 = ('0x'+evm_address_2 + ': ' + str(convertZenToWei(transferred_amount_in_zen))
            + ' wei + ' + str(int(mempool_tx1['gas'],16)) + ' gas × ' + str(int(mempool_tx1['gasPrice'],16)) + ' wei')
        inspect_res = sc_node.rpc_txpool_inspect()['result']
        assert_true(inspect_res['pending']['0x'+evm_address_1]['4'] == inspect_expected_result_address1_smart_contract)
        assert_true(inspect_res['queued']['0x'+evm_address_1]['6'] == inspect_expected_result_address1)

        # generate block and finalize the transactions in the mempool
        generate_next_block(sc_node, "first node")


if __name__ == "__main__":
    SCEvmTxPool().main()
