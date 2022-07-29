#!/usr/bin/env python3
import time

from SidechainTestFramework.account.httpCalls.transaction.allWithdrawRequests import all_withdrawal_requests
from SidechainTestFramework.account.httpCalls.transaction.withdrawCoins import withdrawcoins
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_blocks, generate_next_block, \
    AccountModelBlockVersion, EVM_APP_BINARY, is_mainchain_block_included_in_sc_block, assert_true, \
    check_mainchain_block_reference_info, convertZenToZennies, convertZenniesToWei
from test_framework.util import assert_equal, assert_false, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain

"""
Checks Certificate automatic creation and submission to MC for an EVM Sidechain:
1. Creation of Certificate with no backward transfers.
2. Creation of Certificate with multiple backward transfers.

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


class SCEvmBackwardTransfer(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10
    number_of_sidechain_nodes = 1

    def setup_nodes(self):
        num_nodes = 1
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(num_nodes, self.options.tmpdir, extra_args=[['-debug=sc', '-debug=ws', '-logtimemicros=1',
                                                                        '-scproofqueuesize=0']] * num_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.sc_withdrawal_epoch_length),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=720 * 120 * 5,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir, binary=[EVM_APP_BINARY] * 2)

    def run_test(self):
        time.sleep(0.1)
        self.sync_all()
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]

        # Checks that MC block with sc creation tx is referenced in the genesis sc block
        mc_block = self.nodes[0].getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))

        sc_best_block = sc_node.block_best()["result"]
        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verifies MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        # verifies that there are no withdrawal requests yet
        current_epoch_number = 0
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
        assert_equal(0, len(list_of_WR))

        # creates FT to SC to withdraw later

        mc_return_address = mc_node.getnewaddress()
        ret = sc_node.wallet_createPrivateKeySecp256k1()
        evm_address = ret["result"]["proposition"]["address"]

        ft_amount_in_zen = 10
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        # transfers some fund from MC to SC using the evm address created before
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      self.nodes[0],
                                      evm_address,
                                      ft_amount_in_zen,
                                      mc_return_address)

        # Generates SC block and checks that FT appears in SC account balance
        generate_next_blocks(sc_node, "first node", 1)
        new_balance = http_wallet_balance(sc_node, evm_address)
        assert_equal(new_balance, ft_amount_in_wei, "wrong balance")

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]
        print("End mc block hash in withdrawal epoch 0 = " + we0_end_mcblock_hash)
        we0_end_mcblock_json = mc_node.getblock(we0_end_mcblock_hash)
        we0_end_epoch_cum_sc_tx_comm_tree_root = we0_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 0 = " + we0_end_epoch_cum_sc_tx_comm_tree_root)
        scblock_id2 = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
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
        print("Withdrawal epoch 0 certificate hash = " + we0_certHash)
        we0_cert = mc_node.getrawtransaction(we0_certHash, 1)
        we0_cert_hex = mc_node.getrawtransaction(we0_certHash)
        print("Withdrawal epoch 0 certificate hex = " + we0_cert_hex)
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
        print("MC block with withdrawal certificate for epoch 0 = {0}\n".format(
            str(mc_node.getblock(we1_2_mcblock_hash, False))))

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
        print("First BT MC public key address is {}".format(mc_address1))
        bt_amount1 = ft_amount_in_zen - 3
        sc_bt_amount1 = convertZenToZennies(bt_amount1)
        withdrawcoins(sc_node, mc_address1, sc_bt_amount1)
        # Check the balance hasn't changed yet
        new_balance = http_wallet_balance(sc_node, evm_address)
        assert_equal(ft_amount_in_wei, new_balance, "wrong balance")

        # verifies that there are no withdrawal requests yet
        current_epoch_number = 1
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
        assert_equal(0, len(list_of_WR))

        # Generate SC block
        generate_next_blocks(sc_node, "first node", 1)
        # Check the balance has changed
        bt_amount2 = ft_amount_in_zen - bt_amount1
        sc_bt_amount2 = convertZenToZennies(bt_amount2)
        new_balance = http_wallet_balance(sc_node, evm_address)
        assert_equal(new_balance, convertZenniesToWei(sc_bt_amount2), "wrong balance after first withdrawal request")

        # verifies that there is one withdrawal request
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
        assert_equal(1, len(list_of_WR), "Wrong number of withdrawal requests")
        assert_equal(mc_address1, list_of_WR[0]["proposition"]["mainchainAddress"])
        assert_equal(convertZenniesToWei(sc_bt_amount1), list_of_WR[0]["value"])
        assert_equal(sc_bt_amount1, list_of_WR[0]["valueInZennies"])

        mc_address2 = self.nodes[0].getnewaddress()
        print("Second BT MC public key address is {}".format(mc_address2))
        withdrawcoins(sc_node, mc_address2, sc_bt_amount2)

        # Generate SC block
        generate_next_blocks(sc_node, "first node", 1)
        # Check the balance has changed
        new_balance = http_wallet_balance(sc_node, evm_address)
        assert_equal(new_balance, 0, "wrong balance after second withdrawal request")

        # verifies that there are 2 withdrawal request2
        list_of_WR = all_withdrawal_requests(sc_node, current_epoch_number)["listOfWR"]
        assert_equal(2, len(list_of_WR))

        assert_equal(mc_address1, list_of_WR[0]["proposition"]["mainchainAddress"])
        assert_equal(convertZenniesToWei(sc_bt_amount1), list_of_WR[0]["value"])
        assert_equal(sc_bt_amount1, list_of_WR[0]["valueInZennies"])

        assert_equal(mc_address2, list_of_WR[1]["proposition"]["mainchainAddress"])
        assert_equal(convertZenniesToWei(sc_bt_amount2), list_of_WR[1]["value"])
        assert_equal(sc_bt_amount2, list_of_WR[1]["valueInZennies"])


        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we1_end_mcblock_hash = mc_node.generate(8)[7]
        print("End mc block hash in withdrawal epoch 1 = " + we1_end_mcblock_hash)
        we1_end_mcblock_json = mc_node.getblock(we1_end_mcblock_hash)
        we1_end_epoch_cum_sc_tx_comm_tree_root = we1_end_mcblock_json["scCumTreeHash"]
        print("End cum sc tx commtree root hash in withdrawal epoch 1 = " + we1_end_epoch_cum_sc_tx_comm_tree_root)
        we1_end_scblock_id = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we1_end_mcblock_hash, we1_end_scblock_id, sc_node)

        # Generate first mc block of the next epoch
        we2_1_mcblock_hash = mc_node.generate(1)[0]
        we2_1_scblock_id = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we2_1_mcblock_hash, we2_1_scblock_id, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] == 0 and sc_node.submitter_isCertGenerationActive()["result"]["state"]:
            print("Wait for certificate in mc mempool...")
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
        print("Withdrawal epoch 1 certificate hash = " + we1_certHash)
        we1_cert = mc_node.getrawtransaction(we1_certHash, 1)
        we1_cert_hex = mc_node.getrawtransaction(we1_certHash)
        print("Withdrawal epoch 1 certificate hex = " + we1_cert_hex)
        assert_equal(self.sc_nodes_bootstrap_info.sidechain_id, we1_cert["cert"]["scid"],
                     "Sidechain Id in certificate is wrong.")
        assert_equal(1, we1_cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
        assert_equal(we1_end_epoch_cum_sc_tx_comm_tree_root, we1_cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")
        assert_equal(bt_amount1 + bt_amount2, we1_cert["cert"]["totalAmount"],
                     "Sidechain total amount in certificate is wrong.")

        # Generate MC block and verify that certificate is present
        we2_2_mcblock_hash = mc_node.generate(1)[0]
        assert_equal(0, mc_node.getmempoolinfo()["size"], "Certificate expected to be removed from MC node mempool.")
        assert_equal(1, len(mc_node.getblock(we2_2_mcblock_hash)["tx"]), "MC block expected to contain 1 transaction.")
        assert_equal(1, len(mc_node.getblock(we2_2_mcblock_hash)["cert"]),
                     "MC block expected to contain 1 Certificate.")
        assert_equal(we1_certHash, mc_node.getblock(we2_2_mcblock_hash)["cert"][0],
                     "MC block expected to contain certificate.")
        print("MC block with withdrawal certificate for epoch 1 = {0}\n".format(
            str(mc_node.getblock(we2_2_mcblock_hash, False))))

        # Check certificate BT entries
        assert_equal(bt_amount1, we1_cert["vout"][1]["value"], "First BT amount is wrong.")
        assert_equal(bt_amount2, we1_cert["vout"][2]["value"], "Second BT amount is wrong.")

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
        assert_equal(sc_bt_amount1, we1_sc_cert["backwardTransferOutputs"][0]["amount"], "First BT amount is wrong.")

        sc_pub_key_2 = we1_sc_cert["backwardTransferOutputs"][1]["address"]
        assert_equal(mc_address2, sc_pub_key_2, "Second BT address is wrong.")
        assert_equal(sc_bt_amount2, we1_sc_cert["backwardTransferOutputs"][1]["amount"], "Second BT amount is wrong.")

        assert_equal(we1_certHash, we1_sc_cert["hash"], "Certificate hash is different to the one in MC.")


if __name__ == "__main__":
    SCEvmBackwardTransfer().main()
