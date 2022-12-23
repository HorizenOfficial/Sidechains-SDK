#!/usr/bin/env python3
import logging
import time

import base58
from eth_abi import decode
from eth_utils import add_0x_prefix, encode_hex, event_signature_to_log_topic, remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.ac_utils import generate_block_and_get_tx_receipt
from SidechainTestFramework.account.httpCalls.transaction.allWithdrawRequests import all_withdrawal_requests
from SidechainTestFramework.account.httpCalls.transaction.withdrawCoins import withdrawcoins
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import (computeForgedTxFee,
                                                  convertZenToZennies, convertZenniesToWei)
from SidechainTestFramework.sc_forging_util import check_mcreference_presence, check_mcreferencedata_presence
from SidechainTestFramework.scutil import (
    generate_next_block, generate_next_blocks
)
from test_framework.util import (
    assert_equal, assert_false, hex_str_to_bytes,
)

"""
Checks Certificate automatic creation and submission to MC for an EVM Sidechain:
1. Creation of Certificate with no backward transfers.
2. Creation of Certificate with multiple backward transfers.

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


def check_withdrawal_event(event, source_addr, dest_addr, amount, exp_epoch):
    assert_equal(3, len(event['topics']), "Wrong number of topics in event")
    event_id = event['topics'][0]

    event_signature = encode_hex(event_signature_to_log_topic('AddWithdrawalRequest(address,bytes20,uint256,uint32)'))
    assert_equal(event_signature, event_id, "Wrong event signature in topics")

    from_addr = decode(['address'], hex_str_to_bytes(event['topics'][1][2:]))[0][2:]
    assert_equal(source_addr, from_addr, "Wrong from address in topics")

    mcDestAddr = decode(['bytes20'], hex_str_to_bytes(event['topics'][2][2:]))[0]
    assert_equal(base58.b58decode_check(dest_addr).hex()[4:], encode_hex(mcDestAddr)[2:],
                 "Wrong destination address in topics")

    (wr_amount, epoch) = decode(['uint256', 'uint32'], hex_str_to_bytes(event['data'][2:]))
    assert_equal(convertZenniesToWei(amount), wr_amount, "Wrong amount in event")
    assert_equal(exp_epoch, epoch, "Wrong epoch in event")


class SCEvmBackwardTransfer(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=10)

    def run_test(self):
        time.sleep(0.1)

        ft_amount_in_zen = 10
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        hex_evm_addr = remove_0x_prefix(self.evm_address)

        # verifies that there are no withdrawal requests yet
        current_epoch_number = 0
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(0, len(list_of_WR))

        # Checks that FT appears in SC account balance
        new_balance = http_wallet_balance(sc_node, hex_evm_addr)
        assert_equal(new_balance, ft_amount_in_wei, "wrong balance")

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]

        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]

        scblock_id2 = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Check that certificate generation skipped because mempool have certificate with same quality
        generate_next_blocks(sc_node, "first node", 1)[0]
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"],
                     "Expected certificate generation will be skipped.")

        # Get Certificate for Withdrawal epoch 0 and verify it
        we0_certHash = mc_node.getrawmempool()[0]

        we0_cert = mc_node.getrawtransaction(we0_certHash, 1)

        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_cert["cert"]["scid"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(0, we0_cert["cert"]["totalAmount"], "Sidechain total amount in certificate is wrong.")

        # Generate MC block and verify that certificate is present
        we1_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we1_2_mcblock_hash)["cert"]),
                     "MC block expected to contain 1 Certificate.")
        assert_equal(we0_certHash, mc_node.getblock(we1_2_mcblock_hash)["cert"][0],
                     "MC block expected to contain certificate.")

        # Generate SC block and verify that certificate is synced back
        scblock_id4 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_2_mcblock_hash, scblock_id4, sc_node)

        # Check that certificate generation skipped because chain have certificate with same quality
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"],
                     "Expected certificate generation will be skipped.")

        # Verify Certificate for epoch 0 on SC side
        mbrefdata = sc_node.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]
        we0_sc_cert = mbrefdata["topQualityCertificate"]
        assert_equal(len(mbrefdata["lowerCertificateLeaves"]), 0)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we0_sc_cert["sidechainId"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(0, we0_sc_cert["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we0_end_epoch_cum_sc_tx_comm_tree_root, we0_sc_cert["endCumulativeScTxCommitmentTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(0, len(we0_sc_cert["backwardTransferOutputs"]),
                     "Backward transfer amount in certificate is wrong.")
        assert_equal(we0_certHash, we0_sc_cert["hash"], "Certificate hash is different to the one in MC.")

        # Try to withdraw coins from SC to MC: 2 withdrawals
        mc_address1 = mc_node.getnewaddress()

        bt_amount_in_zen_1 = ft_amount_in_zen - 3
        sc_bt_amount_in_zennies_1 = convertZenToZennies(bt_amount_in_zen_1)
        res = withdrawcoins(sc_node, mc_address1, sc_bt_amount_in_zennies_1)

        tx_id = add_0x_prefix(res["result"]["transactionId"])

        # Check the balance hasn't changed yet
        new_balance = http_wallet_balance(sc_node, hex_evm_addr)
        assert_equal(ft_amount_in_wei, new_balance, "wrong balance")

        # verifies that there are no withdrawal requests yet
        current_epoch_number = 1
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(0, len(list_of_WR))

        receipt = generate_block_and_get_tx_receipt(sc_node, tx_id)
        logging.info(receipt)
        # Check the status of tx
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")
        # Check the logs
        assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
        wr_event = receipt['result']['logs'][0]
        check_withdrawal_event(wr_event, hex_evm_addr, mc_address1, sc_bt_amount_in_zennies_1, 1)

        # Check the balance has changed
        # Retrieve how much gas was spent
        gas_fee_paid, _, _ = computeForgedTxFee(sc_node, tx_id)
        expected_new_balance = ft_amount_in_wei - convertZenniesToWei(sc_bt_amount_in_zennies_1) - gas_fee_paid
        new_balance = http_wallet_balance(sc_node, hex_evm_addr)
        assert_equal(expected_new_balance, new_balance, "wrong balance after first withdrawal request")

        # verifies that there is one withdrawal request
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(1, len(list_of_WR), "Wrong number of withdrawal requests")
        assert_equal(mc_address1, list_of_WR[0]["proposition"]["mainchainAddress"])
        assert_equal(convertZenniesToWei(sc_bt_amount_in_zennies_1), list_of_WR[0]["value"])
        assert_equal(sc_bt_amount_in_zennies_1, list_of_WR[0]["valueInZennies"])

        mc_address2 = self.nodes[0].getnewaddress()

        bt_amount_in_zen_2 = 1
        sc_bt_amount_in_zennies_2 = convertZenToZennies(bt_amount_in_zen_2)

        res = withdrawcoins(sc_node, mc_address2, sc_bt_amount_in_zennies_2)

        tx_id = add_0x_prefix(res["result"]["transactionId"])

        receipt = generate_block_and_get_tx_receipt(sc_node, tx_id)
        # Check the status of tx
        status = int(receipt['result']['status'], 16)
        assert_equal(1, status, "Wrong tx status in receipt")
        # Check the logs
        assert_equal(1, len(receipt['result']['logs']), "Wrong number of events in receipt")
        wr_event = receipt['result']['logs'][0]
        check_withdrawal_event(wr_event, hex_evm_addr, mc_address2, sc_bt_amount_in_zennies_2, 1)

        # Check the balance has changed
        gas_fee_paid, forgersPoolFee, forgerTip = computeForgedTxFee(sc_node, tx_id)
        expected_new_balance = new_balance - convertZenniesToWei(sc_bt_amount_in_zennies_2) - gas_fee_paid
        new_balance = http_wallet_balance(sc_node, hex_evm_addr)
        assert_equal(expected_new_balance, new_balance, "wrong balance after first withdrawal request")

        # verifies that there are 2 withdrawal requests
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)
        assert_equal(2, len(list_of_WR))

        assert_equal(mc_address1, list_of_WR[0]["proposition"]["mainchainAddress"])
        assert_equal(convertZenniesToWei(sc_bt_amount_in_zennies_1), list_of_WR[0]["value"])
        assert_equal(sc_bt_amount_in_zennies_1, list_of_WR[0]["valueInZennies"])

        assert_equal(mc_address2, list_of_WR[1]["proposition"]["mainchainAddress"])
        assert_equal(convertZenniesToWei(sc_bt_amount_in_zennies_2), list_of_WR[1]["value"])
        assert_equal(sc_bt_amount_in_zennies_2, list_of_WR[1]["valueInZennies"])

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(8)[7]
        logging.info("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        logging.info(
            "End cum sc tx commtree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)
        we1_end_scblock_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we1_end_mcblock_hash, we1_end_scblock_id, sc_node)

        # Generate first mc block of the next epoch
        we2_1_mcblock_hash = mc_node.generate(1)[0]
        we2_1_scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_1_mcblock_hash, we2_1_scblock_id, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # Check that certificate generation skipped because mempool have certificate with same quality
        generate_next_blocks(sc_node, "first node", 1)[0]
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"],
                     "Expected certificate generation will be skipped.")

        # Get Certificate for Withdrawal epoch 1 and verify it
        we1_certHash = mc_node.getrawmempool()[0]
        logging.info("Withdrawal epoch 1 certificate hash = " + we1_certHash)
        we1_cert = mc_node.getrawtransaction(we1_certHash, 1)
        we1_cert_hex = mc_node.getrawtransaction(we1_certHash)
        logging.info("Withdrawal epoch 1 certificate hex = " + we1_cert_hex)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we1_cert["cert"]["scid"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(1, we1_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we1_end_epoch_cum_sc_tx_comm_tree_root, we1_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(bt_amount_in_zen_1 + bt_amount_in_zen_2, we1_cert["cert"]["totalAmount"],
                     "Sidechain total amount in certificate is wrong.")

        # Generate MC block and verify that certificate is present
        we2_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we2_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we2_2_mcblock_hash)["cert"]),
                     "MC block expected to contain 1 Certificate.")
        assert_equal(we1_certHash, mc_node.getblock(we2_2_mcblock_hash)["cert"][0],
                     "MC block expected to contain certificate.")
        logging.info("MC block with withdrawal certificate for epoch 1 = {0}\n".format(
            str(mc_node.getblock(we2_2_mcblock_hash, False))))

        # Check certificate BT entries
        assert_equal(bt_amount_in_zen_1, we1_cert["vout"][1]["value"], "First BT amount is wrong.")
        assert_equal(bt_amount_in_zen_2, we1_cert["vout"][2]["value"], "Second BT amount is wrong.")

        cert_address_1 = we1_cert["vout"][1]["scriptPubKey"]["addresses"][0]
        assert_equal(mc_address1, cert_address_1, "First BT standard address is wrong.")
        cert_address_2 = we1_cert["vout"][2]["scriptPubKey"]["addresses"][0]
        assert_equal(mc_address2, cert_address_2, "Second BT standard address is wrong.")

        # Generate SC block and verify that certificate is synced back
        scblock_id5 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_2_mcblock_hash, scblock_id5, sc_node)

        # Check that certificate generation skipped because chain have certificate with same quality
        time.sleep(2)
        assert_false(sc_node.submitter_isCertGenerationActive()["result"]["state"],
                     "Expected certificate generation will be skipped.")

        # Verify Certificate for epoch 1 on SC side
        mbrefdata = sc_node.block_best()["result"]["block"]["mainchainBlockReferencesData"][0]
        we1_sc_cert = mbrefdata["topQualityCertificate"]
        assert_equal(len(mbrefdata["lowerCertificateLeaves"]), 0)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we1_sc_cert["sidechainId"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(1, we1_sc_cert["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we1_end_epoch_cum_sc_tx_comm_tree_root, we1_sc_cert["endCumulativeScTxCommitmentTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(2, len(we1_sc_cert["backwardTransferOutputs"]),
                     "Backward transfer amount in certificate is wrong.")

        sc_pub_key_1 = we1_sc_cert["backwardTransferOutputs"][0]["address"]
        assert_equal(mc_address1, sc_pub_key_1, "First BT address is wrong.")
        assert_equal(sc_bt_amount_in_zennies_1, we1_sc_cert["backwardTransferOutputs"][0]["amount"],
                     "First BT amount is wrong.")

        sc_pub_key_2 = we1_sc_cert["backwardTransferOutputs"][1]["address"]
        assert_equal(mc_address2, sc_pub_key_2, "Second BT address is wrong.")
        assert_equal(sc_bt_amount_in_zennies_2, we1_sc_cert["backwardTransferOutputs"][1]["amount"],
                     "Second BT amount is wrong.")

        assert_equal(we1_certHash, we1_sc_cert["hash"], "Certificate hash is different to the one in MC.")


if __name__ == "__main__":
    SCEvmBackwardTransfer().main()
