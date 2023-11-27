#!/usr/bin/env python3
import json
import logging
import math
import time

from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake, format_eoa, format_evm
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei, convertZenToWei, \
    computeForgedTxFee, FORGER_POOL_RECIPIENT_ADDRESS
from SidechainTestFramework.sc_forging_util import check_mcreference_presence
from SidechainTestFramework.scutil import (
    connect_sc_nodes, generate_account_proposition, generate_next_block, SLOTS_IN_EPOCH, EVM_APP_SLOT_TIME,
    generate_next_blocks, )
from httpCalls.block.getFeePayments import http_block_getFeePayments
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain, assert_false, )

"""
Check Forger fee payments:
1. Forging using stakes of different SC nodes
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC nodes are connected to the MC node.
Test:
    - Do a required setup for 2 forger nodes
    - send a FT to a forger pool address
    - end withdrawal epoch, verify fee distribution
    - invalidate mc blocks to create a fork that will rollback fee distribution
    - send one more a FT to a forger pool address, increasing forger pool value
    - generate couple more mc and sc block, so that the counts per node are changed
    - verify fee distribution after the mc fork
"""


class ScEvmForgingFeePayments(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=20, forward_amount=3,
                         block_timestamp_rewind=SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 100)

    def advance_to_epoch(self, epoch_number: int):
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

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]
        self.advance_to_epoch(60)
        node_1_block_count = 1
        node_2_block_count = 0
        node_1_extra_fees = 0

        # Connect and sync SC nodes
        logging.info("Connecting sc nodes...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()

        # Do FT of some Zen to SC Node 2
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        ft_amount_in_zen = 2.0
        ft_amount_in_wei = convertZenToWei(ft_amount_in_zen)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)
        self.sync_all()
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")
        mc_node.generate(1)
        generate_next_block(sc_node_1, "first node")
        node_1_block_count += 1
        self.sc_sync_all()

        # initial node 2 balance is in wei
        initial_balance_2 = http_wallet_balance(sc_node_2, evm_address_sc_node_2)
        assert_equal(ft_amount_in_wei, initial_balance_2)

        # Create forger stake with some Zen for SC node 2
        sc2_block_sign_pub_key = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrf_pub_key = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]
        forger_stake_amount = 1  # Zen
        forger_stake_amount_in_wei = convertZenToWei(forger_stake_amount)
        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_2, evm_address_sc_node_2, sc2_block_sign_pub_key,
                                                    sc2_vrf_pub_key,
                                                    convertZenToZennies(forger_stake_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        tx_hash_0 = makeForgerStakeJsonRes['result']['transactionId']

        # Generate SC block
        generate_next_block(sc_node_1, "first node")
        node_1_block_count += 1

        transactionFee_0, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node_1, tx_hash_0)
        node_1_extra_fees += forgerTip

        logging.info(sc_node_2.wallet_getTotalBalance())

        # balance now is initial (ft) minus forgerStake and fee
        assert_equal(
            ft_amount_in_wei -
            (forger_stake_amount_in_wei + transactionFee_0),
            sc_node_2.wallet_getTotalBalance()['result']['balance']
        )
        # we now have 2 stakes, one from creation and one just added
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(2, len(stakeList))

        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        node_1_block_count += 1
        self.sc_sync_all()
        generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
        node_2_block_count += 1
        self.sc_sync_all()

        # Generate some MC block to reach the end of the withdrawal epoch
        mc_node.generate(self.withdrawalEpochLength - 4)
        generate_next_block(sc_node_2, "second node")
        node_2_block_count += 1
        self.sc_sync_all()

        # Do a FT to a forger pool and generate mc block with it
        ft_pool_amount = 0.5
        ft_pool_amount_wei = convertZenToWei(ft_pool_amount)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(FORGER_POOL_RECIPIENT_ADDRESS),
                                      ft_pool_amount,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)
        mc_block_with_ft_to_revert = mc_node.generate(1)[0]
        generate_next_block(sc_node_2, "second node")
        node_2_block_count += 1
        self.sc_sync_all()

        # assert Forger Pool balance is updated
        forger_pool_balance = int(self.sc_nodes[0].rpc_eth_getBalance(format_evm(FORGER_POOL_RECIPIENT_ADDRESS), 'latest')['result'], 16)
        assert_equal(forger_pool_balance, ft_pool_amount_wei)

        # end of the withdrawal epoch
        mc_block_last_of_the_epoch_to_revert = mc_node.generate(1)[0]

        # Collect SC node balances before fees redistribution
        sc_node_1_balance_before_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_before_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

        # Generate one more block with no fee by SC node 2 to reach the end of the withdrawal epoch
        sc_last_we_block_id = generate_next_block(sc_node_2, "second node")
        node_2_block_count += 1

        self.sc_sync_all()

        # assert Forger Pool balance is distributed
        forger_pool_balance = int(self.sc_nodes[0].rpc_eth_getBalance(format_evm(FORGER_POOL_RECIPIENT_ADDRESS), 'latest')['result'], 16)
        assert_equal(forger_pool_balance, 0)

        # regular fee includes 60 blocks before reaching the fork
        pool_fee = forgersPoolFee
        pool_average_fee = math.floor(pool_fee / (node_1_block_count + node_2_block_count + 59))
        pool_rem = pool_fee % (node_1_block_count + node_2_block_count + 59)
        node_1_pool_fee = (node_1_block_count + 59) * pool_average_fee + pool_rem
        node_2_pool_fee = (node_2_block_count) * pool_average_fee

        ft_pool_fee = ft_pool_amount_wei
        ft_average_fee = math.floor(ft_pool_fee / (node_1_block_count + node_2_block_count))
        node_1_fees = (ft_average_fee * node_1_block_count) + node_1_pool_fee + node_1_extra_fees
        node_2_fees = (ft_average_fee * node_2_block_count) + node_2_pool_fee

        sc_node_1_balance_after_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_after_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

        # Check forger fee payments
        assert_equal(sc_node_1_balance_before_payments + node_1_fees, sc_node_1_balance_after_payments,
                     "Wrong fee payment amount for SC node 1")
        assert_equal(sc_node_2_balance_before_payments + node_2_fees, sc_node_2_balance_after_payments,
                     "Wrong fee payment amount for SC node 2")

        fee_payments_api_response = http_block_getFeePayments(sc_node_1, sc_last_we_block_id)['feePayments']
        assert_equal(node_1_fees, fee_payments_api_response[0]['value'])
        assert_equal(node_2_fees, fee_payments_api_response[1]['value'])

        # Invalidate last 2 MC blocks - one that contained FT to forger pool and one that ended withdrawal epoch
        mc_node.invalidateblock(mc_block_with_ft_to_revert)
        mc_node.invalidateblock(mc_block_last_of_the_epoch_to_revert)
        time.sleep(5)

        # repeat the same FT and withdrawal epoch ending, this time doubling the FT amount
        ft_pool_amount = 1
        ft_pool_amount_wei += convertZenToWei(ft_pool_amount)
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(FORGER_POOL_RECIPIENT_ADDRESS),
                                      ft_pool_amount,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)
        mc_node.generate(3)
        self.sync_all()

        # Generate 2 more blocks with no fee by SC node 1 to reach the end of the withdrawal epoch
        generate_next_block(sc_node_1, "first node")
        sc_last_we_block_id = generate_next_block(sc_node_1, "first node")
        node_1_block_count += 2
        # because of rollback, 2 sc_node_2 blocks are invalidated
        node_2_block_count -= 2

        self.sc_sync_all()

        # assert Forger Pool balance is distributed
        forger_pool_balance = int(self.sc_nodes[0].rpc_eth_getBalance(format_evm(FORGER_POOL_RECIPIENT_ADDRESS), 'latest')['result'], 16)
        assert_equal(forger_pool_balance, 0)

        # recalculate fee with new block count and pool amount
        pool_fee = forgersPoolFee
        pool_average_fee = math.floor(pool_fee / (node_1_block_count + node_2_block_count + 59))
        pool_rem = pool_fee % (node_1_block_count + node_2_block_count + 59)
        node_1_pool_fee = (node_1_block_count + 59) * pool_average_fee + pool_rem
        node_2_pool_fee = node_2_block_count * pool_average_fee
        ft_pool_fee = ft_pool_amount_wei
        ft_average_fee = math.floor(ft_pool_fee / (node_1_block_count + node_2_block_count))
        node_1_fees = (ft_average_fee * node_1_block_count) + node_1_pool_fee + node_1_extra_fees
        node_2_fees = (ft_average_fee * node_2_block_count) + node_2_pool_fee

        sc_node_1_balance_after_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_after_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

        # Check forger fee payments
        assert_equal(sc_node_1_balance_before_payments + node_1_fees, sc_node_1_balance_after_payments,
                     "Wrong fee payment amount for SC node 1")
        assert_equal(sc_node_2_balance_before_payments + node_2_fees, sc_node_2_balance_after_payments,
                     "Wrong fee payment amount for SC node 2")

        fee_payments_api_response = http_block_getFeePayments(sc_node_1, sc_last_we_block_id)['feePayments']
        assert_equal(node_1_fees, fee_payments_api_response[0]['value'])
        assert_equal(node_2_fees, fee_payments_api_response[1]['value'])


if __name__ == "__main__":
    ScEvmForgingFeePayments().main()
