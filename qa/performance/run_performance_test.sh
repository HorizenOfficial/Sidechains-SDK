#!/bin/bash

### INSTRUCTIONS ###
# BLOCKRATE is argument one and the script would run using the command below which will run a  test in sequence for each
# array value.  Run script using a command similar to the one below from the qa/performance directory.
# bash run_performance_test.sh "5 10 20 30 60"

# !! Remember each subsequent test modifies the same json file, so values will need resetting if necessary.

chmod +x ../perf_test.py
# Update these exports before running
#export BITCOINCLI="<PATH_TO_ZEN-CLI>"
#export BITCOIND="<PATH_TO_ZEND>"
#export SIDECHAIN_SDK="<PATH_TO_SIDECHAIN_SDK"

#BLOCKRATE=($1)

#for rate in ${BLOCKRATE[@]}; do
    #jq --argjson rate $rate '.block_rate = $rate' perf_test.json
    # Modify this line if python version is set differently on local machine
    #(cd .. && python3.9 perf_test.py)
#done

RANDOM_LATENCY=($1)

rm -f perf_test.json
cp perf_test_example.json perf_test.json

if $RANDOM_LATENCY; then
    nodes_number=$(jq . perf_test.json | jq '.nodes' | grep 'forger' | wc -l)
    for i in $(seq $nodes_number);
    do
        latency=$[ $RANDOM % 2000 + 200 ]
        sed -i "0,/\"modifiers_spec\":[[:space:]]0/{s/\"modifiers_spec\":[[:space:]]0/\"modifiers_spec\": ${latency}/}" perf_test.json
    done
fi

(cd .. && python3 perf_test.py)
