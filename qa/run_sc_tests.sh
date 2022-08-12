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
testScripts=(
    'websocket_server_fee_payments.py'
);

# include extended tests
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

