#!/usr/bin/env python3
import time
import logging
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from httpCalls.block.getFeePayments import http_block_getFeePayments
from test_framework.util import assert_equal, assert_true, websocket_port_by_mc_node_index,\
    forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, generate_next_blocks, start_sc_nodes
from httpCalls.wallet.balance import http_wallet_balance
from httpCalls.transaction.sendCoinsToAddress import sendCoinsToAddress
from httpCalls.wallet.reindex import http_wallet_reindex, http_wallet_reindex_status, http_debug_reindex_step

"""
The purpose of this test is to verify that a wallet reindex takes into consideration fee redeistribution boxes if 
they are present

Network Configuration:
    1 MC nodes, 1 SC node 

Workflow modelled in this test:
    McNode: send some money to SCNode1 (forward transfer)
    ScNode:
        -Spent some coins and pay fee.
        -Generate MC and SC blocks to complete  withdrawal epoch and generate the fee payments
        -Check fee payments have been included in the wallet balance
        -Reindex
        -Check the balance is the same as before

"""

class SidechainWalletReindexFeePayments(SidechainTestFramework):
    blocks = []
    number_of_sidechain_nodes = 1
    withdrawal_epoch_length = 10

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0))),
            cert_submitter_enabled=False,
            cert_signing_enabled=False
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, self.withdrawal_epoch_length), sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_nodes(self):
        # Start 1 SC node
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir)

    def run_test(self):
        epoch_mc_blocks_left = self.withdrawal_epoch_length - 1

        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        public_key1 = self.sc_nodes_bootstrap_info.genesis_account.publicKey
        self.sc_sync_all()

        # We need some free coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      public_key1,
                                      self.sc_nodes_bootstrap_info.genesis_account_balance,
                                      mc_node.getnewaddress())
        self.sc_sync_all()
        self.blocks.append(generate_next_blocks(sc_node, "", 1)[0])
        self.sc_sync_all()

        epoch_mc_blocks_left -= 1

        # Check that the wallet balance is doubled now (forging stake + the forward transfer)
        assert_equal(http_wallet_balance(sc_node),  (self.sc_nodes_bootstrap_info.genesis_account_balance * 2) * 100000000)


        # Send some coins to ourselves and pay fee
        fee = self.sc_nodes_bootstrap_info.genesis_account_balance / 10
        sendCoinsToAddress(sc_node, public_key1, self.sc_nodes_bootstrap_info.genesis_account_balance - fee, fee)
        assert_equal(1, len(sc_node.transaction_allTransactions()["result"]["transactions"]),
                     "1 Tx expected to be in the SC node mempool.")

        self.blocks.append(generate_next_blocks(sc_node, "", 1)[0])
        assert_equal(0, len(sc_node.transaction_allTransactions()["result"]["transactions"]),
                     "No Txs expected to be in the SC node mempool.")

        balance_pefore_feeRedistribution = http_wallet_balance(sc_node)

        # Generate MC blocks to reach the end of the withdrawal epoch
        mc_node.generate(epoch_mc_blocks_left)


        # Generate 1 SC block that should include all pending MC block references and lead to fee payment.
        self.blocks.append(generate_next_blocks(sc_node, "", 1)[0])
        sc_last_we_block_id = self.blocks[-1]


        # Check we received fee payments
        api_fee_payments = http_block_getFeePayments(sc_node, sc_last_we_block_id)['feePayments']
        updatedBalance = http_wallet_balance(sc_node)
        fee_received = updatedBalance - balance_pefore_feeRedistribution
        assert_true(len(api_fee_payments) == 1)
        assert_true(fee_received == api_fee_payments[0]['value'])

        logging.info("Start reindex on node  and wait till it is completed")
        reindexStarted = http_wallet_reindex(sc_node)
        assert_equal(reindexStarted, True)
        reindexStatus = http_wallet_reindex_status(sc_node)
        while reindexStatus != 'inactive' :
            time.sleep(1)
            reindexStatus = http_wallet_reindex_status(sc_node)

        logging.info("Check balance is unchanged (fee payments have been correctly taken into consideration again")
        assert_true(http_wallet_balance(sc_node) == updatedBalance)


if __name__ == "__main__":
    SidechainWalletReindexFeePayments().main()
