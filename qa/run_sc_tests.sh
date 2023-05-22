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
    -parallel=*)
      PARALLEL="${i#*=}"
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
    'sc_evm_cert_key_rotation_multiple_nodes.py'
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
    'sc_evm_gasPrice.py'
    'sc_evm_mempool.py'
    'sc_evm_mempool_invalid_txs.py'
    'sc_evm_orphan_txs.py'
    'sc_evm_rpc_invalid_blocks.py'
    'sc_evm_rpc_invalid_txs.py'
    'sc_evm_rpc_net_methods.py'
    'sc_evm_rpc_web3_methods.py'
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
    'sc_evm_forbid_unprotected_txs.py'
    'sc_evm_block_size_limit.py'
    'sc_evm_mempool_size.py'
    'sc_evm_mempool_timeout.py'
    'sc_evm_sync_status.py'
    'sc_evm_txpool.py'
    'sc_evm_eip_1898.py'
    'account_websocket_server.py'
    'account_websocket_server_sync.py'
    'account_websocket_server_rpc.py'
    'sc_evm_seedernode.py'
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
    'sc_node_termination_during_sync.py'
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
    'sc_seedernode.py'
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

testCount=${#testScripts[@]}
successCount=0
successCountFile="/tmp/successCounter.txt"
notFoundCount=0
notFoundCountFile="/tmp/notFoundCounter.txt"
failureCount=0
failureCountFile="/tmp/failureCounter.txt"
declare -a failures
failuresFile="/tmp/failuresList.txt"
flock_file="/tmp/flock_file.lock"
testsFile="/tmp/tests.txt"

function deleteTempFiles
{
  rm -f $flock_file $testsFile $successCountFile $notFoundCountFile $failureCountFile $failuresFile
}

function updateCountFile
{
  (
    flock -x 200
    # Check if the counter file exists
    if [ ! -f "$1" ]; then
      # If the counter file does not exist, initialize it with a starting value
      echo "0" > "$1"
    fi

    # Read the current value of the counter
    count=$(cat "$1")

    # Increment the counter
    count=$((count + 1))

    # Write the new value of the counter
    echo "$count" > "$1"
  ) 200>"$flock_file"
}

function updateFailList
{
  if [ "$PARALLEL" ]; then
    (
      flock -x 200

      # Write the passed argument to the file
      echo "$1" >> $failuresFile
    ) 200>$flock_file
  else
    failures[${#failures[@]}]="$1"
  fi
}

function updateNotFoundCount
{
  if [ "$PARALLEL" ]; then
    updateCountFile "$notFoundCountFile"
  else
    notFoundCount=$((notFoundCount + 1))
  fi
}

function updateFailureCount
{
  if [ "$PARALLEL" ]; then
    updateCountFile "$failureCountFile"
  else
    failureCount=$((failureCount + 1))
  fi
}

function updateSuccessCount
{
  if [ "$PARALLEL" ]; then
    updateCountFile "$successCountFile"
  else
    successCount=$(expr $successCount + 1)
  fi
}

function checkFileExists
{
    # take only file name, strip off any options/param
    local TestScriptFile="$(echo $1 | cut -d' ' -f1)"
    # get filepath relative to the source bash script (e.g. this file)
    local TestScriptFileAndPath="${BASH_SOURCE%/*}/${TestScriptFile}"

    if [ ! -f "${TestScriptFileAndPath}" ]; then
      echo -e "\nWARNING: file not found [ ${TestScriptFileAndPath} ]" | tee /dev/fd/3
      updateFailList "(#-NotFound-$1-#)"
      updateNotFoundCount

      return 1;
    else
      return 0
    fi
}

function checkScriptArgsValid
{
  local scriptArg=$1
  local testToRun=$2

  if [ -z "$scriptArg" ] || [ "${scriptArg:0:1}" = "-" ] || [ "$scriptArg" = "$testToRun" ] || [ "$scriptArg.py" = "$testToRun" ]; then
    return 0;
  else
    echo "Unable to run $testToRun, invalid arg passed to shell script" | tee /dev/fd/3
    return 1
  fi
}

function runTestScript
{
    local testName="$1"
    #Remove first arg $1 from args passed to function, shifting the full file location to arg $1.
    shift

    if [ -z "$PARALLEL" ]; then
      echo -e "=== Running testscript ${testName} ===" | tee /dev/fd/3
    fi;

    #Log test start time
    testStart=$(date +%s)
    runTimeMessage="Run Time: $testRuntime"
    if eval "$@"; then
      testEnd=$(date +%s)
      testRuntime=$((testEnd-testStart))
      updateSuccessCount
      echo "--- Success: ${testName} --- ### Run Time: $testRuntime(s) ###" | tee /dev/fd/3
    else
      testEnd=$(date +%s)
      testRuntime=$((testEnd-testStart))
      updateFailList="$testName"
      updateFailureCount
      echo "!!! FAIL: ${testName} !!! ### Run Time: $testRuntime(s) ###" | tee /dev/fd/3
    fi

    echo | tee /dev/fd/3
}

function runTests
{
  # Assign any parameter given to the shell script, then remove from this functions args
  scriptArg=$1; shift
  # Assign remaining args (which should be the test scripts array)
  testsToRun=("$@")
  runningInfoMessage="Of ${#testsToRun[@]} Tests"

  for (( i = 0; i < ${#testsToRun[@]}; i++ )); do
    if checkFileExists "${testsToRun[$i]}"; then
          if checkScriptArgsValid "$scriptArg"  \
                                  "${testsToRun[$i]}"; then
            echo "Running $((i +1)) $runningInfoMessage" | tee /dev/fd/3
            testFileWithArgs="${BASH_SOURCE%/*}/${testsToRun[$i]}"

            runTestScript \
                  "${testsToRun[$i]}" \
                  "$testFileWithArgs"
          fi
    fi
  done
}

function runParallelTests
{
  # Assign any parameter given to the shell script, then remove from this functions args
  scriptArg=$1; shift

  # Assign parallelGroup then remove from this functions args
  parallelGroup=$1; shift

  while true; do
    # Acquire the lock for the array and retrieve the first test and a count of remaining tests
    result=$(flock -w 1 $testsFile -c "head -n 1 $testsFile; sed -i '1d' $testsFile; num_lines=\$(wc -l $testsFile | awk '{print \$1}'); echo -n \"\$num_lines\"")
    test=$(echo "$result" | awk 'NR==1{print}')
    num_lines=$(echo "$result" | awk 'NR==2{print}')

    if [ -z "$test" ] || [ "$test" = " " ] || [ "$test" = "0" ] ; then
      break
    fi

    testNumber=$((${#testScripts[@]}-$num_lines))

    if checkFileExists "$test"; then
          if checkScriptArgsValid "$scriptArg"  \
                                  "$test"; then
            echo "== Running $testNumber Of $testCount Tests: \"$test\"  == Parallel: $parallelGroup" | tee /dev/fd/3
            testFileWithArgs="${BASH_SOURCE%/*}/$test --parallel=$parallelGroup"

            runTestScript \
                  "$test" \
                  "$testFileWithArgs"
          fi
    fi
  done
}

startTime=$(date +%s)

if [ ! -z "$PARALLEL" ]; then
  deleteTempFiles
  printf "%s\n" "${testScripts[@]}" > $testsFile

  for (( i=1; i<=$PARALLEL; i++ )); do
    runParallelTests  \
        "$1"  \
        "$i"  &
  done
  # Wait for all processes to finish
  wait < <(jobs -p)

else
  # Pass main script arg to function first followed by array.

  runTests  \
      "$1"  \
      "${testScripts[@]}"
fi

if [ "$PARALLEL" ]; then
  if [ -f "$successCountFile" ]; then
    successCount=$(cat "$successCountFile")
  fi
  if [ -f "$failureCountFile" ]; then
    failureCount=$(cat "$failureCountFile")
  fi
  if [ -f "$notFoundCountFile" ]; then
    notFoundCount=$(cat "$notFoundCountFile")
  fi
  if [ -f "$failuresFile" ]; then
    # Read the contents of the failures file into an array
    IFS=$'\n' read -r -a contents < $failuresFile

    # Add the contents array to the existing failures array
    failures=("${failures[@]}" "${contents[@]}")
  fi
fi

endTime=$(date +%s)
runtime=$((endTime-startTime))
total=$((successCount + failureCount))
testRunTimeMessage="\n===  TOTAL TEST RUN TIME: ${runtime}(s)  ==="
testsRunMessage="\nTests Run: $total"
summaryMessage="\nPassed: $successCount; Failed: $failureCount; Not Found: $notFoundCount"
fileNotFoundMessage="\nCould not exec any test: File name [$1]"
failingTestsMessage="\nFailing tests: ${failures[*]}"

echo -e "$testsRunMessage" | tee /dev/fd/3
echo -e "$summaryMessage" | tee /dev/fd/3
echo -e "$testRunTimeMessage" | tee /dev/fd/3

deleteTempFiles

if [ $total -eq 0 ]; then
  echo -e "\n!! WARNING: No test files were found. !!" | tee /dev/fd/3
  echo -e "$failingTestsMessage" | tee /dev/fd/3
  exit 1
fi

if [ ${#failures[@]} -gt 0 ]; then
    echo -e "$failingTestsMessage" | tee /dev/fd/3
    exit 1
  else
    exit 0
fi
