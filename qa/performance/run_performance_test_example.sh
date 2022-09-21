#!/bin/bash

### INSTRUCTIONS ###
# BLOCKRATE is argument one and the script would run using the command below which will run a  test in sequence for each
# array value.  Run script using a command similar to the one below from the qa/performance directory.
# bash run_performance_test.sh "5 10 20 30 60"

# !! Remember each test modifies the same json file, so values will need resetting if necessary.

chmod +x ../sc_perf_evm_test.py
# Update these exports before running
export BITCOINCLI="/usr/bin/zen-cli"
export BITCOIND="/usr/bin/zend"
export SIDECHAIN_SDK="/home/user/Documents/projects/Sidechains-SDK"

BLOCKRATE=($1)

for rate in ${BLOCKRATE[@]}; do
    jq --argjson rate $rate '.block_rate = $rate' sc_perf_evm_test.json
    # Modify this line if python version is set differently on local machine
    (cd .. && python3.9 sc_perf_evm_test.py)
done

