#!/usr/bin/env python3
from netrc import netrc

from SidechainTestFramework.sc_boostrap_info import SCNetworkConfiguration, SCBootstrapInfo, \
    LARGE_WITHDRAWAL_EPOCH_LENGTH
from test_framework.test_framework import BitcoinTestFramework
from test_framework.authproxy import JSONRPCException
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from test_framework.util import check_json_precision, \
    initialize_chain_clean, \
    start_nodes, stop_nodes, \
    sync_blocks, sync_mempools, wait_bitcoinds, websocket_port_by_mc_node_index
from SidechainTestFramework.scutil import initialize_default_sc_chain_clean, \
    start_sc_nodes, stop_sc_nodes, \
    sync_sc_blocks, sync_sc_mempools, TimeoutException, bootstrap_sidechain_nodes
import os
import tempfile
import traceback
import sys
import shutil
from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCCreationInfo, MCConnectionInfo, \
    SCNetworkConfiguration

from SidechainTestFramework.scutil import LEVEL_ERROR, LEVEL_DEBUG

'''
The workflow is the following:
1- add_options      (for MC nodes)
2- sc_add_options   (for SC nodes)
3- setup_chain      (for MC nodes)
4- setup_network    (for MC nodes)
5- sc_setup_chain   (for SC nodes)
6- sc_setup_network (for SC nodes)

Override the proper methods if you want to change default behavior.

Default behavior: the framework starts 1 SC node connected to 1 MC node.
            *************          *************
            * SC Node 1 *  <---->  * MC Node 1 *
            *************          *************

'''
class SidechainTestFramework(BitcoinTestFramework):

    def add_options(self, parser):
        pass

    def setup_chain(self):
        initialize_chain_clean(self.options.tmpdir, 1)

    def setup_network(self, split = False):
        self.nodes = self.setup_nodes()
        self.sync_all()

    def setup_nodes(self):
        return start_nodes(1, self.options.tmpdir)

    def split_network(self):
        pass

    def sync_all(self):
        sync_blocks(self.nodes)
        sync_mempools(self.nodes)

    def sync_nodes(self, mc_nodes):
        sync_blocks(mc_nodes)
        sync_mempools(mc_nodes)

    def join_network(self):
        pass

    def sc_add_options(self, parser):
        pass

    def sc_setup_chain(self):
        mc_node_1 = self.nodes[0]
        sc_node_1_configuration = SCNodeConfiguration(
            MCConnectionInfo(address="ws://{0}:{1}".format(mc_node_1.hostname, websocket_port_by_mc_node_index(0)))
        )
        network = SCNetworkConfiguration(SCCreationInfo(mc_node_1, 600, LARGE_WITHDRAWAL_EPOCH_LENGTH), sc_node_1_configuration)
        self.sc_nodes_bootstrap_info = bootstrap_sidechain_nodes(self.options, network)

    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        self.sc_sync_all()

    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)

    def sc_split_network(self):
        pass

    def sc_sync_all(self):
        sync_sc_blocks(self.sc_nodes)
        sync_sc_mempools(self.sc_nodes)

    def sc_sync_nodes(self, sc_nodes):
        sync_sc_blocks(sc_nodes)
        sync_sc_mempools(sc_nodes)

    def sc_join_network(self):
        pass

    def run_test(self):
        pass

    def main(self):
        import optparse

        parser = optparse.OptionParser(usage="%prog [options]")
        parser.add_option("--nocleanup", dest="nocleanup", default=False, action="store_true",
                          help="Leave bitcoinds and test.* datadir on exit or error")
        parser.add_option("--noshutdown", dest="noshutdown", default=False, action="store_true",
                          help="Don't stop bitcoinds after the test execution")
        parser.add_option("--zendir", dest="zendir", default="ZenCore/src",
                          help="Source directory containing zend/zen-cli (default: %default)")
        parser.add_option("--scjarpath", dest="scjarpath", default="../examples/simpleapp/target/sidechains-sdk-simpleapp-0.3.0.jar;../examples/simpleapp/target/lib/* com.horizen.examples.SimpleApp", #New option. Main class path won't be needed in future
                          help="Directory containing .jar file for SC (default: %default)")
        parser.add_option("--tmpdir", dest="tmpdir", default=tempfile.mkdtemp(prefix="sc_test"),
                          help="Root directory for datadirs")
        parser.add_option("--tracerpc", dest="trace_rpc", default=False, action="store_true",
                          help="Print out all RPC calls as they are made")
        parser.add_option("--restapitimeout", dest="restapitimeout", default=5, action="store",
                          help="timeout in seconds for rest API execution, might be useful when debugging")
        parser.add_option("--logfilelevel", dest="logfilelevel", default=LEVEL_DEBUG, action="store",
                          help="log4j log level for application log file")
        parser.add_option("--logconsolelevel", dest="logconsolelevel", default=LEVEL_ERROR, action="store",
                          help="log4j log level for application console")

        self.add_options(parser)
        self.sc_add_options(parser)
        (self.options, self.args) = parser.parse_args()

        if self.options.trace_rpc:
            import logging
            logging.basicConfig(level=logging.DEBUG)

        os.environ['PATH'] = self.options.zendir+":"+os.environ['PATH']

        check_json_precision()

        success = False
        try:
            if not os.path.isdir(self.options.tmpdir):
                os.makedirs(self.options.tmpdir)

            print("Initializing test directory "+self.options.tmpdir)

            self.setup_chain()

            self.setup_network()

            self.sc_setup_chain()

            self.sc_setup_network()

            self.run_test()

            success = True

        except JSONRPCException as e:
            print("JSONRPC error: "+e.error['message'])
            traceback.print_tb(sys.exc_info()[2])
        except SCAPIException as e: #New exception for SC API
            print("SCAPI error: "+e.error)
            traceback.print_tb(sys.exc_info()[2])
        except TimeoutException as e:
            print("Timeout while: " + e.operation) #Timeout for SC Operations
            traceback.print_tb(sys.exc_info()[2])
        except AssertionError as msg:
            print("Assertion failed: " + str(msg))
            traceback.print_tb(sys.exc_info()[2])
        except Exception as e:
            print("Unexpected exception caught during testing: "+str(e))
            traceback.print_tb(sys.exc_info()[2])

        if not self.options.noshutdown: #Support for tests with MC only, SC only, MC/SC
            if hasattr(self,"sc_nodes"):
                print("Stopping SC nodes")
                stop_sc_nodes(self.sc_nodes)
            if hasattr(self, "nodes"):
                print("Stopping MC nodes")
                stop_nodes(self.nodes)
                wait_bitcoinds()
        else:
            print("Note: client processes were not stopped and may still be running")

        if not self.options.nocleanup and not self.options.noshutdown:
            print("Cleaning up")
            shutil.rmtree(self.options.tmpdir)

        if success:
            print("Test successful")
            sys.exit(0)
        else:
            print("Failed")
            sys.exit(1)

'''Support for running MC & SC Nodes with different binaries. 
For MC the implementation follows the one of BTF, for SC it is possible to specify multiple jars'''
class SidechainComparisonTestFramework(SidechainTestFramework):

    def add_options(self, parser):
        parser.add_option("--testbinary", dest="testbinary",
                          default=os.getenv("BITCOIND", "zend"),
                          help="zend binary to test")
        parser.add_option("--refbinary", dest="refbinary",
                          default=os.getenv("BITCOIND", "zend"),
                          help="zend binary to use for reference nodes (if any)")

    def setup_chain(self):
        self.num_nodes = 2
        initialize_chain_clean(self.options.tmpdir, self.num_nodes)

    def setup_network(self):
        self.nodes = start_nodes(self.num_nodes, self.options.tmpdir,
                                    extra_args=[['-debug', '-whitelist=127.0.0.1']] * self.num_nodes,
                                    binary=[self.options.testbinary] +
                                           [self.options.refbinary]*(self.num_nodes-1))

    def sc_add_options(self, parser):
        parser.add_option("--jarspathlist", dest="jarspathlist", type = "string",
                          action = "callback", callback = self._get_args,
                          default=["resources/twinsChain.jar examples.hybrid.HybridApp", "resources/twinsChainOld.jar examples.hybrid.HybridApp"],
                          help="node jars to test in the format: \"<jar1>,<jar2>,...\"")

    def _get_args(self, option, opt, value, parser):
        setattr(parser.values, option.dest, str.split(','))

    def sc_setup_chain(self):
        self.num_sc_nodes = len(self.options.jarspathlist)
        initialize_default_sc_chain_clean(self.options.tmpdir, self.num_sc_nodes)

    def sc_setup_network(self):
        self.sc_nodes = start_sc_nodes(self.num_sc_nodes, self.options.tmpdir, binary = self.options.jarspathlist)
