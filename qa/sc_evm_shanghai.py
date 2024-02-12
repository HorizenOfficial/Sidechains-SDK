#!/usr/bin/env python3
import logging
import math
import pprint
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.utils import BLOCK_GAS_LIMIT, VER_1_3_FORK_EPOCH, INTEROPERABILITY_FORK_EPOCH
from SidechainTestFramework.scutil import generate_next_block, \
    assert_equal, \
    assert_true, EVM_APP_SLOT_TIME
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import forward_transfer_to_sidechain, fail, assert_false

"""
Check that transactions that were valid during Paris and that were added to the mempool, they are not inserted in a block
after Shanghai was activated.

This test doesn't support --allforks.

Configuration:
    - 1 SC node
    - 1 MC node

Test:
   - Add to the mempool non-executable transactions that are valid in Paris but not in Shanghai 
   - Reach Shanghai fork point
   - make the non-executable transactions executable
   - forge a block
   - Verify that the transactions are not in the block but still in the mempool
"""


class SCEvmMempoolInvalidTxs(AccountChainSetup):
    def __init__(self):
        super().__init__(block_timestamp_rewind=1500 * EVM_APP_SLOT_TIME * VER_1_3_FORK_EPOCH, max_mempool_slots=20,
                         max_nonexec_pool_slots=19)

    def run_test(self):
        if self.options.all_forks:
            logging.info("This test cannot be executed with --allforks")
            exit()

        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('3000.0')
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc2,
                                      1.0,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)

        self.sync_all()

        generate_next_block(sc_node, "first node")
        self.sc_sync_all()

        # Create a non-exec transaction (evm_address_sc1-nonce=1) that will have an intrinsic gas too low when Shanghai is activated.

        tx_id_gas = createEIP1559Transaction(sc_node, fromAddress=evm_address_sc1, toAddress=None,
                                             nonce=1, gasLimit=53049, maxPriorityFeePerGas=900000000,
                                             maxFeePerGas=900000000, value=1, data='012344')

        # Create a non-exec transaction (evm_address_sc2-nonce=1) that will have an initcode size too big when Shanghai is activated.
        MAX_INITCODE_SIZE = 49152
        big_data = 'FF' * (MAX_INITCODE_SIZE + 1)
        tx_id_initcode = createEIP1559Transaction(sc_node, fromAddress=evm_address_sc2, toAddress=None,
                                                  nonce=1, gasLimit=20533001, maxPriorityFeePerGas=900000000,
                                                  maxFeePerGas=900000000, value=1, data=str(big_data))

        # reach the SHANGHAI fork
        current_best_epoch = sc_node.block_forgingInfo()["result"]["bestBlockEpochNumber"]

        for i in range(0, VER_1_3_FORK_EPOCH - current_best_epoch):
            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

        # Check that they are still in the mempool, because they are non-exec
        response = allTransactions(sc_node, False)
        assert_equal(2, len(response["transactionIds"]), "Wrong number of transaction in the mempool")
        assert_true(tx_id_gas in response["transactionIds"])
        assert_true(tx_id_initcode in response["transactionIds"])

        status_res = sc_node.rpc_txpool_status()['result']
        assert_equal(0, status_res['pending'])
        assert_equal(2, status_res['queued'])

        # Create the txs with nonce=0, so the other txs become executable too
        createEIP1559Transaction(sc_node, fromAddress=evm_address_sc1,
                                      toAddress=evm_address_sc2, nonce=0, value=1)
        createEIP1559Transaction(sc_node, fromAddress=evm_address_sc2,
                                      toAddress=evm_address_sc1, nonce=0, value=1)

        status_res = sc_node.rpc_txpool_status()['result']
        assert_equal(4, status_res['pending'])
        assert_equal(0, status_res['queued'])

        # Generate a block. It should contain only the txs with nonce=0, because the others are not valid anymore

        generate_next_block(sc_node, "first node", force_switch_to_next_epoch=False)

        sc_best_block = sc_node.block_best()["result"]['block']
        block_tx_list = sc_best_block['sidechainTransactions']
        assert_equal(2, len(block_tx_list), "Wrong number of transaction in the block")
        assert_false(tx_id_gas in block_tx_list)
        assert_false(tx_id_initcode in block_tx_list)

        response = allTransactions(sc_node, False)
        assert_equal(2, len(response["transactionIds"]),
                     "Transaction that creates a smart contract with empty data added to mempool")
        assert_true(tx_id_gas in response["transactionIds"])
        assert_true(tx_id_initcode in response["transactionIds"])

        status_res = sc_node.rpc_txpool_status()['result']
        assert_equal(2, status_res['pending'])
        assert_equal(0, status_res['queued'])


if __name__ == "__main__":
    SCEvmMempoolInvalidTxs().main()
