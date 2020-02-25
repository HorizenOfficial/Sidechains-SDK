#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_true

"""
    Setup a single MC node and verify that it's fully on by checking that it sends a valid response to a RPC call
"""

class MCNodeAlive(SidechainTestFramework):
    
    def sc_setup_chain(self):
        #SC chain setup
        pass
    
    def sc_setup_network(self, split = False):
        #SC network setup
        pass
        
    def sc_add_options(self, parser):
        #Additional parser options for SC
        pass
    
    def run_test(self):
        i = 0
        for node in self.nodes:
            assert_true(node.getinfo() is not None, "MC node {0} not alive !".format(i))
            print ("MC node {0} alive !".format(i))
            i = i + 1
            
if __name__ == "__main__":
    MCNodeAlive().main()