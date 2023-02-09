#!/usr/bin/env python3
import json
import logging
import time

from eth_utils import add_0x_prefix, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import generate_block_and_get_tx_receipt
from SidechainTestFramework.account.httpCalls.transaction.allWithdrawRequests import all_withdrawal_requests
from SidechainTestFramework.account.httpCalls.transaction.withdrawCoins import withdrawcoins
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import convertZenToZennies, convertZenniesToWei, computeForgedTxFee, \
    convertWeiToZen
from SidechainTestFramework.scutil import generate_next_block
from test_framework.util import (
    assert_equal, assert_true, fail, )

"""
Checks withdrawal requests creation in special cases:
- Insufficient balance
- Invalid zen amount (under the dust threshold)
- Too many WR per epoch

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

Note:
    This test can be executed in two modes:
    1. using no key rotation circuit (by default)
    2. using key rotation circuit (with --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation)
    With key rotation circuit can be executed in two modes:
    1. ceasing (by default)
    2. non-ceasing (with --nonceasing flag)

Test:
    For the SC node:
        - Checks that MC block with sc creation tx is referenced in the genesis sc block
        - verifies that there are no withdrawal requests yet
        - creates new forward transfer to sidechain
        - verifies that the receiving account balance is changed
        - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
        - generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
        - check epoch 0 certificate with not backward transfers in the MC mempool
        - mine 1 more MC block and forge 1 more SC block, check Certificate inclusion into SC block
        - make 2 different withdrawals from SC
        - reach next withdrawal epoch and verify that certificate for epoch 1 was added to MC mempool
          and then to MC/SC blocks.
        - verify epoch 1 certificate, verify backward transfers list    
"""


class SCEvmBWTCornerCases(AccountChainSetup):
    def __init__(self):
        super().__init__(withdrawalEpochLength=10)

    def run_test(self):
        time.sleep(0.1)

        ft_amount_in_zen = 10
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        evm_hex_addr = remove_0x_prefix(self.evm_address)
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # verifies that there are no withdrawal requests yet
        current_epoch_number = 0
        list_of_wr = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(0, len(list_of_wr))

        new_balance = http_wallet_balance(sc_node, evm_hex_addr)
        assert_equal(ft_amount_in_wei, new_balance, "wrong balance")

        initial_balance_in_wei = ft_amount_in_wei

        # *************** Test 1: Insufficient balance *****************
        # Try a withdrawal request with insufficient balance, the tx should not be created
        mc_address1 = mc_node.getnewaddress()
        bt_amount = ft_amount_in_zen + 3
        sc_bt_amount1 = convertZenToZennies(bt_amount)
        error_occur = False
        try:
            withdrawcoins(sc_node, mc_address1, sc_bt_amount1)
        except RuntimeError as e:
            error_occur = True

        assert_true(error_occur, "Withdrawal request with insufficient balance should fail")

        generate_next_block(sc_node, "first node")

        # verifies that there are no withdrawal requests
        current_epoch_number = 0
        list_of_wr = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(0, len(list_of_wr))

        # verifies that the balance didn't change
        new_balance = http_wallet_balance(sc_node, evm_hex_addr)
        assert_equal(initial_balance_in_wei, new_balance, "wrong balance")

        # *************** Test 2: Withdrawal amount under dust threshold *****************
        # Try a withdrawal request
        # with amount under dust threshold (54 zennies), wr should not be created but the tx should be created
        bt_amount_in_zennies = 53
        res = withdrawcoins(sc_node, mc_address1, bt_amount_in_zennies)
        tx_id = add_0x_prefix(res["result"]["transactionId"])

        # Checking the receipt
        receipt = generate_block_and_get_tx_receipt(sc_node, tx_id)
        status = int(receipt['result']['status'], 16)
        assert_equal(0, status, "Wrong tx status in receipt")

        assert_equal(0, len(receipt['result']['logs']), "Wrong number of events in receipt")

        # verifies that there are no withdrawal requests
        list_of_wr = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(0, len(list_of_wr))

        # verifies that the balance didn't change except for the consumed gas
        new_balance = http_wallet_balance(sc_node, evm_hex_addr)
        # Retrieve how much gas was spent
        gas_fee_paid, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_id)

        assert_equal(initial_balance_in_wei - gas_fee_paid, new_balance, "wrong balance")

        # Verifies there is the transaction in the block

        tx_in_block = sc_node.block_best()["result"]["block"]["sidechainTransactions"][0]["id"]
        assert_equal(remove_0x_prefix(tx_id), tx_in_block, "Tx is not in the block")

        # *************** Test 3: Withdrawal amount not valid zen amount (e.g. 1 wei) *****************
        bt_amount_in_wei = 1
        bt_amount_in_zen = convertWeiToZen(bt_amount_in_wei)
        bt_amount_in_zennies = convertZenToZennies(bt_amount_in_zen)
        res = withdrawcoins(sc_node, mc_address1, bt_amount_in_zennies)
        tx_id = add_0x_prefix(res["result"]["transactionId"])

        # Checking the receipt
        receipt = generate_block_and_get_tx_receipt(sc_node, tx_id)
        status = int(receipt['result']['status'], 16)
        assert_equal(0, status, "Wrong tx status in receipt")

        assert_equal(0, len(receipt['result']['logs']), "Wrong number of events in receipt")

        # verifies that there are no withdrawal requests
        list_of_wr = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(0, len(list_of_wr))

        # verifies that the balance didn't change except for the consumed gas
        old_balance = new_balance
        new_balance = http_wallet_balance(sc_node, evm_hex_addr)
        # Retrieve how much gas was spent
        gas_fee_paid, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_id)

        assert_equal(old_balance - gas_fee_paid, new_balance, "wrong balance")

        # Verifies there is the transaction in the block

        tx_in_block = sc_node.block_best()["result"]["block"]["sidechainTransactions"][0]["id"]
        assert_equal(remove_0x_prefix(tx_id), tx_in_block, "Tx is not in the block")

        # *************** Test 4: Number of Withdrawal requests per epoch too big (e.g. > 3999)  *****************
        num_of_wr = 3999
        bt_amount_in_zennies = 100
        mc_address2 = mc_node.getnewaddress()

        # Creates 3999 WR in different blocks in the same epoch, it should succeed. There is currently a limit of
        # 1000 txs per block

        tx_ids = []
        num_of_blocks = 14
        num_of_wr_per_block = int(num_of_wr / num_of_blocks)
        for b in range(1, num_of_blocks + 1):
            start = (num_of_wr_per_block * (b - 1)) + 1
            end = num_of_wr_per_block * b + 1
            for i in range(start, end):
                res = withdrawcoins(sc_node, mc_address2, bt_amount_in_zennies, i + 1)
                tx_ids.append(add_0x_prefix(res["result"]["transactionId"]))
                if "error" in res:
                    fail(f"Creating Withdrawal request {i} failed: " + json.dumps(res))
                else:
                    if i % 10 == 0:
                        logging.info(f"Created Withdrawal request {i}")
            generate_next_block(sc_node, "first node")

        num_of_created_wr = num_of_blocks * num_of_wr_per_block

        for i in range(num_of_created_wr + 1, num_of_wr + 1):
            res = withdrawcoins(sc_node, mc_address2, bt_amount_in_zennies, i + 1)
            tx_ids.append(add_0x_prefix(res["result"]["transactionId"]))
            if "error" in res:
                fail(f"Creating Withdrawal request {i} failed: " + json.dumps(res))
            else:
                logging.info(f"Created Withdrawal request {i}")

        generate_next_block(sc_node, "first node")

        # verifies that there are 3999 withdrawal requests
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(num_of_wr, len(list_of_WR))


        gas_fee = 0
        for tx_id in tx_ids:
            gas_fee_paid, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_id)
            gas_fee += gas_fee_paid

        # verifies that the balance changed
        old_balance = new_balance
        new_balance = http_wallet_balance(sc_node, evm_hex_addr)
        exp_new_balance_in_zennies = bt_amount_in_zennies * num_of_wr
        exp_new_balance_in_wei = old_balance - convertZenniesToWei(exp_new_balance_in_zennies) - gas_fee
        assert_equal(exp_new_balance_in_wei, new_balance, "wrong balance")

        # Tries to create another withdrawal request in the same epoch. Wr should not be created but the tx should be
        # created
        tx_id = add_0x_prefix(withdrawcoins(sc_node, mc_address1, bt_amount_in_zennies)["result"]["transactionId"])

        generate_next_block(sc_node, "first node")
        # verifies that there are no withdrawal requests
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(num_of_wr, len(list_of_WR))

        # verifies that the balance didn't change

        gas_fee_paid, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_id)
        new_balance = http_wallet_balance(sc_node, evm_hex_addr)
        assert_equal(exp_new_balance_in_wei - gas_fee_paid, new_balance, "wrong balance")

        # Verifies there is the transaction in the block

        tx_in_block = sc_node.block_best()["result"]["block"]["sidechainTransactions"][0]["id"]
        assert_equal(tx_in_block, remove_0x_prefix(tx_id), "Tx is not in the block")


if __name__ == "__main__":
    SCEvmBWTCornerCases().main()
