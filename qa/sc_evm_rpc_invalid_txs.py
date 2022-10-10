#!/usr/bin/env python3
import logging
from decimal import Decimal

from SidechainTestFramework.account.address_util import format_evm
from SidechainTestFramework.account.eoa_util import eoa_transaction
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration, LARGE_WITHDRAWAL_EPOCH_LENGTH
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, start_sc_nodes, generate_next_block, \
    EVM_APP_BINARY, AccountModelBlockVersion, assert_true, convertZenToWei
from test_framework.util import start_nodes, \
    websocket_port_by_mc_node_index, forward_transfer_to_sidechain

"""
Check that sending an invalid transaction to the RPC method eth_sendRawTransaction returns an error
"""


class SCEvmRPCInvalidTx(SidechainTestFramework):
    sc_nodes_bootstrap_info = None
    number_of_mc_nodes = 1
    number_of_sidechain_nodes = 1

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_network(self, split=False):
        self.sc_nodes = self.sc_setup_nodes()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(
            SCCreationInfo(mc_node, 100, LARGE_WITHDRAWAL_EPOCH_LENGTH),
            sc_node_1_configuration
        )
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(
            self.options, network,
            block_timestamp_rewind=720 * 120 * 5,
            blockversion=AccountModelBlockVersion
        )

    def sc_setup_nodes(self):
        return start_sc_nodes(
            self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
            binary=[EVM_APP_BINARY] * 2,
            # extra_args=[['-agentlib'], []],
        )

    def run_test(self):
        mc_node = self.nodes[0]
        sc_node_1 = self.sc_nodes[0]

        # transfer some fund from MC to SC1 at a new evm address, then mine mc block
        evm_address_sc1 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
        evm_address_sc2 = sc_node_1.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]

        ft_amount_in_zen = Decimal('3000.0')
        forward_transfer_to_sidechain(
            self.sc_nodes_bootstrap_info.sidechain_id,
            mc_node,
            evm_address_sc1,
            ft_amount_in_zen,
            mc_return_address=mc_node.getnewaddress(),
            generate_block=True
        )
        self.sync_all()

        generate_next_block(sc_node_1, "first node")
        self.sc_sync_all()

        # test that sending an invalid transaction to eth_sendRawTransaction fails with an error
        # the tx is semantically invalid because the supplied gas limit is below the required intrinsic gas
        exception_occured = False
        try:
            res = eoa_transaction(
                sc_node_1, gas=20000,
                from_addr=format_evm(evm_address_sc1), to_addr=format_evm(evm_address_sc2), value=convertZenToWei(1)
            )
            logging.error("invalid transaction was accepted via RPC api: {}".format(str(res)))
        except RuntimeError as err:
            logging.debug("invalid transaction was rejected with: {}".format(str(err)))
            if str(err).find("gas limit is below intrinsic gas") != -1:
                exception_occured = True
        assert_true(exception_occured, "invalid transaction should be rejected by RPC api")


if __name__ == "__main__":
    SCEvmRPCInvalidTx().main()
