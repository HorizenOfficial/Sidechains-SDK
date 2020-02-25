#!/usr/bin/env python2
from SidechainTestFramework.sc_test_framework import SidechainComparisonTestFramework
from SidechainTestFramework.sidechainauthproxy import SCAPIException
from test_framework.util import assert_true, assert_raises

"""
    Show correctness of Comparison Test Framework. Setup different MC & SC nodes with different zends/jars respectively and check that all nodes are alive.
"""
class MultipleClientsTest(SidechainComparisonTestFramework):
    def run_test(self):
        i = 0
        for node in self.nodes:
            res = node.getinfo()
            assert_true(res is not None, "MC node {0} not alive !".format(i))
            print("MC node {0} alive !\nResponse to getinfo RPC call: {1} ".format(i, res))
            i = i + 1
        i = 0
        for sc_node in self.sc_nodes:
            if i == 0:
                res = sc_node.debug_info()
                assert_true(res is not None, "SC node {0} not alive !".format(i))
                print("SC node {0} alive !\nResponse to debuginfo API call: {1}".format(i, res))
            if i == 1:
                '''SC Node 1 has been initialized using a jar in which POST methods are not supported, that's why we expect an exception
                to assess that Node1 has been effectively initialized with this jar and works correctly'''
                assert_raises(SCAPIException, sc_node.debug_info, "SC node {0} not alive !".format(i))
                print("SC node {0} is alive and raised an exception as expected".format(i))
            i = i + 1
            
if __name__ == "__main__":
    MultipleClientsTest().main()