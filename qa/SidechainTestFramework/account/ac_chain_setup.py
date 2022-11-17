import logging
from decimal import Decimal

from SidechainTestFramework.sc_boostrap_info import LARGE_WITHDRAWAL_EPOCH_LENGTH, MCConnectionInfo, \
    SCNetworkConfiguration, SCCreationInfo, SCNodeConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND, \
    AccountModelBlockVersion, start_sc_nodes, EVM_APP_BINARY, is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, generate_next_block
from test_framework.util import start_nodes, websocket_port_by_mc_node_index, assert_equal, assert_true, \
    forward_transfer_to_sidechain


class AccountChainSetup(SidechainTestFramework):

    def __init__(self, API_KEY='Horizen', number_of_mc_nodes=1, number_of_sidechain_nodes=1,
                 withdrawalEpochLength=LARGE_WITHDRAWAL_EPOCH_LENGTH):
        self.evm_address = None
        self.sc_nodes = None
        self.sc_nodes_bootstrap_info = None
        self.API_KEY = API_KEY
        self.number_of_mc_nodes = number_of_mc_nodes
        self.number_of_sidechain_nodes = number_of_sidechain_nodes
        self.withdrawalEpochLength = withdrawalEpochLength

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_network(self):
        self.sc_nodes = self.sc_setup_nodes()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
            api_key=self.API_KEY
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node, 100, self.withdrawalEpochLength),
                                         sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                                                                 blockversion=AccountModelBlockVersion)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              auth_api_key=self.API_KEY,
                              binary=[EVM_APP_BINARY])  # , extra_args=['-agentlib'])

    def sc_ac_setup(self, wallet=True, forwardTransfer=True, ft_amount_in_zen=Decimal("33.22")):
        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]
        mc_return_address = mc_node.getnewaddress()
        mc_block = mc_node.getblock(str(self.sc_nodes_bootstrap_info.mainchain_block_height))
        mc_block_hex = mc_node.getblock(mc_block["hash"], False)
        logging.info("SC genesis mc block hex = " + mc_block_hex)

        sc_best_block = sc_node.block_best()["result"]

        assert_equal(sc_best_block["height"], 1, "The best block has not the specified height.")

        # verify MC block reference's inclusion
        res = is_mainchain_block_included_in_sc_block(sc_best_block["block"], mc_block)
        assert_true(res, "The mainchain block is not included in SC node.")

        sc_mc_best_block_ref_info = sc_node.mainchain_bestBlockReferenceInfo()["result"]
        assert_true(
            check_mainchain_block_reference_info(sc_mc_best_block_ref_info, mc_block),
            "The mainchain block is not included inside SC block reference info.")

        if wallet:
            self.evm_address = '0x' + sc_node.wallet_createPrivateKeySecp256k1()["result"]["proposition"]["address"]
            logging.info("pubkey = {}".format(self.evm_address))
        else:
            forwardTransfer = False

        if forwardTransfer:
            # transfer some fund from MC to SC using the evm address created before
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                          self.nodes[0],
                                          self.evm_address[2:],
                                          ft_amount_in_zen,
                                          mc_return_address)

            generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

            sc_best_block = sc_node.block_best()["result"]
            logging.info(sc_best_block)
