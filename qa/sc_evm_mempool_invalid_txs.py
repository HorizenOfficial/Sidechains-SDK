#!/usr/bin/env python3
import json
import logging
import random
from decimal import Decimal

from SidechainTestFramework.account.httpCalls.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_block, \
    EVM_APP_BINARY, AccountModelBlockVersion, assert_equal, \
    assert_true, BLOCK_GAS_LIMIT, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain

"""
Check Mem Pool is correctly updated after:
1. Blocks are applied to the blockchain
2. Some blocks are applied to the blockchain and others are reverted (fork).
Check also that invalid transactions are not added to the mem pool.

Configuration:
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    - Add some transactions to the SC nodes. 
    - Disconnect the SC nodes and create 2 different sets of txs
    on the 2 nodes, so they have different txs in their mempool.
    - Forge a block on node 1.
    - Connect the SC nodes. Verify that node 2 mempool doesn't contain anymore the txs included in the block
    but still contains the txs created only on node 2.
    - Add some transactions to the SC nodes. 
    - Disconnect the SC nodes
    - On node 1 create some txs and then create a block
    - On node 2 create some txs and then create 2 blocks
    - Connect the SC nodes. Verify that the nodes are aligned and the block created on node 1 is reverted. 
    Verify that node 1 mem pool contains only the txs created on itself.
    
"""


class SCEvmMempoolInvalidTxs(SidechainTestFramework):

    sc_nodes_bootstrap_info = None
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1
    API_KEY = "Horizen"

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            api_key = self.API_KEY
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
                                         sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              auth_api_key=self.API_KEY,
                              binary=[EVM_APP_BINARY] * 2)#, extra_args=[['-agentlib'], []])

    def run_test(self):
        # Synchronize mc_node1, mc_node2 and mc_node3, then disconnect them.
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('3000.0')
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        #Test that a transaction with negative nonce is rejected by the mem pool
        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=None,
                                          nonce=-1, gasLimit=10000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=9000000000, value=1)
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction with with negative nonce had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction with with negative nonce should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction with negative nonce added to node 1 mempool")

        #Test that a transaction with negative value is rejected by the mem pool
        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=None,
                                          nonce=0, gasLimit=10000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=900000000, value=-1)
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction with with negative value had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction with with negative value should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction with negative value added to node 1 mempool")

        #Test that a transaction with max fee lower than priority fee is rejected by the mem pool
        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1,
                                          nonce=0, gasLimit=10000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=1, value=1)
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction  with max fee lower than priority fee had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction  with max fee lower than priority fee should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction  with max fee lower than priority fee added to node 1 mempool")

        # Test that a transaction with max fee with a value of more than 256 bits is rejected by the mem pool
        # TODO at the moment it is not possible to test this case, because createEIP1559Transaction looks for
        #  an account with a balance at least of (value + fee * limit) for signing it, but it is not possible
        #  to transfer more than about 3000 zen in the sidechain from the mainchain
        #tx with
        # very_large_num = random.getrandbits(257)
        # logging.info(very_large_num)
        # exception_occurs = False
        # try:
        #     createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1,
        #                                   nonce=0, gasLimit=1, maxPriorityFeePerGas=900000000,
        #                                     maxFeePerGas=very_large_num, value=1)
        # except RuntimeError as e:
        #     exception_occurs = True
        #     logging.info("Adding a transaction with max fee with more than 256 bits  had an exception as expected: {}".format(str(e)))
        #
        # assert_true(exception_occurs, "Adding a transaction with max fee with more than 256 bits should fail")
        # response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        # assert_equal(0, len(response['result']['transactionIds']), "Transaction with max fee with more than 256 bits added to node 1 mempool")

        # Test that a transaction with gas limit with more than 64 bits is rejected by the mem pool
        very_large_num = random.getrandbits(65)
        logging.info(very_large_num)
        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1,
                                          nonce=0, gasLimit=very_large_num, maxPriorityFeePerGas=1,
                                            maxFeePerGas=1, value=1)
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction with gas limit with more than 64 bits had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction with gas limit with more than 64 bits should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction with gas limit with more than 64 bitsadded to node 1 mempool")

        #Test that a transaction with gas limit greater that block gas limit is rejected by the mem pool
        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc2,
                                          nonce=0, gasLimit=(BLOCK_GAS_LIMIT + 10), maxPriorityFeePerGas=900000000, maxFeePerGas=900000000, value=1)
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction with gas limit > block gas limit had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction with gas limit > block gas limit should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction with gas limit > block gas limit added to node 1 mempool")

        #Test that a transaction that creates a smart contract (to = None) cannot have empty data
        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=None,
                                          nonce=0, gasLimit=100000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=900000000, value=1, data='')
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction that creates a smart contract with empty data had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction that creates a smart contract with empty data should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction that creates a smart contract with empty data added to node 1 mempool")


        #Test that a transaction with gas limit < intrinsic gas is rejected by the mem pool
        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=None,
                                          nonce=0, gasLimit=10, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=900000000, value=1, data='12344')
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction with gas limit < intrinsic gas had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction with gas limit < intrinsic gas should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction with gas limit < intrinsic gas added to node 1 mempool")

        #Test that a transaction with nonce too low is rejected by the mem pool
        nonce_addr_1 = 0
        for i in range(10):
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1,
                                          nonce=nonce_addr_1, gasLimit=230000, maxPriorityFeePerGas=900000000, maxFeePerGas=900000000, value=1)
            nonce_addr_1 += 1

        generate_next_block(sc_node_1, "first node")

        exception_occurs = False
        try:
            createEIP1559Transaction(sc_node_1, fromAddress=evm_address_sc1, toAddress=evm_address_sc1,
                                          nonce=1, gasLimit=100000, maxPriorityFeePerGas=900000000,
                                            maxFeePerGas=900000000, value=1)
        except RuntimeError as e:
            exception_occurs = True
            logging.info("Adding a transaction with nonce too low had an exception as expected: {}".format(str(e)))

        assert_true(exception_occurs, "Adding a transaction with nonce too low should fail")
        response = sc_node_1.transaction_allTransactions(json.dumps({"format": False}))
        assert_equal(0, len(response['result']['transactionIds']), "Transaction with nonce too low  added to node 1 mempool")


if __name__ == "__main__":
    SCEvmMempoolInvalidTxs().main()