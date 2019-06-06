#!/bin/bash
set -e -o pipefail

CURDIR=$(cd $(dirname "$0"); pwd)
# Get BUILDDIR and REAL_BITCOIND
. "${CURDIR}/tests-config.sh"

export BITCOINCLI=${BUILDDIR}/qa/pull-tester/run-bitcoin-cli
export BITCOIND=${REAL_BITCOIND}
export PYTHONPATH=${BUILDDIR}/qa/ZenCore/qa/rpc-tests/test_framework

#Run the tests

testScripts=(
    'mc_node_alive.py'
    'mc_sc_nodes_alive.py'
    'multipleclientstest.py'
    'sc_nodes_connect.py'
    'mc_sc_nodes_generation.py'
    'sc_node_generation.py'
);
testScriptsExt=(

);

#if [ "x$ENABLE_ZMQ" = "x1" ]; then
#  testScripts+=('zmq_test.py')
#fi

#if [ "x$ENABLE_PROTON" = "x1" ]; then
#  testScripts+=('proton_test.py')
#fi

extArg="-extended"
passOn=${@#$extArg}

successCount=0
declare -a failures

function checkFileExists
{
    # take only file name, strip off any options/param
    local TestScriptFile="$(echo $1 | cut -d' ' -f1)"
    local TestScriptFileAndPath=${BUILDDIR}/qa/${TestScriptFile}
        
    if [ ! -f ${TestScriptFileAndPath} ]
    then
        echo -e "\nWARNING: file not found [ ${TestScriptFileAndPath} ]"
        failures[${#failures[@]}]="(#-NotFound-$1-#)"
    fi
}


function runTestScript
{
    local testName="$1"
    shift

    echo -e "=== Running testscript ${testName} ==="

    if eval "$@"
    then
        successCount=$(expr $successCount + 1)
        echo "--- Success: ${testName} ---"
    else
        failures[${#failures[@]}]="$testName"
        echo "!!! FAIL: ${testName} !!!"
    fi

    echo
}

if [ "x${ENABLE_BITCOIND}${ENABLE_UTILS}${ENABLE_WALLET}" = "x111" ]; then
    for (( i = 0; i < ${#testScripts[@]}; i++ ))
    do
        checkFileExists "${testScripts[$i]}"

        if [ -z "$1" ] || [ "${1:0:1}" == "-" ] || [ "$1" == "${testScripts[$i]}" ] || [ "$1.py" == "${testScripts[$i]}" ]
        then
            runTestScript \
                "${testScripts[$i]}" \
                "${BUILDDIR}/qa/${testScripts[$i]}" \
                --zendir "${BUILDDIR}/qa/ZenCore/src" ${passOn}
        fi
    done
    for (( i = 0; i < ${#testScriptsExt[@]}; i++ ))
    do
        checkFileExists "${testScriptsExt[$i]}"

        if [ "$1" == $extArg ] || [ "$1" == "${testScriptsExt[$i]}" ] || [ "$1.py" == "${testScriptsExt[$i]}" ]
        then
            runTestScript \
                "${testScriptsExt[$i]}" \
                "${BUILDDIR}/qa/${testScriptsExt[$i]}" \
                --zendir "${BUILDDIR}/qa/ZenCore/src" ${passOn}
        fi
    done

    total=$(($successCount + ${#failures[@]}))
    echo -e "\n\nTests completed: $total"
    echo "successes $successCount; failures: ${#failures[@]}"

    if [ $total == 0 ]
    then
        echo -e "\nCould not exec any test: File name [$1]"
        checkFileExists $1
        exit 1
    fi

    if [ ${#failures[@]} -gt 0 ]
    then
        echo -e "\nFailing tests: ${failures[*]}"
        exit 1
    else
        exit 0
    fi
else
  echo "No rpc tests to run. Wallet, utils, and bitcoind must all be enabled"
fi
