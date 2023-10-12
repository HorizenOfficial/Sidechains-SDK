#!/usr/bin/env python3
import json
import logging
import math
from decimal import Decimal

from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake, format_eoa
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei, convertZenToWei, \
    computeForgedTxFee, FORGER_POOL_RECIPIENT_ADDRESS
from SidechainTestFramework.sc_forging_util import check_mcreference_presence
from SidechainTestFramework.scutil import (
    connect_sc_nodes, generate_account_proposition, generate_next_block, )
from httpCalls.block.getFeePayments import http_block_getFeePayments
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain, )

"""
Info about forger account block fee payments
"""


class BlockFeeInfo(object):
    def __init__(self, node, baseFee, forgerTips):
        self.node = node
        self.baseFee = baseFee
        self.forgerTips = forgerTips


"""
Check Forger fee payments:
1. Forging using stakes of different SC nodes
Configuration:
    Start 1 MC node and 2 SC node (with default websocket configuration).
    SC nodes are connected to the MC node.
Test:
    - Do FT to the second SC node.
    - Sync SC and MC networks.
    - Delegate coins to forge to the second SC node using coins from the FT.
    - Forge SC block by the first SC node for the next consensus epoch.
    - Forge SC block by the second SC node for the next consensus epoch (Second node ForgingStake must become active).
    - Generate MC and SC blocks to reach the end of the withdrawal epoch. 
    - Check forger payments for the SC nodes.
"""


class ScEvmForgingFeePayments(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=20, forward_amount=1)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # Connect and sync SC nodes
        logging.info("Connecting sc nodes...")
        connect_sc_nodes(self.sc_nodes[0], 1)
        self.sc_sync_all()
        # Set the genesis SC block fee info
        sc_block_fee_info = [BlockFeeInfo(1, 0, 0)]

        # Do FT of some Zen to SC Node 2
        evm_address_sc_node_2 = sc_node_2.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = 2.0
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      evm_address_sc_node_2,
                                      ft_amount_in_zen,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)
        self.sync_all()

        assert_equal(1, mc_node.getmempoolinfo()["size"], "Forward Transfer expected to be added to mempool.")

        # Generate MC block and SC block
        mcblock_hash1 = mc_node.generate(1)[0]
        scblock_id1 = generate_next_block(sc_node_1, "first node")
        check_mcreference_presence(mcblock_hash1, scblock_id1, sc_node_1)
        # Update block fees: node 1 generated block with 0 fees.
        sc_block_fee_info.append(BlockFeeInfo(1, 0, 0))

        self.sc_sync_all()

        # balance is in wei
        initial_balance_2 = http_wallet_balance(sc_node_2, evm_address_sc_node_2)
        assert_equal(ft_amount_in_wei, initial_balance_2)
        logging.info(http_wallet_balance(sc_node_2, evm_address_sc_node_2))
        logging.info(sc_node_2.wallet_getTotalBalance())

        # Create forger stake with some Zen for SC node 2
        sc2_blockSignPubKey = sc_node_2.wallet_createPrivateKey25519()["result"]["proposition"]["publicKey"]
        sc2_vrfPubKey = sc_node_2.wallet_createVrfSecret()["result"]["proposition"]["publicKey"]

        forger_stake_amount = 0.015  # Zen
        forger_stake_amount_in_wei = convertZenToWei(forger_stake_amount)

        makeForgerStakeJsonRes = ac_makeForgerStake(sc_node_2, evm_address_sc_node_2, sc2_blockSignPubKey,
                                                    sc2_vrfPubKey,
                                                    convertZenToZennies(forger_stake_amount))
        if "result" not in makeForgerStakeJsonRes:
            fail("make forger stake failed: " + json.dumps(makeForgerStakeJsonRes))
        else:
            logging.info("Forger stake created: " + json.dumps(makeForgerStakeJsonRes))
        self.sc_sync_all()

        tx_hash_0 = makeForgerStakeJsonRes['result']['transactionId']

        # Generate SC block
        generate_next_block(sc_node_1, "first node")

        transactionFee_0, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node_1, tx_hash_0)

        logging.info(sc_node_2.wallet_getTotalBalance())

        # balance now is initial (ft) minus forgerStake and fee
        assert_equal(
            ft_amount_in_wei -
            (forger_stake_amount_in_wei + transactionFee_0),
            sc_node_2.wallet_getTotalBalance()['result']['balance']
        )

        sc_block_fee_info.append(BlockFeeInfo(1, forgersPoolFee, forgerTip))

        # we now have 2 stakes, one from creation and one just added
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(2, len(stakeList))

        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        sc_block_fee_info.append(BlockFeeInfo(1, 0, 0))

        self.sc_sync_all()

        recipient_keys = generate_account_proposition("seed3", 1, self.model)[0]
        recipient_proposition = recipient_keys.proposition

        # Create a legacy transaction moving some fund from SC2 address to an external address.
        transferred_amount_in_zen_1 = 0.022
        transferred_amount_in_wei_1 = convertZenToWei(transferred_amount_in_zen_1)

        tx_hash_1 = createLegacyTransaction(sc_node_2,
                      fromAddress=evm_address_sc_node_2,
                      toAddress=recipient_proposition,
                      value=transferred_amount_in_wei_1
        )
        self.sc_sync_all()

        # Generate SC block on SC node 1
        sc_middle_we_block_id = generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        transactionFee_1, forgersPoolFee_1, forgerTip_1 = computeForgedTxFee(sc_node_1, tx_hash_1)
        sc_block_fee_info.append(BlockFeeInfo(1, forgersPoolFee_1, forgerTip_1))

        # Create a eip1559 transaction moving some fund from SC2 address to an external address.
        # This also tests a too high maxPriorityFee value, which should be capped using maxFeePerGas
        transferred_amount_in_zen_2 = 0.001
        transferred_amount_in_wei_2 = convertZenToWei(transferred_amount_in_zen_2)

        blockJson = sc_node_2.rpc_eth_getBlockByHash(add_0x_prefix(sc_middle_we_block_id), False)['result']
        if blockJson is None:
            raise Exception('Unexpected error: block not found {}'.format(sc_middle_we_block_id))
        baseFeePerGas = int(blockJson['baseFeePerGas'], 16)

        tx_hash_2 = createEIP1559Transaction(sc_node_2, fromAddress=evm_address_sc_node_2,
                                             toAddress=recipient_proposition,
                                             gasLimit=123400, maxPriorityFeePerGas=56700,
                                             maxFeePerGas=baseFeePerGas + 56690,
                                             value=transferred_amount_in_wei_2)
        self.sc_sync_all()

        # Generate SC block on SC node 2 for the next consensus epoch
        sc_middle_we_block_id = generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        transactionFee_2, forgersPoolFee_2, forgerTip_2 = computeForgedTxFee(sc_node_1, tx_hash_2)
        sc_block_fee_info.append(BlockFeeInfo(2, forgersPoolFee_2, forgerTip_2))

        self.sc_sync_all()

        # let assume a portion of the MC coinbase is sent to the SC as a contribution to the forger pool
        ft_pool_amount = 3.5
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      format_eoa(FORGER_POOL_RECIPIENT_ADDRESS),
                                      ft_pool_amount,
                                      mc_return_address=mc_node.getnewaddress(),
                                      generate_block=False)

        # Generate some MC block to reach the end of the withdrawal epoch
        mc_node.generate(self.withdrawalEpochLength - 2)

        # balance now is initial (ft) without forgerStake and fee and without transferred amounts and fees
        assert_equal(
            ft_amount_in_wei -
            (forger_stake_amount_in_wei + transactionFee_0) -
            (transferred_amount_in_wei_1 + transactionFee_1) -
            (transferred_amount_in_wei_2 + transactionFee_2),
            sc_node_2.wallet_getTotalBalance()['result']['balance']
        )

        # Collect SC node balances before fees redistribution
        sc_node_1_balance_before_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_before_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

        # Generate one more block with no txes by SC node 2, it will include the MC block references one of which is
        # carrying along the FT for the forger pool
        sc_last_we_block_id = generate_next_block(sc_node_2, "second node")
        sc_block_fee_info.append(BlockFeeInfo(2, convertZenToWei(ft_pool_amount), 0))

        self.sc_sync_all()

        # Collect fee values
        total_fee = 0
        pool_fee = 0
        forger_tips = 0

        for sc_block_fee in sc_block_fee_info:
            total_fee += sc_block_fee.baseFee + sc_block_fee.forgerTips
            forger_tips += sc_block_fee.forgerTips
            pool_fee += sc_block_fee.baseFee

        logging.info("pool fee    = {}".format(pool_fee))
        logging.info("forger tips = {}".format(forger_tips))
        logging.info("---------------------------------------")
        logging.info("total fee   = {}".format(total_fee))


        average_fee = math.floor(pool_fee // len(sc_block_fee_info))
        forger_fees = {}

        for idx, sc_block_fee in enumerate(sc_block_fee_info):
            if sc_block_fee.node in forger_fees:
                forger_fees[sc_block_fee.node] += sc_block_fee.forgerTips
            else:
                forger_fees[sc_block_fee.node] = sc_block_fee.forgerTips

            forger_fees[sc_block_fee.node] += average_fee

            if idx < pool_fee % len(sc_block_fee_info):
                forger_fees[sc_block_fee.node] += 1

            logging.info("block {} fees: {}, {}".format(idx, sc_block_fee.baseFee, sc_block_fee.forgerTips))

        sc_node_1_balance_after_payments = sc_node_1.wallet_getTotalBalance()['result']['balance']
        sc_node_2_balance_after_payments = sc_node_2.wallet_getTotalBalance()['result']['balance']

        node_1_fees = forger_fees[1]
        node_2_fees = forger_fees[2]

        # Check forger fee payments
        logging.info("SC1 bal before = {}, after = {}".format(sc_node_1_balance_before_payments,
                                                              sc_node_1_balance_after_payments))
        logging.info("SC2 bal before = {}, after = {}".format(sc_node_2_balance_before_payments,
                                                              sc_node_2_balance_after_payments))

        logging.info("SC1 forger fees = {}".format(node_1_fees))
        logging.info("SC2 forger fees = {}".format(node_2_fees))

        assert_equal(node_1_fees + node_2_fees, total_fee)

        # Check forger fee payments
        assert_equal(sc_node_1_balance_before_payments + node_1_fees, sc_node_1_balance_after_payments,
                     "Wrong fee payment amount for SC node 1")
        assert_equal(sc_node_2_balance_before_payments + node_2_fees, sc_node_2_balance_after_payments,
                     "Wrong fee payment amount for SC node 2")

        # Check fee payments from API perspective
        # Non-last block of the epoch:
        api_fee_payments_node1 = http_block_getFeePayments(sc_node_1, sc_middle_we_block_id)['feePayments']
        logging.info(api_fee_payments_node1)

        assert_equal(0, len(api_fee_payments_node1),
                     "No fee payments expected to be found for the block in the middle of WE")

        api_fee_payments_node2 = http_block_getFeePayments(sc_node_2, sc_middle_we_block_id)['feePayments']
        logging.info(api_fee_payments_node2)

        assert_equal(0, len(api_fee_payments_node2),
                     "No fee payments expected to be found for the block in the middle of WE")

        # Last block of the epoch:
        api_fee_payments_node1 = http_block_getFeePayments(sc_node_1, sc_last_we_block_id)['feePayments']
        api_fee_payments_node2 = http_block_getFeePayments(sc_node_2, sc_last_we_block_id)['feePayments']
        logging.info(api_fee_payments_node1)
        logging.info(api_fee_payments_node2)

        assert_equal(api_fee_payments_node1, api_fee_payments_node2,
                     "SC nodes have different view on the fee payments")

        for i in range(1, len(forger_fees) + 1):
            assert_equal(forger_fees[i], api_fee_payments_node1[i - 1]['value'],
                         "Different fee value found for payment " + str(i))


if __name__ == "__main__":
    ScEvmForgingFeePayments().main()
