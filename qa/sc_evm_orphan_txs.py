#!/usr/bin/env python3
import json
import logging
from decimal import Decimal

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.utils import convertZenToZennies
from SidechainTestFramework.scutil import generate_next_block
from httpCalls.transaction.allTransactions import allTransactions
from test_framework.util import assert_equal, assert_true, forward_transfer_to_sidechain, fail, assert_false

"""
Test that the Sidechain can manage orphan transactions correctly
Configuration: 
    - 2 SC nodes connected with each other
    - 1 MC node

Test:
    - Create an orphan tx and test that it is included in the mempool but is not included in a block
    - Create the missing transaction and check that now both are included in a block
    - Check that the transactions in a block are ordered by effective gas tip
    - Check that a transaction with the same nonce of a tx already in the mempool can replace the old one just if it
    has an higher effective gas tip
     
"""


class SCEvmOrphanTXS(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2)

    def run_test(self):

        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('500.0')

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc1,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        # Generate SC block and check that FT appears in SCs node wallet
        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        logging.info("Create an orphan transaction and check it is not included in a block...")
        transferred_amount_in_zen = Decimal('11')
        # Amount should be expressed in zennies
        amount_in_zennies = convertZenToZennies(transferred_amount_in_zen)

        j = {
            "from": evm_address_sc1,
            "to": evm_address_sc2,
            "value": amount_in_zennies,
        }

        j["nonce"] = 1

        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))

        orphan_tx_hash = response['result']["transactionId"]
        logging.info(orphan_tx_hash)
        self.sc_sync_all()

        # get mempool contents and check contents are as expected
        response = allTransactions(sc_node_1, False)
        assert_true(orphan_tx_hash in response["transactionIds"])

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        txs_in_block = sc_node_1.block_best()["result"]["block"]["sidechainTransactions"]
        assert_equal(0, len(txs_in_block), "Orphan transaction shouldn't be included in the block")
        # Check it is still in the mempool
        response = allTransactions(sc_node_1, False)
        assert_true(orphan_tx_hash in response["transactionIds"])

        logging.info("Create the missing transaction and check that now both are included in a block...")
        j["nonce"] = 0

        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))

        tx_hash_nonce_0 = response['result']["transactionId"]

        self.sc_sync_all()

        # get mempool contents and check contents are as expected
        response = allTransactions(sc_node_1, False)
        assert_true(tx_hash_nonce_0 in response["transactionIds"])

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        txs_in_block = sc_node_1.block_best()["result"]["block"]["sidechainTransactions"]
        assert_equal(2, len(txs_in_block), "Wrong number of transactions in the block")
        assert_equal(tx_hash_nonce_0, txs_in_block[0]['id'], "Wrong first tx")
        assert_equal(orphan_tx_hash, txs_in_block[1]['id'], "Wrong second tx")

        # Check the mempool is empty
        response = allTransactions(sc_node_1, False)
        assert_equal(0, len(response["transactionIds"]))

        # Check that the transactions with the highest effective gas tip are included first in the block
        # The expected order is: txC_0, txC_1, txC_2, txB_0, txA_0, txB_1, txB_2, txA_1, txA_2

        evm_address_scA = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_scB = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_scC = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_scA,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_scB,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_scC,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=True)
        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        j = {
            "to": evm_address_sc2,
            "value": 1,
            "gasInfo": {
                "gasLimit": 230000
            }
        }

        j["from"] = evm_address_scA
        j["nonce"] = 0
        j["gasInfo"]["maxFeePerGas"] = 900000003
        j["gasInfo"]["maxPriorityFeePerGas"] = 900000003
        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))
        txA_0 = response['result']['transactionId']

        j["nonce"] = 1
        j["gasInfo"]["maxFeePerGas"] = 900000001
        j["gasInfo"]["maxPriorityFeePerGas"] = 900000001
        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))
        txA_1 = response['result']['transactionId']

        txA_2 = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_scA, toAddress=evm_address_sc2,
                                         nonce=2, gasLimit=230000, maxPriorityFeePerGas=900000110,
                                         maxFeePerGas=900001100, value=1)

        j["from"] = evm_address_scB
        j["nonce"] = 0
        j["gasInfo"]["maxFeePerGas"] = 900000005
        j["gasInfo"]["maxPriorityFeePerGas"] = 900000005
        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))
        txB_0 = response['result']['transactionId']

        txB_1 = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_scB, toAddress=evm_address_sc2,
                                         nonce=1, gasLimit=230000, maxPriorityFeePerGas=900000002,
                                         maxFeePerGas=900000002, value=1)

        j["nonce"] = 2
        j["gasInfo"]["maxFeePerGas"] = 900000190
        j["gasInfo"]["maxPriorityFeePerGas"] = 900000190
        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))
        txB_2 = response['result']['transactionId']

        txC_0 = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_scC, toAddress=evm_address_sc2,
                                         nonce=0, gasLimit=230000, maxPriorityFeePerGas=900000010,
                                         maxFeePerGas=900000100, value=1)

        txC_1 = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_scC, toAddress=evm_address_sc2,
                                         nonce=1, gasLimit=230000, maxPriorityFeePerGas=900000200,
                                         maxFeePerGas=900002000, value=1)
        txC_2 = createEIP1559Transaction(sc_node_1, fromAddress=evm_address_scC, toAddress=evm_address_sc2,
                                         nonce=2, gasLimit=230000, maxPriorityFeePerGas=900000006,
                                         maxFeePerGas=900000060, value=1)

        self.sc_sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        txs_in_block = sc_node_1.block_best()["result"]["block"]["sidechainTransactions"]

        logging.info(txs_in_block)
        assert_equal(9, len(txs_in_block), "Wrong number of transactions in the block")
        assert_equal(txC_0, txs_in_block[0]['id'])
        assert_equal(txC_1, txs_in_block[1]['id'])
        assert_equal(txC_2, txs_in_block[2]['id'])
        assert_equal(txB_0, txs_in_block[3]['id'])
        assert_equal(txA_0, txs_in_block[4]['id'])
        assert_equal(txB_1, txs_in_block[5]['id'])
        assert_equal(txB_2, txs_in_block[6]['id'])
        assert_equal(txA_1, txs_in_block[7]['id'])
        assert_equal(txA_2, txs_in_block[8]['id'])

        # Check that a transaction with the same nonce of a tx already in the mempool can replace the old one just if it
        # has an higher effective gas tip

        j["from"] = evm_address_scA
        j["nonce"] = 3
        j["gasInfo"]["maxFeePerGas"] = 900000000
        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))
        oldTxId = response['result']['transactionId']

        # check mempool contains oldTxId
        response = allTransactions(sc_node_1, False)
        assert_true(oldTxId in response["transactionIds"])

        j["gasInfo"]["maxFeePerGas"] = 900000500
        response = sc_node_1.transaction_sendCoinsToAddress(json.dumps(j))
        if not 'result' in response:
            fail("send failed: " + str(response))
        newTxId = response['result']['transactionId']

        # check mempool contains newTxId
        response = allTransactions(sc_node_1, False)
        assert_false(oldTxId in response["transactionIds"])
        assert_true(newTxId in response["transactionIds"])

        self.sc_sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        txs_in_block = sc_node_1.block_best()["result"]["block"]["sidechainTransactions"]
        assert_equal(1, len(txs_in_block), "Wrong number of transactions in the block")
        assert_equal(newTxId, txs_in_block[0]['id'])


if __name__ == "__main__":
    SCEvmOrphanTXS().main()
