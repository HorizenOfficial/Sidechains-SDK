#!/usr/bin/env python3

import time


from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, SC_CREATION_VERSION_2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import fail, assert_false, assert_true, start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, \
    start_sc_nodes, generate_next_block, connect_sc_nodes
from SidechainTestFramework.sc_forging_util import *

"""
Check multiple certificates processing for non-ceasing sidechain:
1. Check that signer/submitter node may keep working in case of pending certificate queue:
    * sidechain and mainchain go further to the next epochs, but for the current one there is not enough sigs to submit cert.
2. Check that signer node may contribute to the pending certificate queue after sync with the network.

Configuration:
    Start 1 MC node and 2 SC nodes (with default websocket configuration).
    SC Node 1 is the only forger.
    SC node 1 has 3 schnorr private keys [0, 1, 2] for cert submission. Submitter and signer are ENABLED.
    SC node 1 has 3 other schnorr private keys [3, 4, 5] for cert submission. Submitter is DISABLED and signer is ENABLED.
    SC nodes are disconnected.
    Sidechain is non-ceasing sidechain.

Test:
    - Send FT to SC node 1 to have some coins for Withdrawal requests.
    - Generate MC blocks for 5 full withdrawal epochs and in parallel generate SC blocks to sync with all new MC blocks.
        For every epoch N create N withdrawal request boxes: epoch 0 - 0 BTs, epoch 4 - 4 BTs.
        It is needed to check properly the validation of Certificates on SC side.
    _ Check that from SC node 1 perspective sc is ALIVE. (non-ceasing sidechains are always alive). 
    - Wait a bit and check that SC node 1 has NOT started cert submission (not enough signatures for the first cert)
    - Connect SC node 2 and sync sc nodes.
    - Check that certificate submission is in progress on SC node 1
    - Wait for the cert in MC mempool and generate 1 MC block and 1 SC block with that Cert.
    - Check that we started generating certificate for the next epoch in the queue.
    - Repeat previous 2 steps to see that we generated all certificates except the one for epoch 5 (not a moment)
    - Generate more MC and SC blocks. Check the submission of the Cert for epoch 5.
"""


def pass_withdrawal_epoch(mc_node, sc_node, mc_blocks_before_wrs, mc_block_after_wrs, we_number):
    # Generate first part of the MC blocks
    mc_tip_hash = mc_node.generate(mc_blocks_before_wrs)[-1]

    # Create Tx with WRs
    if we_number > 0:
        withdrawal_request = {"outputs": []}
        mc_dest_address = mc_node.getnewaddress()
        sc_bt_amount = 1 * 100000000  # in Satoshi
        for i in range(0, we_number):
            withdrawal_request["outputs"].append(
                {
                    "mainchainAddress": mc_dest_address,
                    "value": sc_bt_amount
                }
            )
            sc_bt_amount *= 2  # increase by 2 the coins for the next WR

        withdraw_coins_json = sc_node.transaction_withdrawCoins(json.dumps(withdrawal_request))
        if "result" not in withdraw_coins_json:
            fail("Withdraw coins failed: " + json.dumps(withdraw_coins_json))
        else:
            logging.info("Coins withdrawn: " + json.dumps(withdraw_coins_json))

        wr_tx_id = withdraw_coins_json["result"]["transactionId"]

    #  Generate SC block with pending MC block refs and Tx with WRs
    block_id = generate_next_block(sc_node, "first node")

    # Check MC ref data inclusion
    check_mcreferencedata_presence(mc_tip_hash, block_id, sc_node)

    # Check Tx inclusion
    expected_txs_number = 1 if we_number > 0 else 0
    res = sc_node.block_findById(blockId=block_id)
    sc_txs = res["result"]["block"]["sidechainTransactions"]
    assert_equal(expected_txs_number, len(sc_txs), "Only 1 Tx with WRs expected")
    if expected_txs_number != 0:
        assert_equal(sc_txs[0]["id"], wr_tx_id, "Tx with WRs is not as expected")

    # Generate second part of the MC blocks
    mc_tip_hash = mc_node.generate(mc_block_after_wrs)[-1]

    #  Generate SC block with pending MC block refs
    block_id = generate_next_block(sc_node, "first node")

    # Check MC ref data inclusion
    check_mcreferencedata_presence(mc_tip_hash, block_id, sc_node)

def check_for_certificate(mc_node, sc_submitter_node):
    time.sleep(10)
    while (mc_node.getmempoolinfo()["size"] < 1 and
           sc_submitter_node.submitter_isCertGenerationActive()["result"]["state"]):
        logging.info("Wait for certificates in the MC mempool...")
        time.sleep(2)

    assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to MC node mempool.")

class SCMultiplePendingCertsNonCeasing(SidechainTestFramework):
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 2

    sc_nodes_bootstrap_info = None
    sc_withdrawal_epoch_length = 10  # Note: must be even number
    total_withdrawal_epochs_number = 5
    sc_creation_amount = 100  # Zen
    ft_amount = 100  # Zen

    def setup_nodes(self):
        # Set MC scproofqueuesize to 0 to avoid BatchVerifier processing delays
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir,
                           extra_args=[['-debug=sc', '-logtimemicros=1',
                                        '-scproofqueuesize=0']] * self.number_of_mc_nodes)

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            True,  # submitter is enabled
            True,  # signer is enabled
            [0, 1, 2]  # 3 schnorr PKs
        )

        sc_node_2_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            False,  # submitter is disabled
            True,  # signer is enabled
            [3, 4, 5]  # 3 other schnorr PKs
        )

        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node,
                           self.sc_creation_amount,
                           self.sc_withdrawal_epoch_length,
                           sc_creation_version=SC_CREATION_VERSION_2,
                           is_non_ceasing=True),
            sc_node_1_configuration,
            sc_node_2_configuration
        )

        # rewind sc genesis block timestamp for 5 consensus epochs
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, 720 * 120 * 5)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, self.options.tmpdir
                              # , extra_args=['-agentlib']
                              )

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]

        mc_blocks_left_for_we = self.sc_withdrawal_epoch_length - 1  # minus genesis block

        # Send FT to SC node 1 to have some coins for WRs
        mc_return_address = mc_node.getnewaddress()
        forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                      mc_node,
                                      self.sc_nodes_bootstrap_info.genesis_account.publicKey,
                                      self.ft_amount,
                                      mc_return_address,
                                      generate_block=True)

        mc_blocks_left_for_we -= 1  # minus block with FT
        generate_next_block(sc_node1, "first node")

        # Do `total_withdrawal_epochs_number` loops of withdrawal epochs with different BTs size
        half_epoch = int(self.sc_withdrawal_epoch_length / 2)
        for epoch_number in range(0, self.total_withdrawal_epochs_number):
            # up to the half of WE
            mc_blocks_before_wrs = half_epoch - (self.sc_withdrawal_epoch_length - mc_blocks_left_for_we)
            # the second half of WE
            mc_block_after_wrs = half_epoch
            pass_withdrawal_epoch(mc_node, sc_node1, mc_blocks_before_wrs, mc_block_after_wrs, epoch_number)
            mc_blocks_left_for_we = self.sc_withdrawal_epoch_length

        # First node expects to generate its signatures
        # Connect and sync SC nodes
        connect_sc_nodes(sc_node1, 1)
        self.sc_sync_all()

        # Second node expects to be synced and with all signatures generated
        # Do `total_withdrawal_epochs_number` loops and await for certificates one by one.
        for epoch_number in range(0, self.total_withdrawal_epochs_number):
            logging.info("Check for certificate for epoch " + str(epoch_number))
            # Check for certificate to be appeared in MC mempool
            check_for_certificate(mc_node, sc_node1)

            mc_node.generate(1)
            mc_blocks_left_for_we -= 1

            # After cert appeared only in MC, no next cert attempts expected
            time.sleep(2)
            if sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
                fail("Cert submission is not expected")
            # Next SC block triggers WE `epoch_number + 1` certificate submitting
            generate_next_block(sc_node1, "first node")
            self.sc_sync_all()

        # Generate MC blocks and SC blocks to finish the WE
        # Check that after all pending cert were published, Nodes are able to keep processing new epochs
        mc_node.generate(mc_blocks_left_for_we)
        generate_next_block(sc_node1, "first node")

        # Generate one more MC and SC block to trigger cert submission
        mc_node.generate(1)
        generate_next_block(sc_node1, "first node")

        # Check for certificate to be appeared in MC mempool
        check_for_certificate(mc_node, sc_node1)


if __name__ == "__main__":
    SCMultiplePendingCertsNonCeasing().main()
