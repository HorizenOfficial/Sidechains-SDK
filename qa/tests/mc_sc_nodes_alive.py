#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainTestFramework
from test_framework.util import assert_true
import time

"""
    Setup a single MC node and a single SC Node and verify that 
    they are fully on by checking that they sends a valid response to a RPC/API call
"""

class MCSCNodesAlive(SidechainTestFramework):
    def run_test(self):
        i = 0
        for node in self.nodes:
            res = node.getinfo()
            assert_true(res is not None, "MC node {0} not alive !".format(i))
            print("MC node {0} alive !\nResponse to getinfo RPC call: {1} ".format(i, res))
            i = i + 1
        i = 0
        for sc_node in self.sc_nodes:
            res = sc_node.node_connectedPeers()
            assert_true(res is not None, "SC node {0} not alive !".format(i))
            print("SC node {0} alive !\nResponse to API call with input : {1}".format(i, res))
            i = i + 1
            
if __name__ == "__main__":
    MCSCNodesAlive().main()
    