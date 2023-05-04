#!/usr/bin/env python3
import logging
import os
import time

from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import (convertZenToZennies, convertZenniesToWei)
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCCreationInfo, \
    SC_CREATION_VERSION_2, SCNetworkConfiguration, KEY_ROTATION_CIRCUIT
from SidechainTestFramework.sc_forging_util import check_mcreference_presence, check_mcreferencedata_presence
from SidechainTestFramework.scutil import (
    generate_next_block, generate_next_blocks, bootstrap_sidechain_nodes, AccountModel, connect_sc_nodes, assert_true
)
from test_framework.util import (
    assert_equal, websocket_port_by_mc_node_index,
)

"""
Check multiple certificate submitters processing for non-ceasing sidechain:
    - Check that "faster" node which submits the certificate first, does not cause "slower" node to output error when trying to 
    submit its certificate for the same epoch.

Configuration:
    Start 1 MC node and 2 SC nodes.
    SC Node 1 is the only forger.
    SC node 1 has 3 schnorr private keys [0, 1, 2] for cert submission. Submitter and signer are ENABLED.
    SC node 2 has 3 other schnorr private keys [3, 4, 5] for cert submission. Submitter is ENABLED and signer is ENABLED.
    Sidechain is non-ceasing sidechain.

Test:
    - creates new forward transfer to sidechain
    - verifies that the receiving account balance is changed
    - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
    - generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
    - check epoch 0 certificate with not backward transfers in the MC mempool
    - check that only one SC node forged certificate successfuly, and the other one contains 'submission not needed message'
"""


class SCEvmMultipleCertSubmittersTest(AccountChainSetup):

    def __init__(self):
        super().__init__(withdrawalEpochLength=10, number_of_sidechain_nodes=2,
                         forward_amount=100
                         )

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
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network, block_timestamp_rewind=self.block_timestamp_rewind, model=AccountModel)

    def run_test(self):
        time.sleep(0.1)
        print(len(self.submitters_private_keys_indexes))

        ft_amount_in_zen = 10
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)
        mc_node = self.nodes[0]
        sc_node = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        connect_sc_nodes(sc_node, 1)  # Connect SC nodes

        print("sc node1 is submitter " + str(sc_node.submitter_isCertificateSubmitterEnabled()["result"]["enabled"]))
        print("sc node2 is submitter " + str(sc_node2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"]))

        hex_evm_addr = remove_0x_prefix(self.evm_address)

        # Checks that FT appears in SC account balance
        new_balance = http_wallet_balance(sc_node, hex_evm_addr)
        assert_equal(new_balance, ft_amount_in_wei, "wrong balance")

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]

        scblock_id2 = generate_next_block(sc_node, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while ((mc_node.getmempoolinfo()["size"] < 1) and (sc_node.submitter_isCertGenerationActive()["result"]["state"] or sc_node2.submitter_isCertGenerationActive()["result"]["state"])):
            logging.info("Wait for certificate in mc mempool...")
            print("node1 isGenerating: " + str(sc_node.submitter_isCertGenerationActive()["result"]["state"]))
            print("node2 isGenerating: " + str(sc_node2.submitter_isCertGenerationActive()["result"]["state"]))
            time.sleep(2)
            sc_node.block_best()  # just a ping to SC node. For some reason, STF can't request SC node API after a while idle.
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # wait till both SC nodes stop generating certificate
        while sc_node.submitter_isCertGenerationActive()["result"]["state"] or sc_node2.submitter_isCertGenerationActive()["result"]["state"]:
            time.sleep(2)

        # Assert that the proper log messages are present in both log files
        with open(os.path.join(self.options.tmpdir + "/sc_node0/log", "debugLog.txt")) as node0, open(os.path.join(self.options.tmpdir + "/sc_node1/log", "debugLog.txt")) as node1:
            log0 = node0.read()
            log1 = node1.read()
            submission_not_needed_msg = "Submission not needed. Certificate already present in epoch 0"
            submission_successful = "Backward transfer certificate response had been received"
            assert_true((submission_not_needed_msg in log0 and submission_successful in log1) or (submission_not_needed_msg in log1 and submission_successful in log0))


if __name__ == "__main__":
    SCEvmMultipleCertSubmittersTest().main()
