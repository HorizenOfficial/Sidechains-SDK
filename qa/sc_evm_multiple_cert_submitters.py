#!/usr/bin/env python3

import time

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_2, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import *
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    generate_next_block, connect_sc_nodes, AccountModel, assert_equal, assert_true
from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.utils import convertZenToZennies
from test_framework.util import fail, websocket_port_by_mc_node_index

"""
Check multiple certificate submitters processing for non-ceasing sidechain:
1. Check that submitter nodes may keep working in case of pending certificate queue:
    * sidechain and mainchain go further to the next epochs, but for the current one there is not enough sigs to submit cert.
2. Check that "faster" node which submits the certificate first, does not cause "slower" node to output error when trying to 
    submit its certificate for the same epoch. Slower node should continue working on the next certificate.

Configuration:
    Start 1 MC node and 2 SC nodes.
    SC Node 1 is the only forger.
    SC node 1 has 3 schnorr private keys [0, 1, 2] for cert submission. Submitter and signer are ENABLED.
    SC node 2 has 3 other schnorr private keys [3, 4, 5] for cert submission. Submitter is ENABLED and signer is ENABLED.
    Sidechain is non-ceasing sidechain.

Test:
    - Send FT to SC node 1 to have some coins for Withdrawal requests.
    - Connect SC node 2 and sync sc nodes.
    - Generate MC blocks for 5 full withdrawal epochs and in parallel generate SC blocks to sync with all new MC blocks.
        For every epoch N create N withdrawal request boxes: epoch 0 - 0 BTs, epoch 4 - 4 BTs.
        It is needed to check properly the validation of Certificates on SC side.
    _ Check that from SC node 1 perspective sc is ALIVE. (non-ceasing sidechains are always alive). 
    - Check that certificate submission is in progress on SC node 1 and SC node 2
    - Wait for the cert in MC mempool and generate 1 MC block and 1 SC block with that Cert.
    - Check that we started generating certificate for the next epoch in the queue.
    - Verify that first certificate endCumulativeScTxCommitmentTreeRoot equals to last one in the withdrawal epoch 0.
    - Repeat previous 2 steps to see that we generated all certificates except the one for epoch 5 (not a moment)
    - Verify that certificate endCumulativeScTxCommitmentTreeRoot equals to the one that contains previous epoch cert.
       Note: due to the certificate timing check in the MC we need to shift the endCumulativeScTxCommitmentTreeRoot.
    - Generate more MC and SC blocks. Check the submission of the Cert for epoch 5.
    - Verify that certificate endCumulativeScTxCommitmentTreeRoot equals to last one in the withdrawal epoch 5.
"""


def pass_withdrawal_epoch(mc_node, sc_node, mc_blocks_before_wrs, mc_block_after_wrs, we_number, nonce):
    # Generate first part of the MC blocks
    mc_tip_hash = mc_node.generate(mc_blocks_before_wrs)[-1]
    wr_tx_ids = []

    gas_limit = 230000
    max_fee_per_gas = 900000000
    max_priority_fee_per_gas = 900000000
    # Create Tx with WRs
    sc_bt_amount = convertZenToZennies(1)
    for i in range(0, we_number):
        mc_dest_address = mc_node.getnewaddress()
        withdrawal_request = {
            "nonce": nonce,
            "withdrawalRequest":
                {
                    "mainchainAddress": mc_dest_address,
                    "value": sc_bt_amount
                },
            "gasInfo": {
                "gasLimit": gas_limit,
                "maxFeePerGas": max_fee_per_gas,
                "maxPriorityFeePerGas": max_priority_fee_per_gas
            }
        }
        sc_bt_amount *= 2
        nonce += 1

        withdraw_coins_json = sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))
        if "result" not in withdraw_coins_json:
            fail("Withdraw coins failed: " + json.dumps(withdraw_coins_json))
        else:
            logging.info("Coins withdrawn: " + json.dumps(withdraw_coins_json))

        wr_tx_ids.append(withdraw_coins_json["result"]["transactionId"])

    #  Generate SC block with pending MC block refs and Tx with WRs
    block_id = generate_next_block(sc_node, "first node")

    # Check MC ref data inclusion
    check_mcreferencedata_presence(mc_tip_hash, block_id, sc_node)

    # Check Tx inclusion
    expected_txs_number = we_number
    res = sc_node.block_findById(blockId=block_id)
    sc_txs = res["result"]["block"]["sidechainTransactions"]
    assert_equal(expected_txs_number, len(sc_txs), "Number of WRs transactions is wrong")
    for i in range(0, we_number):
        assert_equal(sc_txs[i]["id"], wr_tx_ids[i], "Tx with WRs is not as expected")

    # Generate second part of the MC blocks
    mc_tip_hash = mc_node.generate(mc_block_after_wrs)[-1]

    #  Generate SC block with pending MC block refs
    block_id = generate_next_block(sc_node, "first node")

    # Check MC ref data inclusion
    check_mcreferencedata_presence(mc_tip_hash, block_id, sc_node)


def check_for_certificate(mc_node, sc_submitter_node1, sc_submitter_node2):
    time.sleep(10)
    while ((mc_node.getmempoolinfo()["size"] < 1) and
           (sc_submitter_node1.submitter_isCertGenerationActive()["result"]["state"] or
            sc_submitter_node2.submitter_isCertGenerationActive()["result"]["state"])):
        print("node1 isGenerating: " + str(sc_submitter_node1.submitter_isCertGenerationActive()["result"]["state"]))
        print("node2 isGenerating: " + str(sc_submitter_node2.submitter_isCertGenerationActive()["result"]["state"]))
        logging.info("Wait for certificates in the MC mempool...")
        time.sleep(1)

    print("node1 isGenerating: " + str(sc_submitter_node1.submitter_isCertGenerationActive()["result"]["state"]))
    print("node2 isGenerating: " + str(sc_submitter_node2.submitter_isCertGenerationActive()["result"]["state"]))
    assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to MC node mempool.")


class SCMultiplePendingCertsNonCeasing(AccountChainSetup):
    def __init__(self):
        super().__init__(number_of_sidechain_nodes=2,
                         withdrawalEpochLength=10,  # each 10 MC blocks, withdrawal certificate should be pushed to MC
                         circuittype_override=KEY_ROTATION_CIRCUIT,
                         forward_amount=100)

    total_withdrawal_epochs_number = 5

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True,  # submitter is enabled
            True,  # signer is enabled
            [0, 1, 2],  # 3 schnorr PKsб
            api_key='Horizen'
        )

        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True,  # submitter is enabled
            True,  # signer is enabled
            [3, 4, 5],  # 3 other schnorr PKs
            api_key='Horizen'
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node,
                           100,
                           10,
                           sc_creation_version=SC_CREATION_VERSION_2,
                           is_non_ceasing=True,
                           circuit_type=KEY_ROTATION_CIRCUIT),
            sc_node_1_configuration,
            sc_node_2_configuration
        )

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5,
                                                                 model=AccountModel)

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]
        nonce = 0
        mc_blocks_left_for_we = self.withdrawalEpochLength - 1  # minus genesis block

        assert_true(sc_node1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 1 submitter expected to be enabled.")
        assert_true(sc_node2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "Node 2 submitter expected to be enabled.")
        connect_sc_nodes(sc_node1, 1)

        # We need regular coins (the genesis account balance is locked into forging stake), so we perform a
        # Forward transfer to sidechain for an amount equals to the genesis_account_balance
        ft_amount_in_zen = 100
        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)

        mc_blocks_left_for_we -= 1  # minus block with FT
        generate_next_block(sc_node1, "first node")

        end_epoch_cum_sc_tx_comm_tree_root = ""
        # Do `total_withdrawal_epochs_number` loops of withdrawal epochs with different BTs size
        half_epoch = int(self.withdrawalEpochLength / 2)
        for epoch_number in range(0, self.total_withdrawal_epochs_number):
            # up to the half of WE
            mc_blocks_before_wrs = half_epoch - (self.withdrawalEpochLength - mc_blocks_left_for_we)
            # the second half of WE
            mc_block_after_wrs = half_epoch
            pass_withdrawal_epoch(mc_node, sc_node1, mc_blocks_before_wrs, mc_block_after_wrs, epoch_number, nonce)
            nonce += epoch_number
            mc_blocks_left_for_we = self.withdrawalEpochLength

            # For the first epoch store the last virtual withdrawal epoch block scCumTreeHash
            # It should appear as the first certificate endEpochCumScTxCommTreeRoot
            if epoch_number == 0:
                mcblock_hash = mc_node.getbestblockhash()
                mcblock = mc_node.getblock(mcblock_hash)
                end_epoch_cum_sc_tx_comm_tree_root = mcblock["scCumTreeHash"]

            self.sc_sync_all()

        # Second node expects to be synced and with all signatures generated
        # Do `total_withdrawal_epochs_number` loops and await for certificates one by one.
        for epoch_number in range(0, self.total_withdrawal_epochs_number):
            logging.info("Check for certificate for epoch " + str(epoch_number))
            # Check for certificate to be appeared in MC mempool
            check_for_certificate(mc_node, sc_node1, sc_node2)

            # Get Certificate and verify epoch number and endEpochCumScTxCommTreeRoot
            cert_hash = mc_node.getrawmempool()[0]
            cert = mc_node.getrawtransaction(cert_hash, 1)
            assert_equal(epoch_number, cert["cert"]["epochNumber"], "Sidechain epoch number in certificate is wrong.")
            assert_equal(end_epoch_cum_sc_tx_comm_tree_root, cert["cert"]["endEpochCumScTxCommTreeRoot"],
                         "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")

            # Generate MC block and remember its scCumTreeHash
            # It should appear as the next certificate endEpochCumScTxCommTreeRoot, because the current certificate
            # had been applied to the MC after the given virtual withdrawal epoch end.
            mc_block_with_cert_hash = mc_node.generate(1)[0]
            mc_blocks_left_for_we -= 1

            mc_block_with_cert = mc_node.getblock(mc_block_with_cert_hash)
            end_epoch_cum_sc_tx_comm_tree_root = mc_block_with_cert["scCumTreeHash"]

            self.sc_sync_all()

            time.sleep(2)

            # Next SC block triggers WE `epoch_number + 1` certificate submission
            generate_next_block(sc_node1, "first node")
            self.sc_sync_all()

        # Generate MC blocks and SC blocks to finish the WE
        # Check that after all pending cert were published, Nodes are able to keep processing new epochs
        mcblock_hash = mc_node.generate(mc_blocks_left_for_we)[-1]
        generate_next_block(sc_node1, "first node")

        # Generate one more MC and SC block to trigger cert submission
        mc_node.generate(1)
        generate_next_block(sc_node1, "first node")

        # Check for certificate to be appeared in MC mempool
        check_for_certificate(mc_node, sc_node1, sc_node2)

        # Get Certificate and verify epoch number and endEpochCumScTxCommTreeRoot
        cert_hash = mc_node.getrawmempool()[0]
        cert = mc_node.getrawtransaction(cert_hash, 1)
        assert_equal(self.total_withdrawal_epochs_number, cert["cert"]["epochNumber"],
                     "Sidechain epoch number in certificate is wrong.")
        mcblock = mc_node.getblock(mcblock_hash)
        # Since the previous certificate has been generated in time, the next certificate should specify
        # endEpochCumScTxCommTreeRoot equals to the one of virtual withdrawal epoch last mc block.
        end_epoch_cum_sc_tx_comm_tree_root = mcblock["scCumTreeHash"]
        assert_equal(end_epoch_cum_sc_tx_comm_tree_root, cert["cert"]["endEpochCumScTxCommTreeRoot"],
                     "Sidechain endEpochCumScTxCommTreeRoot in certificate is wrong.")


if __name__ == "__main__":
    SCMultiplePendingCertsNonCeasing().main()
