#!/bin/bash
set -e -o pipefail

#Log stdout to file, use '| tee /dev/fd/3' to also log to console
#Must match logfile location referenced in sc_test_framework
exec 3>&1 1>>"${BASH_SOURCE%/*}/sc_test.log" 2>&1

#Check Environment Variables have been set
if [[ -z "$BITCOINCLI" ]]; then
    echo "Environment Variable: BITCOINCLI not set" | tee /dev/fd/3
    exit 1
fi
if [[ -z "$BITCOIND" ]]; then
    echo "Environment Variable: BITCOIND not set" | tee /dev/fd/3
    exit 1
fi
if [[ -z "$SIDECHAIN_SDK" ]]; then
    echo "Environment Variable: SIDECHAIN_SDK not set" | tee /dev/fd/3
    exit 1
fi

# parse args
for i in "$@"; do
  case "${i}" in
    -extended)
      EXTENDED="true"
      shift
      ;;
    -exclude=*)
      EXCLUDE="${i#*=}"
      shift
      ;;
    -evm_only)
      EVM_ONLY="true"
      shift
      ;;
    -utxo_only)
      UTXO_ONLY="true"
      shift
      ;;
    -split=*)
      SPLIT="${i#*=}"
      shift
      ;;
    *)
      # unknown option/passOn
      passOn+="${i} "
      ;;
  esac
done

#Run the tests
testScripts=();

testScriptsEvm=(
    'sc_evm_bootstrap.py'
    'sc_evm_eoa2eoa.py'
    'sc_evm_forward_transfer.py'
    'sc_evm_backward_transfer.py'
    'sc_evm_backward_transfer.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_evm_backward_transfer.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_evm_backward_transfer_2.py'
    'sc_evm_backward_transfer_2.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_evm_backward_transfer_2.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_evm_base_fee.py'
    'sc_evm_bwt_corner_cases.py'
    'sc_evm_bwt_corner_cases.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_evm_bwt_corner_cases.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_evm_cert_key_rotation.py'
    'sc_evm_cert_key_rotation.py --nonceasing'
    'sc_evm_cert_key_rotation_across_epoch.py'
    'sc_evm_cert_key_rotation_across_epoch.py --nonceasing'
    'sc_evm_cert_key_rotation_old_circuit.py'
    'sc_evm_multiple_pending_certs_non_ceasing.py'
    'sc_evm_contract_deployment_create2.py'
    'sc_evm_forger.py'
    'sc_evm_forger_delegation.py'
    'sc_evm_closed_forger.py'
    'sc_evm_forging_fee_payments.py'
    'sc_evm_fee_payments_rpc.py'
    'sc_evm_mempool.py'
    'sc_evm_mempool_invalid_txs.py'
    'sc_evm_orphan_txs.py'
    'sc_evm_rpc_invalid_txs.py'
    'sc_evm_test_debug_methods.py'
    'sc_evm_estimateGas.py'
    'sc_evm_test_block_bloom_filter.py'
    'sc_evm_test_storage_contract.py'
    'sc_evm_test_erc20.py'
    'sc_evm_test_erc721.py'
    'sc_evm_test_contract_contract_deployment_and_interaction.py'
    'sc_evm_test_metamask_related.py'
    'sc_evm_test_prevrandao.py'
    'sc_evm_storage_recovery.py'
    'sc_evm_raw_tx_http_api.py'
    'sc_evm_import_export_keys.py'
    'sc_evm_delegatecall_contract.py'
    'sc_evm_mc_fork.py'
    'sc_evm_context_blockhash.py'
);

testScriptsUtxo=(
    'mc_sc_connected_nodes.py'
    'mc_sc_forging1.py'
    'mc_sc_forging2.py'
    'mc_sc_forging3.py'
    'mc_sc_forging4.py'
    'mc_sc_forging5.py'
    'mc_sc_forging_delegation.py'
    'mc_sc_forging_fee_payments.py'
    'mc_sc_nodes_alive.py'
    'sc_backward_transfer.py'
    'sc_backward_transfer.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_backward_transfer.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_blockid_for_backup.py'
    'sc_bootstrap.py'
    'sc_bt_limit.py'
    'sc_bt_limit.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_bt_limit.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_bt_limit_across_fork.py'
    'sc_bt_limit_across_fork.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_bt_limit_across_fork.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_bwt_minimum_value.py'
    'sc_bwt_minimum_value.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_bwt_minimum_value.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_ceased.py'
    'sc_cert_fee_conf.py'
    'sc_cert_no_coin_record.py'
    'sc_cert_submission_decentralization.py'
    'sc_cert_submission_decentralization.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_cert_submission_decentralization.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_cert_submitter_after_sync_1.py'
    'sc_cert_submitter_after_sync_1.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_cert_submitter_after_sync_1.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_cert_submitter_after_sync_2.py'
    'sc_cert_submitter_after_sync_2.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_cert_submitter_after_sync_2.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation --nonceasing'
    'sc_cert_key_rotation_old_circuit.py'
    'sc_cert_key_rotation.py'
    'sc_cert_key_rotation.py --nonceasing'
    'sc_cert_key_rotation_across_epoch.py'
    'sc_cert_key_rotation_across_epoch.py --nonceasing'
    'sc_cert_submitter_secure_enclave.py'
    'sc_closed_forger.py'
    'sc_csw_ceased_at_epoch_1.py'
    'sc_csw_ceased_at_epoch_1_with_large_epoch_length.py'
    'sc_csw_ceased_at_epoch_2.py'
    'sc_csw_ceased_at_epoch_3.py'
    'sc_csw_disabled.py'
    'sc_csw_in_fee_payment.py'
    'sc_cum_comm_tree_hash.py'
    'sc_forger_feerate.py'
    'sc_forward_transfer.py'
    'sc_genesisinfo_sc_versions.py'
    'sc_import_export_keys.py'
    'sc_mempool_max_fee.py'
    'sc_mempool_max_size.py'
    'sc_mempool_min_fee_rate.py'
    'sc_multiple_certs.py'
    'sc_multiple_certs.py --certcircuittype=NaiveThresholdSignatureCircuitWithKeyRotation'
    'sc_multiple_pending_certs_non_ceasing.py'
    'sc_node_api_test.py'
    'sc_node_response_along_sync.py'
    'sc_nodes_initialize.py'
    'sc_storage_recovery_with_csw.py'
    'sc_storage_recovery_without_csw.py'
    'sc_versions_and_mc_certs.py'
    'sc_withdrawal_epoch_last_block.py'
    'websocket_server.py'
    'websocket_server_fee_payments.py'
    'sc_sync_after_fork.py'
    'sc_dust_threshold_fork.py'
    'sc_ft_limit_fork.py'
    'sc_fork_one_forced_tx.py'
    'sc_big_block.py'
);

testScriptsNetworking=(
    'net_declared_address.py'
    'net_first_known_peers.py'
    'net_incoming_connections.py'
    'net_peers_storage_persistence.py'
    'net_ring_of_nodes.py'
    'net_skip_down_known_peer.py'
)

# decide whether to have only evm tests or only utxo tests or the whole set
if [ ! -z "$EVM_ONLY" ] && [ ! -z "$UTXO_ONLY" ]; then
    echo -e "\nCan not have both options '-evm_only' and '-utxo_only'" | tee /dev/fd/3
    exit 1
fi

if [ ! -z "$EVM_ONLY" ] && [ "${EVM_ONLY}" = "true" ]; then
  testScripts+=( "${testScriptsEvm[@]}" )
elif [ ! -z "$UTXO_ONLY" ] && [ "${UTXO_ONLY}" = "true" ]; then
  testScripts+=( "${testScriptsUtxo[@]}" )
else
  testScripts+=( "${testScriptsEvm[@]}" )
  testScripts+=( "${testScriptsUtxo[@]}")
  testScripts+=( "${testScriptsNetworking[@]}" )
fi

# include extended tests (not used as of now)
if [ ! -z "$EXTENDED" ] && [ "${EXTENDED}" = "true" ]; then
  testScripts+=( "${testScriptsExt[@]}" )
fi

# remove tests provided by --exclude= from testScripts
if [ ! -z "$EXCLUDE" ]; then
  for target in ${EXCLUDE//,/ }; do
    for i in "${!testScripts[@]}"; do
      if [ "${testScripts[i]}" = "$target" ] || [ "${testScripts[i]}" = "$target.py" ]; then
        unset "testScripts[i]"
      fi
    done
  done
fi

# split array into m parts and only run tests of part n where SPLIT=m:n
if [ ! -z "$SPLIT" ]; then
  chunks="${SPLIT%*:*}"
  chunk="${SPLIT#*:}"
  chunkSize="$((${#testScripts[@]}/${chunks}))"
  start=0
  for (( i = 1; i <= $chunks; i++ )); do
    if [ $i -eq $chunk ]; then
      break
    fi
    start=$(($start+$chunkSize))
  done
  if [ $chunks -eq $chunk ]; then
    testScripts=( "${testScripts[@]:$start}" )
  else
    testScripts=( "${testScripts[@]:$start:$chunkSize}" )
  fi
fi

successCount=0
notFoundCount=0
failureCount=0
declare -a failures

function checkFileExists
{
    # take only file name, strip off any options/param
    local TestScriptFile="$(echo $1 | cut -d' ' -f1)"
    # get filepath relative to the source bash script (e.g. this file)
    local TestScriptFileAndPath="${BASH_SOURCE%/*}/${TestScriptFile}"

    if [ ! -f "${TestScriptFileAndPath}" ]; then
      echo -e "\nWARNING: file not found [ ${TestScriptFileAndPath} ]" | tee /dev/fd/3
      failures[${#failures[@]}]="(#-NotFound-$1-#)"
      notFoundCount=$((notFoundCount + 1))
      return 1;
    else
      return 0
    fi
}

function runTestScript
{
    local testName="$1"
    #Remove from $1 array
    shift

    echo -e "=== Running testscript ${testName} ===" | tee /dev/fd/3

    if eval "$@"; then
    successCount=$(expr $successCount + 1)
    echo "--- Success: ${testName} ---" | tee /dev/fd/3
    else
    failures[${#failures[@]}]="$testName"
    failureCount=$((failureCount + 1))
    echo "!!! FAIL: ${testName} !!!" | tee /dev/fd/3
    fi

    echo | tee /dev/fd/3
}

for (( i = 0; i < ${#testScripts[@]}; i++ )); do
  if checkFileExists "${testScripts[$i]}"; then
        if [ -z "$1" ] || [ "${1:0:1}" = "-" ] || [ "$1" = "${testScripts[$i]}" ] || [ "$1.py" = "${testScripts[$i]}" ]; then
        echo "Running $((i +1)) Of ${#testScripts[@]} Tests" | tee /dev/fd/3
        runTestScript \
              "${testScripts[$i]}" \
              "${BASH_SOURCE%/*}/${testScripts[$i]}"
        fi
  fi
done

total=$((successCount + failureCount))
echo -e "\n\nTests Run: $total" | tee /dev/fd/3
echo "Passed: $successCount; Failed: $failureCount; Not Found: $notFoundCount" | tee /dev/fd/3

if [ $total -eq 0 ]; then
  echo -e "\nCould not exec any test: File name [$1]" | tee /dev/fd/3
  checkFileExists $1
  exit 1
fi

if [ ${#failures[@]} -gt 0 ]; then
    echo -e "\nFailing tests: ${failures[*]}" | tee /dev/fd/3
    exit 1
  else
    exit 0
fi

