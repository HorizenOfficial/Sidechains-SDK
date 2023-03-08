#!/usr/bin/env python3
import json
import logging
import math

from eth_utils import add_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import ac_makeForgerStake
from SidechainTestFramework.account.httpCalls.transaction.createEIP1559Transaction import createEIP1559Transaction
from SidechainTestFramework.account.httpCalls.transaction.createLegacyTransaction import createLegacyTransaction
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import (computeForgedTxFee, convertZenToWei, convertZenToZennies)
from SidechainTestFramework.sc_forging_util import check_mcreference_presence
from SidechainTestFramework.scutil import (
    generate_account_proposition, generate_next_block)
from test_framework.util import (
    assert_equal, fail, forward_transfer_to_sidechain)


class BlockFeeInfo(object):
    def __init__(self, node, base_fee, forger_tips):
        self.node = node
        self.baseFee = base_fee
        self.forgerTips = forger_tips


class ScEvmFeePaymentsRpc(AccountChainSetup):
    # rewind sc genesis block timestamp for 5 consensus epochs
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2, withdrawalEpochLength=20, forward_amount=1,
                         block_timestamp_rewind=720 * 120 * 5)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]
        sc_node_2 = self.sc_nodes[1]

        # Set the genesis SC block fee info
        sc_block_fee_info = [BlockFeeInfo(1, 0, 0)]

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
                                                    sc2_vrfPubKey, convertZenToZennies(forger_stake_amount))
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
            sc_node_2.wallet_getTotalBalance()['result']['balance'],
            ft_amount_in_wei -
            (forger_stake_amount_in_wei + transactionFee_0)
        )

        sc_block_fee_info.append(BlockFeeInfo(1, forgersPoolFee, forgerTip))

        # we now have 2 stakes, one from creation and one just added
        stakeList = sc_node_1.transaction_allForgingStakes()["result"]['stakes']
        assert_equal(len(stakeList), 2)

        # Generate SC block on SC node 1 for the next consensus epoch
        generate_next_block(sc_node_1, "first node", force_switch_to_next_epoch=True)
        sc_block_fee_info.append(BlockFeeInfo(1, 0, 0))

        self.sc_sync_all()

        recipient_keys = generate_account_proposition("seed3", 1)[0]
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
        sc_middle_we_block_height = sc_node_1.block_best()["result"]["height"]
        self.sc_sync_all()

        transactionFee_1, forgersPoolFee_1, forgerTip_1 = computeForgedTxFee(sc_node_1, tx_hash_1)
        sc_block_fee_info.append(BlockFeeInfo(1, forgersPoolFee_1, forgerTip_1))

        # Create an eip1559 transaction moving some fund from SC2 address to an external address.
        # This also tests a too high maxPriorityFee value, which should be capped using maxFeePerGas
        transferred_amount_in_zen_2 = 0.001
        transferred_amount_in_wei_2 = convertZenToWei(transferred_amount_in_zen_2)

        blockJson = sc_node_2.rpc_eth_getBlockByHash(add_0x_prefix(sc_middle_we_block_id), False)['result']
        if (blockJson is None):
            raise Exception('Unexpected error: block not found {}'.format(sc_middle_we_block_id))
        baseFeePerGas = int(blockJson['baseFeePerGas'], 16)

        tx_hash_2 = createEIP1559Transaction(sc_node_2,
            fromAddress=evm_address_sc_node_2,
            toAddress=recipient_proposition,
            gasLimit=123400,
            maxPriorityFeePerGas=56700,
            maxFeePerGas=baseFeePerGas + 56690,
            value=transferred_amount_in_wei_2)
        # self.sc_sync_all()

        # Generate SC block on SC node 2 for the next consensus epoch
        sc_middle_we_block_id = generate_next_block(sc_node_2, "second node", force_switch_to_next_epoch=True)
        self.sc_sync_all()

        transactionFee_2, forgersPoolFee_2, forgerTip_2 = computeForgedTxFee(sc_node_1, tx_hash_2)
        sc_block_fee_info.append(BlockFeeInfo(2, forgersPoolFee_2, forgerTip_2))

        self.sc_sync_all()

        # Generate some MC block to reach the end of the withdrawal epoch
        mc_node.generate(self.withdrawalEpochLength - 2)

        # balance now is initial (ft) without forgerStake and fee and without transferred amounts and fees
        assert_equal(
            sc_node_2.wallet_getTotalBalance()['result']['balance'],
            ft_amount_in_wei -
            (forger_stake_amount_in_wei + transactionFee_0) -
            (transferred_amount_in_wei_1 + transactionFee_1) -
            (transferred_amount_in_wei_2 + transactionFee_2)
        )

        # Generate one more block with no fee by SC node 2 to reach the end of the withdrawal epoch
        sc_last_we_block_id = generate_next_block(sc_node_2, "second node")
        sc_block_fee_info.append(BlockFeeInfo(2, 0, 0))

        self.sc_sync_all()

        # Collect fee values
        total_fee = 0
        pool_fee = 0.0
        forger_fees = {}
        for sc_block_fee in sc_block_fee_info:
            total_fee += sc_block_fee.baseFee + sc_block_fee.forgerTips
            pool_fee += sc_block_fee.baseFee

        for idx, sc_block_fee in enumerate(sc_block_fee_info):
            if sc_block_fee.node in forger_fees:
                forger_fees[sc_block_fee.node] += sc_block_fee.forgerTips
            else:
                forger_fees[sc_block_fee.node] = sc_block_fee.forgerTips

            forger_fees[sc_block_fee.node] += math.floor(pool_fee / len(sc_block_fee_info))

            if idx < pool_fee % len(sc_block_fee_info):
                forger_fees[sc_block_fee.node] += 1

        forger_data = sc_node_1.rpc_zen_getFeePayments(add_0x_prefix(sc_middle_we_block_id))
        assert_equal(forger_data["result"], None)

        forger_data = sc_node_1.rpc_zen_getFeePayments(sc_middle_we_block_height)
        assert_equal(forger_data["result"], None)

        exp_forger_address_1 = add_0x_prefix(stakeList[0]["forgerStakeData"]["ownerPublicKey"]["address"])
        exp_forger_address_2 = add_0x_prefix(stakeList[1]["forgerStakeData"]["ownerPublicKey"]["address"])

        exp_forger_fee_1 = hex(forger_fees[1])
        exp_forger_fee_2 = hex(forger_fees[2])

        # Test with block hash
        (forger_data_1, forger_data_2) = sc_node_1.rpc_zen_getFeePayments(add_0x_prefix(sc_last_we_block_id))["result"]["payments"]

        assert_equal(exp_forger_address_1, forger_data_1["address"])
        assert_equal(exp_forger_address_2, forger_data_2["address"])

        assert_equal(forger_data_1["value"], exp_forger_fee_1)
        assert_equal(forger_data_2["value"], exp_forger_fee_2)

        # Test with block number
        sc_last_we_block_height = sc_node_1.block_best()["result"]["height"]
        (forger_data_1, forger_data_2) = sc_node_1.rpc_zen_getFeePayments(sc_last_we_block_height)["result"]["payments"]

        assert_equal(exp_forger_address_1, forger_data_1["address"])
        assert_equal(exp_forger_address_2, forger_data_2["address"])

        assert_equal(exp_forger_fee_1, forger_data_1["value"])
        assert_equal(exp_forger_fee_2, forger_data_2["value"])

        # Test with tag
        (forger_data_1, forger_data_2) = sc_node_1.rpc_zen_getFeePayments("latest")["result"]["payments"]

        assert_equal(exp_forger_fee_1, forger_data_1["value"])
        assert_equal(exp_forger_fee_2, forger_data_2["value"])

        assert_equal(exp_forger_fee_1, forger_data_1["value"])
        assert_equal(exp_forger_fee_2, forger_data_2["value"])


if __name__ == "__main__":
    ScEvmFeePaymentsRpc().main()
