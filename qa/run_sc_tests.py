#!/usr/bin/env python2
import sys
from test_framework.util import assert_equal
from sc_nodes_initialize import SidechainNodesInitializationTest
from sc_node_generation import SidechainNodeBlockGenerationTest

def run_test(test):
    try:
        print("----> Running test - " + str(test.__class__))
        test.main()
    except SystemExit as e:
        return e.code
    return 0

def run_tests(log_file):
    sys.stdout = log_file
    result = run_test(SidechainNodesInitializationTest())
    assert_equal(0, result, "SidechainInitialization test failed!")
    result = run_test(SidechainNodeBlockGenerationTest())
    assert_equal(0, result, "SidechainNodeBlockGeneration test failed!")

if __name__ == "__main__":
    log_file = open("sc_test.log", "w")
    run_tests(log_file)
