#!/usr/bin/env python2
from netrc import netrc

from SidechainTestFramework.sc_boostrap_info import SCNodeConfiguration, SCNetworkConfiguration
from test_framework.test_framework import BitcoinTestFramework
from test_framework.authproxy import JSONRPCException
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from test_framework.util import check_json_precision, \
    initialize_chain_clean, \
    start_nodes, stop_nodes, get_genesis_info, \
    sync_blocks, sync_mempools, wait_bitcoinds
from SidechainTestFramework.scutil import initialize_default_sc_chain_clean, \
    start_sc_nodes, stop_sc_nodes, \
    sync_sc_blocks, sync_sc_mempools, TimeoutException, \
    generate_secrets, initialize_sc_datadir
import tempfile
import os
import json
import traceback
import sys
import shutil
import sc_boostrap_info

'''
If you want to keep default behavior:
For MC Test Only: Override sc_setup_chain, sc_setup_network, sc_add_options
For SC Test Only: Override setup_chain, setup_network, add_options and sc_generate_genesis_data
For MC&SC Tests: Don't override anything
'''

#Default config, for the moment, just setup 1 MC node and 1 SC node
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

    def join_network(self):
        pass

    def sc_add_options(self, parser):
        pass

    def sc_setup_chain(self):
        initialize_default_sc_chain_clean(self.options.tmpdir, 1)

    """
    Bootstrap a network of sidechain nodes.
    
    Parameters:
     - network: an instance of SCNetworkConfiguration (see sc_boostrap_info.py)
                    
    Example: 2 mainchain nodes and 3 sidechain nodes (with default websocket configuration) bootstrapped, respectively, from mainchain node first, first, and third.
    The JSON representation is only for documentation.
    {
        network: [
            sidechain_1_configuration: {
                "sc_creation_info":{
                "sc_id": "id_1"
                "forward_amout": 200
                "withdrawal_epoch_length": 1000
                },
                "mainchain_node": mc_node_1
                "mc_connection_info":{
                    "address": "ws://localhost:8888"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            },
            sidechain_2_configuration: {
                "sc_creation_info":{
                "sc_id": "id_2"
                "forward_amout": 300
                "withdrawal_epoch_length": 1000
                },
                "mainchain_node": mc_node_1
                "mc_connection_info":{
                    "address": "ws://localhost:8888"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            
            },
            sidechain_3_configuration: {
                "sc_creation_info":{
                "sc_id": "id_3"
                "forward_amout": 450
                "withdrawal_epoch_length": 1000
                },
                "mainchain_node": mc_node_2
                "mc_connection_info":{
                    "address": "ws://localhost:8888"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            
            }
        ]
    }
     
     Output: a map of:
     - key: i, i=[1,...,n] with n=the number of sidechain nodes to be bootstrapped
     - value: bootstrap information if the sidechain node i. An array of:
        - sidechain_id
        - account_secrets
        - total_balance
        - genesis_info
    
    """
    def bootstrap_sidechain_nodes(self, network):
        self.sc_nodes_bootstrap_info = {}
        total_number_of_sidechains = len(network.sc_nodes_configuration)
        for i in range(total_number_of_sidechains):
            sc_node_conf = network.sc_nodes_configuration[i]
            sc_nodes_bootstrap_info_i = self.bootstrap_sidechin_node(i, sc_node_conf)
            self.sc_nodes_bootstrap_info[i] = sc_nodes_bootstrap_info_i
        return self.sc_nodes_bootstrap_info

    """
    Bootstrap one sidechain node.
    
    Parameters:
     - n: sidechain node nth: used to create directory "sc_node_n"
     - sc_node_configuration: an instance of SCNodeConfiguration (see sc_boostrap_info.py)
     
     Output: a map of:
     - bootstrap information of the sidechain node. An array of:
        - sidechain_id
        - account_secrets
        - total_balance
        - genesis_info
    """
    def bootstrap_sidechin_node(self, n, sc_node_configuration):
        account_secrets = generate_secrets(n, 1)
        sc_creation_info = sc_node_configuration.sc_creation_info
        sidechain_id = sc_creation_info.sc_id
        genesis_info = get_genesis_info(sidechain_id,
                                        sc_node_configuration.mc_node,
                                        sc_creation_info.withdrawal_epoch_length,
                                        account_secrets,
                                        [sc_creation_info.forward_amout])
        print "Sidechain created with id: " + sidechain_id
        initialize_sc_datadir(self.options.tmpdir, n, account_secrets, genesis_info[0], sc_node_configuration.mc_connection_info)
        return [sidechain_id, account_secrets, sc_creation_info.forward_amout, genesis_info[1]]

    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
        self.sc_sync_all()

    def sc_setup_nodes(self, number_of_sidechains_nodes = 1):
        return start_sc_nodes(number_of_sidechains_nodes, self.options.tmpdir)

    def sc_split_network(self):
        pass

    def sc_sync_all(self):
        sync_sc_blocks(self.sc_nodes)
        sync_sc_mempools(self.sc_nodes)

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
        parser.add_option("--scjarpath", dest="scjarpath", default="../examples/simpleapp/target/Sidechains-SDK-simpleapp-0.1-SNAPSHOT.jar;../examples/simpleapp/target/lib/* com.horizen.examples.SimpleApp", #New option. Main class path won't be needed in future
                          help="Directory containing .jar file for SC (default: %default)")
        parser.add_option("--tmpdir", dest="tmpdir", default="../examples/simpleapp/target/tmp",
                          help="Root directory for datadirs")
        parser.add_option("--tracerpc", dest="trace_rpc", default=False, action="store_true",
                          help="Print out all RPC calls as they are made")

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
        except AssertionError as e:
            print("Assertion failed: "+e.message)
            traceback.print_tb(sys.exc_info()[2])
        except Exception as e:
            print("Unexpected exception caught during testing: "+str(e))
            traceback.print_tb(sys.exc_info()[2])

        if not self.options.noshutdown: #Support for tests with MC only, SC only, MC/SC
            if hasattr(self, "nodes"):
                print("Stopping MC nodes")
                stop_nodes(self.nodes)
                wait_bitcoinds()
            if hasattr(self,"sc_nodes"):
                print("Stopping SC nodes")
                stop_sc_nodes(self.sc_nodes)
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