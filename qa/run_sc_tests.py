#!/usr/bin/env python3
import sys

from mc_sc_forging5 import MCSCForging5
from mc_sc_forging_delegation import MCSCForgingDelegation
from sc_ceased import SCCeased
from sc_cert_no_coin_record import SCCertNoCoinRecord
from sc_cert_submission_decentralization import SCCertSubmissionDecentralization
from sc_cert_submitter_after_sync_1 import ScCertSubmitterAfterSync1
from sc_cert_submitter_after_sync_2 import ScCertSubmitterAfterSync2
from sc_csw_ceased_at_epoch_1 import SCCswCeasedAtEpoch1
from sc_csw_ceased_at_epoch_1_with_large_epoch_length import SCCswCeasedAtEpoch1WithLargeEpochLength
from sc_csw_ceased_at_epoch_2 import SCCswCeasedAtEpoch2
from sc_csw_ceased_at_epoch_3 import SCCswCeasedAtEpoch3
from sc_cum_comm_tree_hash import SCCumCommTreeHash
from sc_genesisinfo_sc_versions import SCGenesisInfoScVersions
from sc_multiple_certs import SCMultipleCerts
from sc_nodes_initialize import SidechainNodesInitializationTest
from sc_versions_and_mc_certs import SCVersionsAndMCCertificates
from sc_withdrawal_epoch_last_block import SCWithdrawalEpochLastBlock
from test_framework.util import assert_equal
from mc_sc_connected_nodes import MCSCConnectedNodes
from mc_sc_forging1 import MCSCForging1
from mc_sc_forging2 import MCSCForging2
from mc_sc_forging3 import MCSCForging3
from mc_sc_forging4 import MCSCForging4
from mc_sc_nodes_alive import MCSCNodesAlive
from sc_backward_transfer import SCBackwardTransfer
from sc_bootstrap import SCBootstrap
from sc_forward_transfer import SCForwardTransfer
from websocket_server import SCWsServer
from mc_sc_forging_fee_payments import MCSCForgingFeePayments
from sc_cert_fee_conf import CertFeeConfiguration
from sc_bwt_minimum_value import SCBwtMinValue
from sc_storage_recovery_with_csw import StorageRecoveryWithCSWTest
from sc_storage_recovery_without_csw import StorageRecoveryWithoutCSWTest
from websocket_server_fee_payments import SCWsServerFeePayments
from sc_closed_forger import SidechainClosedForgerTest
from sc_node_response_along_sync import SCNodeResponseAlongSync
from sc_blockid_for_backup import SidechainBlockIdForBackupTest
from sc_node_api_test import SidechainNodeApiTest
from sc_import_export_keys import SidechainImportExportKeysTest
from sc_forger_feerate import SCForgerFeerate
from sc_mempool_max_fee import SCMempoolMaxFee
from sc_csw_disabled import SCCswDisabled
from sc_mempool_max_size import  SCMempoolMaxSize
from sc_mempool_min_fee_rate import SCMempoolMinFeeRate
from sc_csw_in_fee_payment import ScCSWInFeePaymentTest
from sc_bt_limit import ScBtLimitTest
from sc_bt_limit_across_fork import ScBtLimitAcrossForkTest
from sc_wallet_reindex import SidechainWalletReindexTest
from sc_wallet_reindex_along_sync import SidechainWalletReindexSyncTest
from sc_wallet_reindex_feepayments import SidechainWalletReindexFeePayments
from sc_storage_recovery_with_wallet_reindex import StorageRecoveryWithReindexTest

def run_test(test):
    try:
        print("----> Running test - " + str(test.__class__))
        test.main()
    except SystemExit as e:
        return e.code
    return 0

def run_tests(log_file):
    sys.stdout = log_file

    result = run_test(MCSCNodesAlive())
    assert_equal(0, result, "mc_sc_nodes_alive test failed!")

    result = run_test(SCBootstrap())
    assert_equal(0, result, "sc_bootstrap test failed!")

    result = run_test(SidechainNodesInitializationTest())
    assert_equal(0, result, "sc_nodes_initialize test failed!")

    result = run_test(MCSCConnectedNodes())
    assert_equal(0, result, "mc_node_alive test failed!")

    result = run_test(MCSCForging1())
    assert_equal(0, result, "mc_sc_forging1 test failed!")

    result = run_test(MCSCForging2())
    assert_equal(0, result, "mc_sc_forging2 test failed!")

    result = run_test(MCSCForging3())
    assert_equal(0, result, "mc_sc_forging3 test failed!")

    result = run_test(MCSCForging4())
    assert_equal(0, result, "mc_sc_forging4 test failed!")

    result = run_test(MCSCForging5())
    assert_equal(0, result, "mc_sc_forging5 test failed!")

    result = run_test(MCSCForgingDelegation())
    assert_equal(0, result, "mc_sc_forging_delegation test failed!")

    result = run_test(MCSCForgingFeePayments())
    assert_equal(0, result, "mc_sc_forging_fee_payments test failed!")

    result = run_test(SCForwardTransfer())
    assert_equal(0, result, "sc_forward_transfer test failed!")

    result = run_test(SCWithdrawalEpochLastBlock())
    assert_equal(0, result, "sc_withdrawal_epoch_last_block test failed!")

    result = run_test(SCCumCommTreeHash())
    assert_equal(0, result, "sc_cum_comm_tree_hash test failed!")

    result = run_test(SCWsServer())
    assert_equal(0, result, "websocket_server test failed!")

    result = run_test(SCWsServerFeePayments())
    assert_equal(0, result, "websocket_server_fee_payments test failed!")

    result = run_test(SCBackwardTransfer())
    assert_equal(0, result, "sc_backward_transfer test failed!")

    result = run_test(SCMultipleCerts())
    assert_equal(0, result, "sc_multiple_certs test failed!")

    result = run_test(SCCeased())
    assert_equal(0, result, "sc_ceased test failed!")

    result = run_test(SCCertSubmissionDecentralization())
    assert_equal(0, result, "sc_cert_submission_decentralization test failed!")

    result = run_test(ScCertSubmitterAfterSync1())
    assert_equal(0, result, "sc_cert_submitter_after_sync test failed!")

    result = run_test(ScCertSubmitterAfterSync2())
    assert_equal(0, result, "sc_cert_submitter_after_sync2 test failed!")

    result = run_test(SCCertNoCoinRecord())
    assert_equal(0, result, "sc_cert_no_coin_record test failed!")

    result = run_test(CertFeeConfiguration())
    assert_equal(0, result, "sc_cert_fee_conf test failed!")

    result = run_test(SCBwtMinValue())
    assert_equal(0, result, "sc_bwt_minimum_value test failed!")

    result = run_test(SCCswCeasedAtEpoch1())
    assert_equal(0, result, "sc_csw_ceased_at_epoch_1 test failed!")

    result = run_test(SCCswCeasedAtEpoch1WithLargeEpochLength())
    assert_equal(0, result, "sc_csw_ceased_at_epoch_1_with_large_epoch_length test failed!")

    result = run_test(SCCswCeasedAtEpoch2())
    assert_equal(0, result, "sc_csw_ceased_at_epoch_2 test failed!")

    result = run_test(SCCswCeasedAtEpoch3())
    assert_equal(0, result, "sc_csw_ceased_at_epoch_3 test failed!")

    result = run_test(SCGenesisInfoScVersions())
    assert_equal(0, result, "sc_genesisinfo_sc_versions test failed!")

    result = run_test(SCVersionsAndMCCertificates())
    assert_equal(0, result, "sc_versions_and_mc_certs test failed!")

    result = run_test(SidechainClosedForgerTest())
    assert_equal(0, result, "sc_closed_forger test failed!")

    result = run_test(SCNodeResponseAlongSync())
    assert_equal(0, result, "sc_node_response_along_sync test failed!")

    result = run_test(StorageRecoveryWithCSWTest())
    assert_equal(0, result, "Storage recovery with CSW enabled test failed!")

    result = run_test(StorageRecoveryWithoutCSWTest())
    assert_equal(0, result, "DStorage recovery with CSW disabled test failed!")

    result = run_test(StorageRecoveryWithReindexTest())
    assert_equal(0, result, "DStorage recovery with wallet reindex test failed!")

    result = run_test(SidechainBlockIdForBackupTest())
    assert_equal(0, result, "sc_blockid_for_backup test failed!")

    result = run_test(SidechainNodeApiTest())
    assert_equal(0, result, "sc_node_api_test test failed!")

    result = run_test(SidechainImportExportKeysTest())
    assert_equal(0, result, "sc_import_export_keys test failed!")

    result = run_test(SCForgerFeerate())
    assert_equal(0, result, "sc_forger_feerate test failed!")

    result = run_test(SCMempoolMaxFee())
    assert_equal(0, result, "sc_mempool_max_fee test failed!")

    result = run_test(SCCswDisabled())
    assert_equal(0, result, "sc_csw_disabled test failed!")

    result = run_test(SCMempoolMaxSize())
    assert_equal(0, result, "sc_mempool_max_size test failed!")

    result = run_test(SCMempoolMinFeeRate())
    assert_equal(0, result, "sc_mempool_min_fee_rate test failed!")

    result = run_test(ScCSWInFeePaymentTest())
    assert_equal(0, result, "sc_csw_in_fee_payment test failed!")

    result = run_test(ScBtLimitTest())
    assert_equal(0, result, "sc_bt_limit test failed!")

    result = run_test(ScBtLimitAcrossForkTest())
    assert_equal(0, result, "sc_bt_limit_across_fork test failed!")

    result = run_test(SidechainWalletReindexTest())
    assert_equal(0, result, "sc_wallet_reindex test failed!")

    result = run_test(SidechainWalletReindexSyncTest())
    assert_equal(0, result, "sc_wallet_reindex_along_sync test failed!")

    result = run_test(SidechainWalletReindexFeePayments())
    assert_equal(0, result, "sc_wallet_reindex_feepayments test failed!")




if __name__ == "__main__":
    my_log_file = open("sc_test.log", "w")
    run_tests(my_log_file)
