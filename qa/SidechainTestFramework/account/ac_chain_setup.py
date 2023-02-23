import logging
from decimal import Decimal

from SidechainTestFramework.sc_boostrap_info import LARGE_WITHDRAWAL_EPOCH_LENGTH, MCConnectionInfo, \
    SCNetworkConfiguration, SCCreationInfo, SCNodeConfiguration
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from SidechainTestFramework.scutil import bootstrap_sidechain_nodes, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND, \
    start_sc_nodes, EVM_APP_BINARY, is_mainchain_block_included_in_sc_block, \
    check_mainchain_block_reference_info, generate_next_block, connect_sc_nodes, AccountModel
from test_framework.util import start_nodes, websocket_port_by_mc_node_index, assert_equal, assert_true, \
    forward_transfer_to_sidechain

from SidechainTestFramework.sc_boostrap_info import KEY_ROTATION_CIRCUIT, SC_CREATION_VERSION_2, \
    SC_CREATION_VERSION_1


class AccountChainSetup(SidechainTestFramework):

    def __init__(self, API_KEY='Horizen', number_of_mc_nodes=1, number_of_sidechain_nodes=1,
                 withdrawalEpochLength=LARGE_WITHDRAWAL_EPOCH_LENGTH, forward_amount=100,
                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND, forger_options=None,
                 initial_private_keys=None, circuittype_override=None, remote_keys_manager_enabled=False,
                 remote_keys_server_address=None, max_incoming_connections=100):
        super().__init__()

        self.evm_address = None
        self.sc_nodes = None
        self.sc_nodes_bootstrap_info = None
        self.mc_return_address = None
        self.block_id = None
        self.ft_amount_in_zen = None
        self.API_KEY = API_KEY
        self.number_of_mc_nodes = number_of_mc_nodes
        self.number_of_sidechain_nodes = number_of_sidechain_nodes
        self.withdrawalEpochLength = withdrawalEpochLength
        self.forward_amount = forward_amount
        self.block_timestamp_rewind = block_timestamp_rewind
        self.forger_options = forger_options
        self.initial_private_keys = initial_private_keys
        self.circuittype_override = circuittype_override
        self.remote_keys_manager_enabled = remote_keys_manager_enabled
        self.remote_keys_server_address = remote_keys_server_address
        self.max_incoming_connections = max_incoming_connections

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sc_setup_network(self):
        self.sc_nodes = self.sc_setup_nodes()
        if self.number_of_sidechain_nodes > 2:
            for i in range(self.number_of_sidechain_nodes - 1):
                connect_sc_nodes(self.sc_nodes[i], i + 1)
            connect_sc_nodes(self.sc_nodes[self.number_of_sidechain_nodes - 1], 0)
            self.sync_all()
        elif self.number_of_sidechain_nodes == 2:
            connect_sc_nodes(self.sc_nodes[0], 1)
            self.sync_all()

    def sc_setup_chain(self):
        mc_node = self.nodes[0]
        sc_node_configuration = []

        for x in range(self.number_of_sidechain_nodes):
            if self.forger_options is None:
                sc_node_configuration.append(SCNodeConfiguration(
                    MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                    api_key=self.API_KEY,
                    remote_keys_manager_enabled=self.remote_keys_manager_enabled,
                    remote_keys_server_address=self.remote_keys_server_address,
                    max_incoming_connections=self.max_incoming_connections))
            else:
                sc_node_configuration.append(SCNodeConfiguration(
                    MCConnectionInfo(
                        address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                    forger_options=self.forger_options,
                    api_key=self.API_KEY,
                    initial_private_keys=self.initial_private_keys,
                    remote_keys_manager_enabled=self.remote_keys_manager_enabled,
                    remote_keys_server_address=self.remote_keys_server_address,
                    max_incoming_connections=self.max_incoming_connections))

        if self.circuittype_override is not None:
            circuit_type = self.circuittype_override
        else:
            circuit_type = self.options.certcircuittype

        if circuit_type == KEY_ROTATION_CIRCUIT:
            sc_creation_version = SC_CREATION_VERSION_2  # non-ceasing could be only SC_CREATION_VERSION_2>=2
        else:
            sc_creation_version = SC_CREATION_VERSION_1

        network = SCNetworkConfiguration(SCCreationInfo(mc_node, self.forward_amount, self.withdrawalEpochLength,
                                                        sc_creation_version=sc_creation_version,
                                                        is_non_ceasing=self.options.nonceasing,
                                                        circuit_type=circuit_type),
                                         *sc_node_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network,
                                                                 block_timestamp_rewind=self.block_timestamp_rewind,
                                                                 model=AccountModel)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes, dirname=self.options.tmpdir,
                              auth_api_key=self.API_KEY,
                              binary=[EVM_APP_BINARY] * self.number_of_sidechain_nodes,
                              extra_args=self.debug_extra_args)

    def sc_ac_setup(self, wallet=True, forwardTransfer=True, ft_amount_in_zen=Decimal("33.22")):
        sc_node = self.sc_nodes[0]
        mc_node = self.nodes[0]
        self.mc_return_address = mc_node.getnewaddress()
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
            self.ft_amount_in_zen = ft_amount_in_zen
            # transfer some fund from MC to SC using the evm address created before
            forward_transfer_to_sidechain(self.sc_nodes_bootstrap_info.sidechain_id,
                                          self.nodes[0],
                                          self.evm_address[2:],
                                          ft_amount_in_zen,
                                          self.mc_return_address)

            self.block_id = generate_next_block(sc_node, "first node", force_switch_to_next_epoch=True)
            self.sc_sync_all()

            sc_best_block = sc_node.block_best()["result"]
            logging.info(sc_best_block)
