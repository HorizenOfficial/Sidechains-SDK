#!/usr/bin/env python3
import time

from eth_utils import add_0x_prefix, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import generate_block_and_get_tx_receipt
from SidechainTestFramework.account.httpCalls.transaction.allWithdrawRequests import all_withdrawal_requests
from SidechainTestFramework.account.httpCalls.transaction.withdrawCoins import withdrawcoins
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.scutil import (
    computeForgedTxFee, convertZenToZennies, convertZenniesToWei, generate_next_block, )
from test_framework.util import (
    assert_equal, assert_true, )

"""
Checks withdrawal requests creation in special cases:
- Insufficient balance
- Invalid zen amount (under the dust threshold)
- Too many WR per epoch

Configuration:
    Start 1 MC node and 1 SC node (with default websocket configuration).
    SC node connected to the first MC node.

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
        # Try a withdrawal request with amount under dust threshold (54 zennies), wr should not be created but the tx should be created
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

        # *************** Test 3: Withdrawal amount not valid zen amount (e.g. 1 wei) TODO *****************
        # *************** Test 4: Test all_withdrawal_requests with a value >0. Being non-payable, it should fail TODO *****************

        # *************** Test 5: Number of Withdrawal requests per epoch too big (e.g. > 3999)  *****************
        # TODO this test fails do to a bug releted to endianess that is solved in UTXO sidechain but still to be released in Evm
        # TODO checking receipts and gas spent calculation
        # The test should be completed after the fix.
        # Please note that the checks on the balance after the WR are wrong because sometimes zennies/zen are used instead to wei. To be fixed.
        # Note 2: the amount of wr per block should be > 1000 for testing the fix on the forger in case the num of tx limit is crossed
        # num_of_wr = 3999
        # bt_amount_in_zennies = 100
        # mc_address2 = mc_node.getnewaddress()

        # Creates 3999 WR in different blocks in the same epoch, it should succeed. There is currently a limit of 1000 txs per block


#      num_of_blocks = 10
#      num_of_wr_per_block = int(num_of_wr/num_of_blocks)
#      for b in range (1, num_of_blocks + 1):
#          start = (num_of_wr_per_block * (b - 1)) + 1
#          end = num_of_wr_per_block * b + 1
#          for i in range(start, end):
#              res = withdrawcoins(sc_node, mc_address2, bt_amount_in_zennies, i)
#              if "error" in res:
#                  fail(f"Creating Withdrawal request {i} failed: " + json.dumps(res))
#              else:
#                  if i % 10 == 0:
#                      logging.info(f"Created Withdrawal request {i}")
#          generate_next_block(sc_node, "first node")
#
#      num_of_created_wr = num_of_blocks * num_of_wr_per_block
#
#      for i in range(num_of_created_wr + 1, num_of_wr + 1 ):
#          res = withdrawcoins(sc_node, mc_address2, bt_amount_in_zennies, i)
#          if "error" in res:
#              fail(f"Creating Withdrawal request {i} failed: " + json.dumps(res))
#          else:
#              logging.info(f"Created Withdrawal request {i}")
#
#      generate_next_block(sc_node, "first node")
#
#
#      # verifies that there are 3999 withdrawal requests
#      list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
#      assert_equal(num_of_wr, len(list_of_WR))
#
#      # verifies that the balance changed TODO Gas should also be removed
#      new_balance = http_wallet_balance(sc_node, evm_address)
#      exp_new_balance_in_zennies = ft_amount_in_zennies - bt_amount_in_zennies * num_of_wr
# #     exp_new_balance_in_wei = ft_amount_in_wei - convertZenniesToWei(bt_amount_in_zennies * num_of_wr)
#      exp_new_balance_in_wei = convertZenniesToWei(exp_new_balance_in_zennies)
#      assert_equal(exp_new_balance_in_wei, new_balance, "wrong balance")
#
#      # Tries to create another withdrawal request in the same epoch. Wr should not be created but the tx should be created
#
#      tx_id = withdrawcoins(sc_node, mc_address1, bt_amount_in_zennies)["result"]["transactionId"]
#
#      generate_next_block(sc_node, "first node")
#     # verifies that there are no withdrawal requests
#      list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
#      assert_equal(num_of_wr, len(list_of_WR))
#
#      # verifies that the balance didn't change TODO Gas should be removed
#      new_balance = http_wallet_balance(sc_node, evm_address)
#      assert_equal(exp_new_balance_in_wei, new_balance, "wrong balance")
#
#      # Verifies there is the transaction in the block
#
#      tx_in_block = sc_node.block_best()["result"]["block"]["sidechainTransactions"][0]["id"]
#      assert_equal(tx_id, tx_in_block, "Tx is not in the block")
#
#      # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
#      we0_end_mcblock_hash = mc_node.generate(8)[7]
#      logging.info("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
#      we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
#      we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
#      logging.info("End cum sc tx commtree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
#      scblock_id2 = generate_next_block(sc_node, "first node")
#      check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)
#
#      # Generate first mc block of the next epoch
#      we1_1_mcblock_hash = mc_node.generate(1)[0]
#      scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
#      check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)
#
#      # Wait until Certificate will appear in MC node mempool
#      time.sleep(10)
#      while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
#          logging.info("Wait for certificate in mc mempool...")
#          time.sleep(2)
#          sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
#      assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")
#
#      # Check that certificate generation skipped because mempool have certificate with same quality
#      generate_next_blocks(sc_node, "first node", 1)[0]
#      time.sleep(2)
#      assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"],
#                   "Expected certificate generation will be skipped.")
#
#      # Get Certificate for Withdrawal epoch 0 and verify it
#      we0_certHash = mc_node.getrawmempool()[0]
#      logging.info("Withdrawal epoch 0 certificate hash = " + we0_certHash)
#      we0_cert = mc_node.getrawtransaction(we0_certHash, 1)
#      we0_cert_hex = mc_node.getrawtransaction(we0_certHash)
#      logging.info("Withdrawal epoch 0 certificate hex = " + we0_cert_hex)
#      assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_cert["cert"]["scid"],
#                   "Sidechain Id in certificate is wrong.")
#      assert_equal(current_epoch_number, we0_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
#      assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_cert["cert"]["endEpochCumScTxCommTreeRoot"],
#                   "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
#      assert_equal(bt_amount_in_zennies * num_of_wr, convertZenToZennies(we0_cert["cert"]["totalAmount"]), "Sidechain total amount in certificate is wrong.")
#
#      # Generate MC block and verify that certificate is present
#      we1_2_mcblock_hash = mc_node.generate(1)[0]
#      assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
#      assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
#      assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]),
#                   "MC block expected to contain 1 Certificate.")
#      assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0],
#                   "MC block expected to contain certificate.")
#      logging.info("MC block with withdrawal certificate for epoch 0 = {0}\n".format(
#          str(mc_node.getblock(we1_2_mcblock_hash, False))))
#
#      # Generate SC block and verify that certificate is synced back
#      scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
#      check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)
#
#      # Check that certificate generation skipped because chain have certificate with same quality
#      time.sleep(2)
#      assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"],
#                   "Expected certificate generation will be skipped.")
#
#      # Verify Certificate for epoch 0 on SC side
#      mbrefdata = sc_node.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]
#      we0_sc_cert = mbrefdata["topQualityCertificate"]
#      assert_equal(len(mbrefdata["lowerCertificateLeaves"]), 0)
#      assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_sc_cert["sidechainId"],
#                   "Sidechain Id in certificate is wrong.")
#      assert_equal(current_epoch_number, we0_sc_cert["epochNumber"], "Sidechain epoch number in certificate is wrong.")
#      assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_sc_cert["endCumulativeScTxCommitmentTreeRoot"],
#                   "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
#      assert_equal(num_of_wr, len(we0_sc_cert["backwardTransferOutputs"]),
#                   "Backward transfer amount in certificate is wrong.")
#      assert_equal(we0_certHash, we0_sc_cert["hash"], "Certificate hash is different to the one in MC.")
#
#      # Try to withdraw coins from SC to MC in a new epoch: 2 withdrawals
#      current_epoch_number = 1
#      mc_address3 = mc_node.getnewaddress()
#
#
#      bt_amount1_in_zennies = exp_new_balance_in_zennies - 3
#      sc_bt_amount1 = convertZenToZennies(bt_amount1_in_zennies)
#      withdrawcoins(sc_node, mc_address3, sc_bt_amount1)
#
#      # Generate SC block
#      generate_next_blocks(sc_node, "first node", 1)
#      # Check the balance has changed
#      bt_amount2 = exp_new_balance_in_zennies - bt_amount1_in_zennies
#      sc_bt_amount2 = convertZenToZennies(bt_amount2)
#      new_balance = http_wallet_balance(sc_node, evm_address)
#      assert_equal(convertZenniesToWei(sc_bt_amount2), new_balance,  "wrong balance after first withdrawal request")
#
#      # verifies that there is one withdrawal request
#      list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
#      assert_equal(1, len(list_of_WR), "Wrong number of withdrawal requests")
#
#      # Create a second WR
#      withdrawcoins(sc_node, mc_address3, sc_bt_amount2)
#      generate_next_blocks(sc_node, "first node", 1)
#      # Check the balance has changed
#      new_balance = http_wallet_balance(sc_node, evm_address)
#      assert_equal(new_balance, 0, "wrong balance after second withdrawal request")
#
#      # verifies that there are 2 withdrawal request2
#      list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
#      assert_equal(2, len(list_of_WR))


if __name__ == "__main__":
    SCEvmBWTCornerCases().main()
