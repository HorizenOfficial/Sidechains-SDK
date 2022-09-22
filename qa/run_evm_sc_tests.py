#!/usr/bin/env python3
import sys

from test_framework.util import assert_equal

from sc_evm_bootstrap import SCEvmBootstrap
from sc_evm_test_storage_contract import SCEvmStorageContract
from sc_evm_bwt_corner_cases import SCEvmBWTCornerCases
from sc_evm_forward_transfer import SCEvmForwardTransfer
from sc_evm_test_erc20 import SCEvmERC20Contract
from sc_evm_test_contract_contract_deployment_and_interaction import SCEvmDeployingContract
from sc_evm_eoa2eoa import SCEvmEOA2EOA
from sc_evm_test_erc721 import SCEvmERC721Contract
from sc_evm_backward_transfer import SCEvmBackwardTransfer
from sc_evm_forger import SCEvmForger
from sc_evm_closed_forger import SCEvmClosedForgerList
from sc_evm_orphan_txs import SCEvmOrphanTXS
from sc_evm_mempool import SCEvmMempool


"""
Runs all tests related to EVM Sidechain,
"""
def run_test(test):
    try:
        print("----> Running test - " + str(test.__class__))
        test.main()
    except SystemExit as e:
        return e.code
    return 0

def run_tests(log_file):
    sys.stdout = log_file

    result = run_test(SCEvmBootstrap())
    assert_equal(0, result, "sc_evm_bootstrap test failed!")

    result = run_test(SCEvmForwardTransfer())
    assert_equal(0, result, "sc_evm_forward_transfer test failed!")

    result = run_test(SCEvmForger())
    assert_equal(0, result, "sc_evm_forger test failed!")

    result = run_test(SCEvmClosedForgerList())
    assert_equal(0, result, "sc_evm_closed_forger test failed!")

    result = run_test(SCEvmBackwardTransfer())
    assert_equal(0, result, "sc_evm_backward_transfer test failed!")

    result = run_test(SCEvmBWTCornerCases())
    assert_equal(0, result, "sc_evm_bwt_corner_cases test failed!")

    result = run_test(SCEvmDeployingContract())
    assert_equal(0, result, "sc_evm_test_contract_contract_deployment_and_interaction test failed!")

    result = run_test(SCEvmERC20Contract())
    assert_equal(0, result, "sc_evm_test_erc20 test failed!")

    result = run_test(SCEvmEOA2EOA())
    assert_equal(0, result, "sc_evm_eoa2eoa test failed!")

    result = run_test(SCEvmERC721Contract())
    assert_equal(0, result, "sc_evm_test_erc721 test failed!")

    result = run_test(SCEvmStorageContract())
    assert_equal(0, result, "sc_evm_test_storage_contract test failed!")

    result = run_test(SCEvmOrphanTXS())
    assert_equal(0, result, "sc_evm_orphan_txs test failed!")

    result = run_test(SCEvmMempool())
    assert_equal(0, result, "sc_evm_mempool test failed!")

if __name__ == "__main__":
    log_file = open("sc_evm_test.log", "w")
    run_tests(log_file)
