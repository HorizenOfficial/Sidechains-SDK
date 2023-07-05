#!/usr/bin/env python3
import json
import logging
import os
import shutil
import sys
import tempfile
import traceback

from SidechainTestFramework.sc_boostrap_info import LARGE_WITHDRAWAL_EPOCH_LENGTH, SCNetworkConfiguration, \
     SCNodeConfiguration, SCCreationInfo, MCConnectionInfo,  NO_KEY_ROTATION_CIRCUIT, KEY_ROTATION_CIRCUIT,\
     SC_CREATION_VERSION_2, SC_CREATION_VERSION_1
from SidechainTestFramework.scutil import APP_LEVEL_DEBUG, TEST_LEVEL_INFO, TEST_LEVEL_DEBUG, \
     start_sc_nodes, stop_sc_nodes, DefaultModel, UtxoModel, AccountModel, \
     sync_sc_blocks, sync_sc_mempools, TimeoutException, bootstrap_sidechain_nodes, APP_LEVEL_INFO, \
     set_sc_parallel_test, EVM_APP_BINARY, SIMPLE_APP_BINARY
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from test_framework.authproxy import JSONRPCException
from test_framework.test_framework import BitcoinTestFramework
from test_framework.util import check_json_precision, initialize_chain_clean, start_nodes, stop_nodes, \
     wait_bitcoinds, websocket_port_by_mc_node_index, set_mc_parallel_test, connect_nodes_bi, sync_blocks,\
    sync_mempools

from SidechainTestFramework.sc_boostrap_info import SCForgerConfiguration, DEFAULT_MAX_NONCE_GAP, \
    DEFAULT_MAX_ACCOUNT_SLOTS, DEFAULT_MAX_MEMPOOL_SLOTS, DEFAULT_MAX_NONEXEC_POOL_SLOTS, DEFAULT_TX_LIFETIME
from SidechainTestFramework.scutil import connect_sc_nodes, DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND

'''
This class implements a base framework for tests needing more than one sidechain.
By default, it creates:
 - a mainchain with 1 node
 - 1 utxo and 1 account sidechain, both with just 1 node. 
For customizing MC behavior, override setup_chain or setup_network methods.
For customizing SC behavior:
- override sc_create_sidechains method for adding new/different sidechains
- subtype SidechainInfo classes for customizing the specific sidechain

The workflow is the following:
1- add_options           (for MC nodes)
2- sc_add_options        (for SC nodes)
3- setup_chain           (for MC nodes)
4- setup_network         (for MC nodes)
5- sc_setup_sidechains   (for SC)


'''


class MultiSidechainTestFramework(BitcoinTestFramework):

    def __init__(self,
                 number_of_mc_nodes=1
                 ):

        super().__init__()
        self.number_of_mc_nodes = number_of_mc_nodes
        self.sidechains = []

    def set_parallel_test(self, n):
        set_mc_parallel_test(n)
        set_sc_parallel_test(n)

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, self.number_of_mc_nodes)

    def setup_network(self):
        self.nodes = self.setup_nodes()
        for n in range(self.number_of_mc_nodes - 1):
            connect_nodes_bi(self.nodes, n, n + 1)
        self.sync_all()

    def setup_nodes(self):
        return start_nodes(self.number_of_mc_nodes, self.options.tmpdir)

    def sync_all(self):
        sync_blocks(self.nodes)
        sync_mempools(self.nodes)

    def sc_add_options(self, parser):
        pass

    def sc_create_sidechains(self):
        self.sidechains.append(UTXOSidechainInfo(self.options))
        self.sidechains.append(AccountSidechainInfo(self.options))

    def sc_setup_sidechains(self):

        self.sc_create_sidechains()

        if self.options.sidechain_opts is None:
            sidechain_opts = {}
        else:
            sidechain_opts = json.loads(self.options.sidechain_opts.replace("'", "\""))

        mc_node_1 = self.nodes[0]
        for sidechain in self.sidechains:
            sidechain.handle_debug_option()
            sidechain.sc_setup_chain(mc_node_1, sidechain_opts.get(str(sidechain.sc_num)))
            sidechain.sc_setup_network()

    def run_test(self):
        pass

    def setup_logger(self,  options):
        logfile = os.path.abspath(os.path.join(os.path.dirname(__file__), "../", "sc_test.log"))
        filehandler = logging.FileHandler(logfile, "a+")
        streamhandler = logging.StreamHandler()

        if self.options.trace_rpc:
            filehandler.setLevel(logging.DEBUG)
            streamhandler.setLevel(logging.DEBUG)
        else:
            filehandler.setLevel(options.testlogfilelevel.upper())
            streamhandler.setLevel(options.testlogconsolelevel.upper())

        logging_format = "[%(asctime)s] : [%(levelname)s] : %(message)s"
        if options.parallel > 0:
            logging_format = f"[ParallelGroup: {self.options.parallel}] : {logging_format}"

        logging.basicConfig(format=logging_format,
                            handlers=[filehandler, streamhandler],
                            level=logging.DEBUG
                            )

    def main(self):
        import optparse

        parser = optparse.OptionParser(usage="%prog [options]")
        parser.add_option("--nocleanup", dest="nocleanup", default=False, action="store_true",
                          help="Leave datadir on exit or error")
        parser.add_option("--noshutdown", dest="noshutdown", default=False, action="store_true",
                          help="Don't stop nodes after the test execution")
        parser.add_option("--zendir", dest="zendir", default="ZenCore/src",
                          help="Source directory containing zend/zen-cli (default: %default)")
        parser.add_option("--scutxojarpath", dest="scutxojarpath",
                          default=f"{SIMPLE_APP_BINARY}",  # New option. Main class path won't be needed in future
                          help="jar file and main class for UTXO SC (default: %default)")
        parser.add_option("--scevmjarpath", dest="scevmjarpath",
                          default=f"{EVM_APP_BINARY}",  # New option. Main class path won't be needed in future
                          help="jar file and main class for EVM SC (default: %default)")
        parser.add_option("--tmpdir", dest="tmpdir", default=tempfile.mkdtemp(prefix="sc_test"),
                          help="Root directory for datadirs")
        parser.add_option("--tracerpc", dest="trace_rpc", default=False, action="store_true",
                          help="Print out all RPC calls as they are made")
        parser.add_option("--restapitimeout", dest="restapitimeout", default=5, action="store",
                          help="timeout in seconds for rest API execution, might be useful when debugging")
        parser.add_option("--logfilelevel", dest="logfilelevel", default=APP_LEVEL_DEBUG, action="store",
                          help="log4j log level for application log file")
        parser.add_option("--logconsolelevel", dest="logconsolelevel", default=APP_LEVEL_INFO, action="store",
                          help="log4j log level for application console")
        parser.add_option("--testlogfilelevel", dest="testlogfilelevel", default=TEST_LEVEL_DEBUG, action="store",
                          help="log level for test log file")
        parser.add_option("--testlogconsolelevel", dest="testlogconsolelevel", default=TEST_LEVEL_INFO, action="store",
                          help="log level for test console")
        parser.add_option("--debugnode", dest="debugnode", default=None, action="store",
                          help="Index of the SC node to debug. Adds -agentlib option to java VM. "
                               "Format: --debugnode=[sidechain index, node index].")
        parser.add_option("--sidechain_opts", dest="sidechain_opts", default=None, action="store",
                          help="Stores sidechain-specific options. They are specified as a dictionary in json format, "
                               "using sidechain index as key and another dictionary as value. "
                               """Format: 
                               --sidechain_opts="{'sc_idx_1': {'key_1':'value_1', 'key_2':'value_2'}, 'sc_idx_2': {...}}"
                               Supported sidechain options are: 
                                - "nonceasing": boolean, specify if sidechain is non-ceasing. Default is False.
                                - "certcircuittype": Type of certificate circuit: NaiveThresholdSignatureCircuit or
                                NaiveThresholdSignatureCircuitWithKeyRotation""")
        parser.add_option("--parallel", dest="parallel", type=int, default=0, action="store",
                          help="Stores parallel process integer assigned to current test")

        self.add_options(parser)
        self.sc_add_options(parser)
        (self.options, self.args) = parser.parse_args()
        self.setup_logger(self.options)

        os.environ['PATH'] = self.options.zendir+":"+os.environ['PATH']

        check_json_precision()

        success = False
        try:
            if not os.path.isdir(self.options.tmpdir):
                os.makedirs(self.options.tmpdir)

            logging.info("Initializing test directory "+self.options.tmpdir)

            parallel_group = int(self.options.parallel)
            if parallel_group > 0:
                self.set_parallel_test(parallel_group)

            self.setup_chain()

            self.setup_network()

            self.sc_setup_sidechains()

            self.run_test()

            success = True

        except JSONRPCException as e:
            logging.error("JSONRPC error: "+e.error['message'])
            traceback.print_tb(sys.exc_info()[2])
        except SCAPIException as e:  # New exception for SC API
            logging.error("SCAPI error: "+e.error)
            traceback.print_tb(sys.exc_info()[2])
        except TimeoutException as e:
            logging.error("Timeout while: " + e.operation)  # Timeout for SC Operations
            traceback.print_tb(sys.exc_info()[2])
        except AssertionError as msg:
            logging.error("Assertion failed: " + str(msg))
            traceback.print_tb(sys.exc_info()[2])
        except Exception as e:
            logging.error("Unexpected exception caught during testing: "+str(e))
            traceback.print_tb(sys.exc_info()[2])

        if not self.options.noshutdown:  # Support for tests with MC only, SC only, MC/SC
            if hasattr(self, "sidechains"):
                for sidechain in self.sidechains:
                    if hasattr(sidechain, "sc_nodes"):
                        logging.info("Stopping SC nodes")
                        stop_sc_nodes(sidechain.sc_nodes, sidechain.sc_num)
            if hasattr(self, "nodes"):
                logging.info("Stopping MC nodes")
                stop_nodes(self.nodes)
                wait_bitcoinds()
        else:
            logging.info("Note: client processes were not stopped and may still be running")

        if success and not self.options.nocleanup and not self.options.noshutdown:
            logging.info("Cleaning up")
            shutil.rmtree(self.options.tmpdir)

        if success:
            logging.info("Test successful")
            sys.exit(0)
        else:
            logging.error("Failed")
            sys.exit(1)


class SidechainInfo(object):

    num_of_sidechains = 0

    def __init__(self,
                 options,
                 number_of_sidechain_nodes=1,
                 model=DefaultModel,
                 API_KEY='Horizen',
                 withdrawalEpochLength=LARGE_WITHDRAWAL_EPOCH_LENGTH,
                 forward_amount=100,
                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                 forger_options=SCForgerConfiguration(),
                 initial_private_keys=[],
                 circuittype_override=None,
                 remote_keys_manager_enabled=False,
                 remote_keys_server_addresses=[],
                 max_incoming_connections=100,
                 connect_nodes=True,
                 # Array of websocket ports. Node N will establish the connection to port N, if defined.
                 # Otherwise, websocket connection is skipped. For example, [5100, None, 5101]
                 websocket_server_ports=[],
                 cert_max_keys=7,
                 cert_sig_threshold=5,
                 # Array of arrays of signer keys indexes owned by the nodes. For example, [[0,1], [2,4]]
                 # If no value for given Node N index is present then the default value is assigned later: range(7)
                 submitters_private_keys_indexes=[],
                 magic_bytes=None,
                 sc2sc_proving_key_file_name=None,
                 sc2sc_verification_key_file_name=None
                 ):

        super().__init__()
        self.sc_num = SidechainInfo.num_of_sidechains
        SidechainInfo.num_of_sidechains = SidechainInfo.num_of_sidechains + 1
        self.number_of_sidechain_nodes = number_of_sidechain_nodes
        self.options = options
        self.name = model + str(self.sc_num)
        self.datadir = os.path.join(self.options.tmpdir, self.name)
        if not os.path.isdir(self.datadir):
            os.makedirs(self.datadir)
        self.model = model
        self.debug_extra_args = None
        self.sc_nodes = None
        self.sc_nodes_bootstrap_info = None

        self.API_KEY = API_KEY
        self.ft_amount_in_zen = None
        self.withdrawalEpochLength = withdrawalEpochLength
        self.forward_amount = forward_amount
        self.block_timestamp_rewind = block_timestamp_rewind
        self.forger_options = forger_options
        self.initial_private_keys = initial_private_keys
        self.circuittype_override = circuittype_override
        self.remote_keys_manager_enabled = remote_keys_manager_enabled
        self.remote_keys_server_addresses = remote_keys_server_addresses
        self.max_incoming_connections = max_incoming_connections
        self.connect_nodes = connect_nodes
        if len(websocket_server_ports) == 0:
            websocket_server_ports = [None] * number_of_sidechain_nodes
        assert(len(websocket_server_ports) == number_of_sidechain_nodes)
        self.websocket_server_ports = websocket_server_ports
        self.cert_max_keys = cert_max_keys
        self.cert_sig_threshold = cert_sig_threshold
        self.submitters_private_keys_indexes = submitters_private_keys_indexes
        self.magic_bytes = magic_bytes if magic_bytes is not None else list(os.urandom(4))
        self.sc2sc_proving_key_file_name=sc2sc_proving_key_file_name
        self.sc2sc_verification_key_file_name=sc2sc_verification_key_file_name

    def sc_setup_nodes_configuration(self, mc_node):
        sc_nodes_configuration = []
        for x in range(self.number_of_sidechain_nodes):
            sc_nodes_configuration.append(SCNodeConfiguration(
                MCConnectionInfo(address="ws://{0}:{1}".format(mc_node.hostname, websocket_port_by_mc_node_index(0))),
                forger_options=self.forger_options,
                api_key=self.API_KEY,
                initial_private_keys=self.initial_private_keys,
                remote_keys_manager_enabled=self.remote_keys_manager_enabled,
                remote_keys_server_address=self.remote_keys_server_addresses[x]
                if len(self.remote_keys_server_addresses) > x else "",
                max_incoming_connections=self.max_incoming_connections,
                websocket_server_enabled=(self.websocket_server_ports[x] is not None),
                websocket_server_port=self.websocket_server_ports[x]
                if self.websocket_server_ports[x] is not None else 0,
                cert_submitter_enabled=(x == 0),  # last first is a submitter
                submitter_private_keys_indexes=self.submitters_private_keys_indexes[x]
                if len(self.submitters_private_keys_indexes) > x else None,
                magic_bytes=self.magic_bytes,
                sc2sc_proving_key_file_path=os.path.join(self.options.tmpdir, self.sc2sc_proving_key_file_name) if self.sc2sc_proving_key_file_name is not None else None,
                sc2sc_verification_key_file_path=os.path.join(self.options.tmpdir, self.sc2sc_verification_key_file_name) if self.sc2sc_verification_key_file_name is not None else None
            ))

        return sc_nodes_configuration

    def sc_setup_chain(self, mc_node, sidechain_opts):
        if len(self.submitters_private_keys_indexes) > self.number_of_sidechain_nodes:
            raise ValueError("Number of submitters_private_keys_indexes configs must be <= number_of_sidechain_nodes")
        for node_indexes in self.submitters_private_keys_indexes:
            if len(node_indexes) > self.cert_max_keys:
                raise ValueError("node_indexes must be <= cert_max_keys")

        sc_nodes_configuration = self.sc_setup_nodes_configuration(mc_node)

        cert_circuit_type = NO_KEY_ROTATION_CIRCUIT
        is_non_ceasing = False
        sc_creation_version = SC_CREATION_VERSION_1
        if sidechain_opts is not None:
            if sidechain_opts.get('nonceasing') is not None:
                is_non_ceasing = sidechain_opts['nonceasing'].casefold() == "true".casefold()
            if sidechain_opts.get('certcircuittype') is not None:
                cert_circuit_type = sidechain_opts['certcircuittype']

        if self.circuittype_override is not None:
            cert_circuit_type = self.circuittype_override
        if cert_circuit_type == KEY_ROTATION_CIRCUIT:
            sc_creation_version = SC_CREATION_VERSION_2  # non-ceasing could be only SC_CREATION_VERSION_2>=2

        network = SCNetworkConfiguration(SCCreationInfo(mc_node,
                                                        self.forward_amount,
                                                        self.withdrawalEpochLength,
                                                        sc_creation_version=sc_creation_version,
                                                        is_non_ceasing=is_non_ceasing,
                                                        circuit_type=cert_circuit_type,
                                                        cert_max_keys=self.cert_max_keys,
                                                        cert_sig_threshold=self.cert_sig_threshold,
                                                        ),
                                         *sc_nodes_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options,
                                                                 network,
                                                                 block_timestamp_rewind=self.block_timestamp_rewind,
                                                                 model=self.model,
                                                                 tmpdir=self.datadir,
                                                                 sc_num=self.sc_num)

    def sc_setup_network(self):
        self.sc_nodes = self.sc_setup_nodes()
        if self.connect_nodes:
            for n in range(self.number_of_sidechain_nodes - 1):
                connect_sc_nodes(self.sc_nodes[n], n + 1, sc_num=self.sc_num)
            self.sc_sync_all()

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes,
                              auth_api_key=self.API_KEY,
                              dirname=self.datadir,
                              extra_args=self.debug_extra_args,
                              sc_num=self.sc_num)

    def sc_sync_all(self, mempool_cardinality_only=False):
        sync_sc_blocks(self.sc_nodes)
        sync_sc_mempools(self.sc_nodes, mempool_cardinality_only=mempool_cardinality_only)

    def sc_sync_nodes(self, sc_nodes):
        sync_sc_blocks(sc_nodes)
        sync_sc_mempools(sc_nodes)

    def handle_debug_option(self):
        if self.options.debugnode is not None:
            args = self.options.debugnode.strip('][').split(',')
            if len(args) < 2:
                raise RuntimeError("\n===> Error: could not handle --debugnode option. "
                                   "It should be --debugnode=[sc_idx,node_idx]")
            sc_idx = int(args[0])
            if not (0 <= sc_idx < SidechainInfo.num_of_sidechains):
                raise RuntimeError(
                    "\n===> Error: could not handle --debugnode option. Sidechain index {} out of range [{}, {}]"
                    .format(sc_idx, 0, SidechainInfo.num_of_sidechains - 1))
            if sc_idx == self.sc_num:
                sc_node_index = int(args[1])
                if not (0 <= sc_node_index < self.number_of_sidechain_nodes):
                    raise RuntimeError("\n===> Error: could not handle --debugnode option. "
                                       "Node index {} for sidechain {} out of range [{}, {}]"
                                       .format(sc_node_index, sc_idx, 0, self.number_of_sidechain_nodes - 1))

                self.debug_extra_args = []
                for i in range(0, self.number_of_sidechain_nodes):
                    if i == sc_node_index:
                        self.debug_extra_args.append(['-agentlib'])
                    else:
                        self.debug_extra_args.append([''])


class UTXOSidechainInfo(SidechainInfo):

    def __init__(self,
                 options,
                 number_of_sidechain_nodes=1,
                 API_KEY='Horizen',
                 withdrawalEpochLength=LARGE_WITHDRAWAL_EPOCH_LENGTH,
                 forward_amount=100,
                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                 forger_options=SCForgerConfiguration(),
                 initial_private_keys=[],
                 circuittype_override=None,
                 remote_keys_manager_enabled=False,
                 remote_keys_server_addresses=[],
                 max_incoming_connections=100,
                 connect_nodes=True,
                 websocket_server_ports=[],
                 cert_max_keys=7,
                 cert_sig_threshold=5,
                 submitters_private_keys_indexes=[],
                 magic_bytes=None
                 ):

        super().__init__(
                         options,
                         number_of_sidechain_nodes,
                         UtxoModel,
                         API_KEY,
                         withdrawalEpochLength,
                         forward_amount,
                         block_timestamp_rewind,
                         forger_options,
                         initial_private_keys,
                         circuittype_override,
                         remote_keys_manager_enabled,
                         remote_keys_server_addresses,
                         max_incoming_connections,
                         connect_nodes,
                         websocket_server_ports,
                         cert_max_keys,
                         cert_sig_threshold,
                         submitters_private_keys_indexes,
                         magic_bytes)

    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes,
                              dirname=self.datadir,
                              auth_api_key=self.API_KEY,
                              binary=[self.options.scutxojarpath] * self.number_of_sidechain_nodes,
                              extra_args=self.debug_extra_args,
                              sc_num=self.sc_num)


class AccountSidechainInfo(SidechainInfo):

    def __init__(self,
                 options,
                 number_of_sidechain_nodes=1,
                 API_KEY='Horizen',
                 withdrawalEpochLength=LARGE_WITHDRAWAL_EPOCH_LENGTH,
                 forward_amount=100,
                 block_timestamp_rewind=DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND,
                 forger_options=SCForgerConfiguration(),
                 initial_private_keys=[],
                 circuittype_override=None,
                 remote_keys_manager_enabled=False,
                 remote_keys_server_addresses=[],
                 max_incoming_connections=100,
                 connect_nodes=True,
                 websocket_server_ports=[],
                 cert_max_keys=7,
                 cert_sig_threshold=5,
                 submitters_private_keys_indexes=[],
                 magic_bytes=None,
                 allow_unprotected_txs=True,
                 max_nonce_gap=DEFAULT_MAX_NONCE_GAP,
                 max_account_slots=DEFAULT_MAX_ACCOUNT_SLOTS,
                 max_mempool_slots=DEFAULT_MAX_MEMPOOL_SLOTS,
                 max_nonexec_pool_slots=DEFAULT_MAX_NONEXEC_POOL_SLOTS,
                 tx_lifetime=DEFAULT_TX_LIFETIME,
                 sc2sc_proving_key_file_name=None,
                 sc2sc_verification_key_file_name=None
                 ):

        super().__init__(
                         options,
                         number_of_sidechain_nodes,
                         AccountModel,
                         API_KEY,
                         withdrawalEpochLength,
                         forward_amount,
                         block_timestamp_rewind,
                         forger_options,
                         initial_private_keys,
                         circuittype_override,
                         remote_keys_manager_enabled,
                         remote_keys_server_addresses,
                         max_incoming_connections,
                         connect_nodes,
                         websocket_server_ports,
                         cert_max_keys,
                         cert_sig_threshold,
                         submitters_private_keys_indexes,
                         magic_bytes,
                         sc2sc_proving_key_file_name,
                         sc2sc_verification_key_file_name
        )
        self.allow_unprotected_txs = allow_unprotected_txs
        self.max_nonce_gap = max_nonce_gap
        self.max_account_slots = max_account_slots
        self.max_mempool_slots = max_mempool_slots
        self.max_nonexec_pool_slots = max_nonexec_pool_slots
        self.tx_lifetime = tx_lifetime


    def sc_setup_nodes(self):
        return start_sc_nodes(self.number_of_sidechain_nodes,
                              dirname=self.datadir,
                              auth_api_key=self.API_KEY,
                              binary=[self.options.scevmjarpath] * self.number_of_sidechain_nodes,
                              extra_args=self.debug_extra_args,
                              sc_num=self.sc_num)

    def sc_setup_nodes_configuration(self, mc_node):
        sc_nodes_configuration = super().sc_setup_nodes_configuration(mc_node)
        for node_conf in sc_nodes_configuration:
            node_conf.allow_unprotected_txs = self.allow_unprotected_txs
            node_conf.max_nonce_gap = self.max_nonce_gap
            node_conf.max_account_slots = self.max_account_slots
            node_conf.max_mempool_slots = self.max_mempool_slots
            node_conf.max_nonexec_pool_slots = self.max_nonexec_pool_slots
            node_conf.tx_lifetime = self.tx_lifetime

        return sc_nodes_configuration
