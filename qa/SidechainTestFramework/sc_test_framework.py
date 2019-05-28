from test_framework import BitcoinTestFramework
from authproxy import JSONRPCException
from util import assert_true, check_json_precision, \
    initialize_chain, initialize_chain_clean, \
    start_nodes, stop_nodes, \
    sync_blocks, sync_mempools, wait_bitcoinds
from SidechainTestFramework.scutil import initialize_sc_chain, initialize_sc_chain_clean, \
    start_sc_nodes, stop_sc_nodes, \
    sync_sc_blocks, sync_sc_mempools, wait_sidechainclients
import tempfile
import os
import json
import traceback
import sys
import shutil

class SidechainTestFramework(BitcoinTestFramework):
    
    def add_options(self, parser):
        pass
    
    def setup_chain(self):
        print("Initializing test directory "+self.options.tmpdir)
        initialize_chain_clean(self.options.tmpdir, 1)
        
    def setup_network(self, split = False):
        self.nodes = self.setup_nodes() 
    
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
        initialize_sc_chain_clean(self.options.tmpdir, 1)
    
    def sc_setup_network(self, split = False):
        self.sc_nodes = self.sc_setup_nodes()
    
    def sc_setup_nodes(self):
        return start_sc_nodes(1, self.options.tmpdir)
        
    def sc_split_network(self):
        pass

    def sc_sync_all(self):
        sc_sync_blocks(self.sc_nodes)
        sc_sync_mempools(self.sc_nodes)

    def sc_join_network(self):
        pass
        
    def run_test(self):
        i = 0
        '''Dummy (POST) call to check if SC node is alive. Note that Scorex API requires to specify a path to call a method. 
        It's not possible to do as MC node because slashes in a method name would result in a syntax error. So a workaround has been done.
        Hopefully, this will be removed in our SC SDK.'''
        for sc_node in self.sc_nodes:
            sc_node.__service_name = "/utils/hash/blake2b"
            assert_true(sc_node(json.dumps({"message":"Prova"})) is not None, "SC node {0} not alive !".format(i))
            i = i + 1
        i = 0
        for node in self.nodes:
            assert_true(node.getinfo() is not None, "MC node {0} not alive !".format(i))
            print("MC node {0} alive !".format(i))
            i = i + 1


        
    def main(self):
        import optparse

        parser = optparse.OptionParser(usage="%prog [options]")
        parser.add_option("--nocleanup", dest="nocleanup", default=False, action="store_true",
                          help="Leave bitcoinds and test.* datadir on exit or error")
        parser.add_option("--noshutdown", dest="noshutdown", default=False, action="store_true",
                          help="Don't stop bitcoinds after the test execution")
        parser.add_option("--zendir", dest="zendir", default="ZenCore/src",
                          help="Source directory containing zend/zen-cli (default: %default)")
        parser.add_option("--tmpdir", dest="tmpdir", default=tempfile.mkdtemp(prefix="test"),
                          help="Root directory for datadirs")
        parser.add_option("--tracerpc", dest="trace_rpc", default=False, action="store_true",
                          help="Print out all RPC calls as they are made")
        #Must add option for parsing SC src
        self.add_options(parser)
        self.sc_add_options(parser)
        (self.options, self.args) = parser.parse_args()

        if self.options.trace_rpc:
            import logging
            logging.basicConfig(level=logging.DEBUG)

        os.environ['PATH'] = self.options.zendir+":"+os.environ['PATH']
        #should we update path variable too for SC ?
        
        check_json_precision()

        success = False
        try:
            if not os.path.isdir(self.options.tmpdir):
                os.makedirs(self.options.tmpdir)
            self.setup_chain()

            self.setup_network()
            
            self.sc_setup_chain()
            
            self.sc_setup_network()

            self.run_test()

            success = True

        except JSONRPCException as e:
            print("JSONRPC error: "+e.error['message'])
            traceback.print_tb(sys.exc_info()[2])
        except AssertionError as e:
            print("Assertion failed: "+e.message)
            traceback.print_tb(sys.exc_info()[2])
        except Exception as e:
            print("Unexpected exception caught during testing: "+str(e))
            traceback.print_tb(sys.exc_info()[2])
        
        #add ifs for SC too
        if not self.options.noshutdown:
            print("Stopping nodes")
            stop_nodes(self.nodes)
            wait_bitcoinds()
        else:
            print("Note: bitcoinds were not stopped and may still be running")

        if not self.options.nocleanup and not self.options.noshutdown:
            print("Cleaning up")
            shutil.rmtree(self.options.tmpdir)

        if success:
            print("Tests successful")
            sys.exit(0)
        else:
            print("Failed")
            sys.exit(1)