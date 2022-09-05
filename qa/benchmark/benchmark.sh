#!/bin/bash
BASEDIR=$(pwd);
EVM_NODE_DIR="${BASEDIR}/evmNodeEnvironment";
FIXTURE_DATA_CREATION_DIR="${BASEDIR}/fixtures/dataCreation";

echo "fetching node repo and launch the nodes...";
cd "$EVM_NODE_DIR" || exit;
git clone "git@github.com:rocknitive/compose-evm-regtest-v1.git";
echo "launching the nodes...";
git checkout -b cr/single_forger && cd scripts && ./init.sh;

echo "creating the fixtures...";
cd "$FIXTURE_DATA_CREATION_DIR" || exit;

echo "installing hardhat deps...";
yarn install --non-interactive --frozen-lockfile;
# yarn install --quiet --non-interactive --frozen-lockfile;

echo "creating 1K accounts...";

echo "deploying ERC-20 contract...";
echo "creating gas tokens and distribute them...";


echo "sending tokens to addresses...";

echo "starting the benchmark...";
echo "locust...";
