#!/usr/bin/env python3
import logging
import time

from eth_utils import remove_0x_prefix

from SidechainTestFramework.account.ac_chain_setup import AccountChainSetup
from SidechainTestFramework.account.httpCalls.wallet.balance import http_wallet_balance
from SidechainTestFramework.account.utils import (convertZenToZennies, convertZenniesToWei)
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, MCConnectionInfo, SCCreationInfo, \
    SC_CREATION_VERSION_2, SCNetworkConfiguration, KEY_ROTATION_CIRCUIT, DEFAULT_MAX_MEMPOOL_SLOTS
from SidechainTestFramework.sc_forging_util import check_mcreference_presence, check_mcreferencedata_presence
from SidechainTestFramework.scutil import (
    generate_next_block, generate_next_blocks, bootstrap_sidechain_nodes, AccountModel, connect_sc_nodes, assert_true,
    assert_false
)
from test_framework.util import (
    assert_equal, websocket_port_by_mc_node_index,
)

"""
Check that node info endpoint gives correct data for Account sidechain model.

Configuration:
    Start 1 MC node and 2 SC nodes.
    SC Node 1 is the only forger.
    SC node 1 has 3 schnorr private keys [0, 1, 2] for cert submission. Submitter and signer are ENABLED.
    SC node 2 has 3 other schnorr private keys [3, 4, 5] for cert submission. Only signer is ENABLED.
    Sidechain is non-ceasing sidechain.

Test:
    - creates new forward transfer to sidechain
    - verifies that the receiving account balance is changed
    - generate MC and SC blocks to reach the end of the Withdrawal epoch 0
    - generate one more MC and SC block accordingly and await for certificate submission to MC node mempool
    - generate MC for certificate to appear in MC
    - generate SC block to sync with MC
    - check node info results of both nodes
"""


class SCEvmNodeInfo(AccountChainSetup):

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
            False,  # submitter is enabled
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
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=self.block_timestamp_rewind,
                                                                 model=AccountModel)

    def run_test(self):
        time.sleep(0.1)

        ft_amount_in_zen = 10
        ft_amount_in_zennies = convertZenToZennies(ft_amount_in_zen)
        ft_amount_in_wei = convertZenniesToWei(ft_amount_in_zennies)

        self.sc_ac_setup(ft_amount_in_zen=ft_amount_in_zen)
        mc_node = self.nodes[0]
        sc_node1 = self.sc_nodes[0]
        sc_node2 = self.sc_nodes[1]

        connect_sc_nodes(sc_node1, 1)  # Connect SC nodes

        assert_true(sc_node1.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                    "sc node1 should be submitter")
        assert_false(sc_node2.submitter_isCertificateSubmitterEnabled()["result"]["enabled"],
                     "sc node2 should not be submitter")

        hex_evm_addr = remove_0x_prefix(self.evm_address)

        # Checks that FT appears in SC account balance
        new_balance = http_wallet_balance(sc_node1, hex_evm_addr)
        assert_equal(new_balance, ft_amount_in_wei, "wrong balance")

        # Generate 8 more MC block to finish the first withdrawal epoch, then generate 1 more SC block to sync with MC.
        we0_end_mcblock_hash = mc_node.generate(8)[7]

        scblock_id2 = generate_next_block(sc_node1, "first node")
        check_mcreferencedata_presence(we0_end_mcblock_hash, scblock_id2, sc_node1)

        # Generate first mc block of the next epoch
        we1_1_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_blocks(sc_node1, "first node", 1)[0]
        check_mcreference_presence(we1_1_mcblock_hash, scblock_id3, sc_node1)

        # Wait until Certificate will appear in MC node mempool
        time.sleep(10)
        while mc_node.getmempoolinfo()["size"] < 1 and sc_node1.submitter_isCertGenerationActive()["result"]["state"]:
            logging.info("Wait for certificate in mc mempool...")
            time.sleep(2)
        assert_equal(1, mc_node.getmempoolinfo()["size"], "Certificate was not added to Mc node mempool.")

        # generate one MC block for certificate to appear in MC
        # generate one SC block to sync with MC
        we3_end_mcblock_hash = mc_node.generate(1)[0]
        scblock_id3 = generate_next_block(sc_node1, "first node")
        check_mcreferencedata_presence(we3_end_mcblock_hash, scblock_id3, sc_node1)

        node_info_1 = sc_node1.node_info()['result']
        node_info_2 = sc_node2.node_info()['result']

        # sc node 1
        assert_true(node_info_1['nodeName'], 'node0')
        assert_true(node_info_1['nodeType'], 'signer,submitter')
        assert_true(node_info_1['protocolVersion'], '0.0.1')
        assert_true(node_info_1['agentName'], '2-Hop')
        assert_true(node_info_1['sdkVersion'], "sdkVersion should be present")
        assert_true(node_info_1['scId'], self.sc_nodes_bootstrap_info.sidechain_id)
        assert_true(node_info_1['scType'], "non ceasing" if self.sc_nodes_bootstrap_info.is_non_ceasing else "ceasing")
        assert_true(node_info_1['scModel'], 'Account')
        assert_true(node_info_1['scBlockHeight'], 5)
        assert_true(node_info_1['scConsensusEpoch'], 2)
        assert_true(node_info_1['epochForgersStake'], 10000000000)
        assert_true(node_info_1['nextBaseFee'], 512908936)
        assert_true(node_info_1['scWithdrawalEpochLength'], self.sc_nodes_bootstrap_info.withdrawal_epoch_length)
        assert_equal(node_info_1['scWithdrawalEpochNum'], 1)
        assert_true(node_info_1['scEnv'], self.sc_nodes_bootstrap_info.network)
        assert_true(node_info_1['lastMcBlockReferenceHash'], we3_end_mcblock_hash)
        assert_true(node_info_1['numberOfPeers'], 1)
        assert_true(node_info_1['numberOfConnectedPeers'], 1)
        assert_equal(node_info_1['numberOfBlacklistedPeers'], 0)
        assert_true(node_info_1['maxMemPoolSlots'], DEFAULT_MAX_MEMPOOL_SLOTS)
        assert_equal(node_info_1['numOfTxInMempool'], 0)
        assert_equal(node_info_1['executableTxSize'], 0)
        assert_equal(node_info_1['nonExecutableTxSize'], 0)
        assert_equal(node_info_1['lastCertEpoch'], 0)
        assert_equal(node_info_1['lastCertQuality'], 6)
        assert_equal(node_info_1['lastCertBtrFee'], 0)
        assert_equal(node_info_1['lastCertFtMinAmount'], 54)
        assert_true(node_info_1['lastCertHash'], "lastCertHash should be present")
        assert_equal(node_info_1['errors'], [])
        assert_true(list(node_info_1.keys()).__len__(), 29)

        # sc node 2
        assert_true(node_info_2['nodeName'], 'node0')
        assert_true(node_info_2['nodeType'], 'signer')
        assert_true(node_info_2['protocolVersion'], '0.0.1')
        assert_true(node_info_2['agentName'], '2-Hop')
        assert_true(node_info_2['sdkVersion'], "sdkVersion should be present")
        assert_true(node_info_2['scId'], self.sc_nodes_bootstrap_info.sidechain_id)
        assert_true(node_info_2['scType'], "non ceasing" if self.sc_nodes_bootstrap_info.is_non_ceasing else "ceasing")
        assert_true(node_info_2['scModel'], 'Account')
        assert_true(node_info_2['scBlockHeight'], 5)
        assert_true(node_info_2['scConsensusEpoch'], 2)
        assert_true(node_info_2['epochForgersStake'], 10000000000)
        assert_true(node_info_2['nextBaseFee'], 512908936)
        assert_true(node_info_2['scWithdrawalEpochLength'], self.sc_nodes_bootstrap_info.withdrawal_epoch_length)
        assert_equal(node_info_2['scWithdrawalEpochNum'], 1)
        assert_true(node_info_2['scEnv'], self.sc_nodes_bootstrap_info.network)
        assert_true(node_info_2['lastMcBlockReferenceHash'], we3_end_mcblock_hash)
        assert_true(node_info_2['numberOfPeers'], 1)
        assert_true(node_info_2['numberOfConnectedPeers'], 1)
        assert_equal(node_info_2['numberOfBlacklistedPeers'], 0)
        assert_true(node_info_2['maxMemPoolSlots'], DEFAULT_MAX_MEMPOOL_SLOTS)
        assert_equal(node_info_2['numOfTxInMempool'], 0)
        assert_equal(node_info_2['executableTxSize'], 0)
        assert_equal(node_info_2['nonExecutableTxSize'], 0)
        assert_equal(node_info_2['lastCertEpoch'], 0)
        assert_equal(node_info_2['lastCertQuality'], 6)
        assert_equal(node_info_2['lastCertBtrFee'], 0)
        assert_equal(node_info_2['lastCertFtMinAmount'], 54)
        assert_true(node_info_2['lastCertHash'], "lastCertHash should be present")
        assert_equal(node_info_2['errors'], [])
        assert_true(list(node_info_2.keys()).__len__(), 29)


if __name__ == "__main__":
    SCEvmNodeInfo().main()
