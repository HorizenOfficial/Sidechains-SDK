#!/bin/bash

set -eEuo pipefail

command -v jq &> /dev/null || { echo "jq is required to run this script"; exit 1; }
command -v bc &> /dev/null || { echo "bc is required to run this script"; exit 1; }
command -v pwgen &> /dev/null || { echo "pwgen is required to run this script"; exit 1; }
( docker compose version 2>&1 || docker-compose version 2>&1 ) | grep -q v2 || { echo "docker compose v2 is required to run this script"; exit 1; }

compose_cmd="$(docker compose version 2>&1 | grep -q v2 && echo 'docker compose' || echo 'docker-compose')"
workdir="$( cd "$( dirname "${BASH_SOURCE[0]}" )/.." &> /dev/null && pwd )"
compose_project_name=$( grep COMPOSE_PROJECT_NAME "${workdir}"/.env | cut -d '=' -f2 )
bootstrap_compose_file='docker-compose-bootstrap.yml'

# Functions
function fn_die() {
  echo -e "$1" >&2
  exit "${2:-1}"
}

bootstrap_simpleapp () {
  local cmd="$1"
  local args="$2"
  # shellcheck disable=SC2086
  $compose_cmd -f "${workdir}/${bootstrap_compose_file}" run --rm simpleapp "$cmd" $args | tail -n1
}

zen-cli_cmd () {
  local cmd="$1"
  local args="${*:2}"
  # shellcheck disable=SC2086
  $compose_cmd -f "${workdir}/${bootstrap_compose_file}" exec zend gosu user zen-cli "$cmd" $args
}

cd "${workdir}"

# Checking if .env file exist
if ! [ -f "${workdir}/.env" ]; then
  fn_die "Create .env file under ${workdir} based on .env_sample.  Exiting..."
fi


######
# Starting zend container if not running.
# Checking if zend is running and fully synchronized
#####
if [ -z "$(docker ps -q -f status=running -f name="zend")" ]; then
  echo "" && echo "=== Starting zend ===" && echo ""
  $compose_cmd -f "${workdir}/${bootstrap_compose_file}" up -d zend
fi

# Waiting for zend to start
while ! zen-cli_cmd getinfo &> /dev/null; do
  echo "Waiting for ZEND to start..."
  sleep 20
done

blockchain="$(zen-cli_cmd getblockchaininfo | jq -r '.chain')"
if [ "${blockchain}" = 'test' ]; then
  api_sync_endpoint='https://explorer-testnet.horizen.io/api/sync'
elif [ "${blockchain}" = 'main' ]; then
  api_sync_endpoint='https://explorer.horizen.io/api/sync'
fi

if [ "${blockchain}" = 'test' ] || [ "${blockchain}" = 'main' ]; then
  blocks_amount_blockchain="$(curl -s "${api_sync_endpoint}" | jq -r '.blockChainHeight')"
  blocks_amount_zend="$(zen-cli_cmd getblockcount)"

  echo "" && echo "=== Total blocks on ${blockchain} Blockchain = ${blocks_amount_blockchain}. Total blocks on ZEND node = ${blocks_amount_zend} ===" && echo ""

  spin='-\|/'
  i=0
  until [ "${blocks_amount_zend}" -eq "${blocks_amount_blockchain}" ]; do
    sleep 0.1
    i=$(( (i+1) % 4 ))
    echo -n "Waiting for ZEND node to be fully synchronized with ${blockchain} Blockchain. This may take a while ..."
    # shellcheck disable=SC2059
    printf "\r${spin:$i:1}"

    blocks_amount_blockchain="$(curl -s "${api_sync_endpoint}" | jq -r '.blockChainHeight')"
    blocks_amount_zend="$(zen-cli_cmd getblockcount)"
  done
fi


######
# Preparing Bootstrapping container to start from scratch
######
if [ -n "$(docker ps -a -q -f name="simpleapp")" ]; then
  echo "" && echo "=== Cleaning up previous bootstrapping container resources ===" && echo ""
  docker stop simpleapp && docker rm simpleapp
fi

# Cleaning up Bootstrapping container volumes if exist
if docker volume inspect "${compose_project_name}_evmapp-forger-data" &>/dev/null; then
  echo "" && echo "=== Deleting bootstrapping container data volume ===" && echo ""
  docker volume rm "${compose_project_name}_evmapp-forger-data"
fi

if docker volume inspect "${compose_project_name}_evmapp-snark-keys" &>/dev/null; then
  echo "" && echo "=== Deleting bootstrapping container snark-keys volume ===" && echo ""
  docker volume rm "${compose_project_name}_evmapp-snark-keys"
fi

######
# Collecting parameters
######
zend_address="$(zen-cli_cmd listaddresses | jq -rc '.[0]')"
zen_funds="$(zen-cli_cmd getwalletinfo | jq -r .balance)"

read -p "Input ZEN amount for forward transfer to Sidechain Nodes: " ftAmount
read -p "Input withdrawalEpochLen: " withdrawalEpochLen
read -p "Input number of FORGER nodes to setup: " forgerNodesAmount
read -p "Input threshold amount of signers(SCNODE_CERT_SIGNERS_THRESHOLD): " signers_threshold

# Checking the sanity
maxpks="${forgerNodesAmount}"
more_than_half_forgers_amount="$(( ( forgerNodesAmount + 2 - 1 ) / 2 ))"
if [ "${signers_threshold}" -gt "${maxpks}" ] || [ "${signers_threshold}" -lt "${more_than_half_forgers_amount}" ] ; then
  fn_die "Error for the requirement: 50% of SCNODE_CERT_SIGNERS_MAXPKS < SCNODE_CERT_SIGNERS_THRESHOLD <= SCNODE_CERT_SIGNERS_MAXPKS   Exiting..."
fi

# Checking amount of zen for Sidechain creation and FW transfer(s)
echo "" && echo "=== Your current ZEND balance = ${zen_funds} ZEN. Your first ZEND address is = ${zend_address} ===" && echo ""
needed_amount="$(echo "scale=6; ${forgerNodesAmount}*${ftAmount}" | bc)"
if (( $(echo "${zen_funds} < ${needed_amount}" | bc -l) )); then
  fn_die "There is not enough ZEN on your balance: ${zen_funds} < ${needed_amount}.\nCollect enough ZEN >= ${needed_amount} before proceeding. It is a requirement for Sidechain registration and operation.  Exiting..."
fi

# Prompting for user's consent to spend ZEN for Sidechain registration
echo "" && echo "=== Max amount of ${needed_amount} ZEN will be used for Sidechain registration ===" && echo ""
read -p "Continue (y/n)? " REPLY
if [[ ! "${REPLY}" =~ ^[Yy]$ ]]; then
    fn_die "Exiting..."
fi


######
# Generating SEED phrases
######
echo "" && echo "=== Generating SEED phrases for Sidechain nodes ===" && echo ""
forger_seed_phrases_list=()
i=1
until [ ${i} -gt "${forgerNodesAmount}" ]; do
  seed="$(pwgen 64 1)"
  forger_seed_phrases_list+=("${seed}")
  ((i++))
done


######
# Generating keys
######
echo "" && echo "=== Generating Keys ===" && echo ""
keypair_forgers_list=()
vrfkeypair_forgers_list=()
account_keypair_forgers_list=()
cert_forgers_list=()
for seed in "${forger_seed_phrases_list[@]}"; do
  keyPairForger="$(bootstrap_simpleapp generatekey '{"seed":"'"$seed"'"}')"
  keypair_forgers_list+=("${keyPairForger}")

  vrfKeyPairForger="$(bootstrap_simpleapp generateVrfKey '{"seed":"'"$seed"'"}')"
  vrfkeypair_forgers_list+=("${vrfKeyPairForger}")

  accountKeyPairForger="$(bootstrap_simpleapp generateAccountKey '{"seed":"'"$seed"'"}')"
  account_keypair_forgers_list+=("${accountKeyPairForger}")

  certForger="$(bootstrap_simpleapp generateCertificateSignerKey '{"seed":"'"$seed"'"}')"
  cert_forgers_list+=("${certForger}")
done


######
# Generating CertProofInfo
######
echo "" && echo "=== Generating CertProofInfo ===" && echo ""
proof_info_args="["
for index in "${!cert_forgers_list[@]}"; do
  # shellcheck disable=SC2027
  proof_info_args+="\""$(jq -rc ".signerPublicKey" <<< "${cert_forgers_list[${index}]}")"\","
done
# Trimming final version of the string
signersPubKeys="${proof_info_args%?}]"
CertProofInfo="$(bootstrap_simpleapp generateCertProofInfo '{"signersPublicKeys":'"${signersPubKeys}"',"threshold":'"${signers_threshold}"',"verificationKeyPath":"/tools/output/marlin_snark_vk","provingKeyPath":"/tools/output/marlin_snark_pk","isCSWEnabled":false}')"


######
# Registering a Sidechain
######
echo "" && echo "=== Registering a Sidechain ===" && echo ""

# Registering sidechain using SC node #1 parameters
arg="{\"version\":1,"
arg+="\"withdrawalEpochLength\":${withdrawalEpochLen},"
arg+="\"toaddress\":\"$(jq -rc ".accountProposition" <<< "${account_keypair_forgers_list[0]}")\","
arg+="\"amount\":${ftAmount},"
arg+="\"wCertVk\":\"$(jq -rc ".verificationKey" <<< "${CertProofInfo}")\","
arg+="\"customData\":\"$(jq -rc ".vrfPublicKey" <<< "${vrfkeypair_forgers_list[0]}")$(jq -rc ".publicKey" <<< "${keypair_forgers_list[0]}")\","
arg+="\"constant\":\"$(jq -rc ".genSysConstant" <<< "${CertProofInfo}")\","
arg+="\"vFieldElementCertificateFieldConfig\":[]}"
scCreate="$(zen-cli_cmd sc_create "${arg}")"

scId=$(jq -rc '.scid' <<< "${scCreate}")
echo "Sidechain ID is: ${scId}"

# Checking if sidechain was created
spin='-\|/'
i=0
until [ "$(zen-cli_cmd getscinfo "${scId}" | jq -r '.items[].state')" = 'ALIVE' ]; do
  sleep 0.1
  i=$(( (i+1) % 4 ))
  echo -n "Waiting for Sidechain to be registered on Mainchain"
  # shellcheck disable=SC2059
  printf "\r${spin:$i:1}"
done

echo "" && echo "=== Sidechain was successfully registered. Getting genesisinfo ===" && echo ""

scgenesisinfo="$(zen-cli_cmd getscgenesisinfo "${scId}")"
# shellcheck disable=SC2046
genesisinfo="$(bootstrap_simpleapp genesisinfo '{"blockversion": 2, "info":"'"${scgenesisinfo}"'","secret":"'$(jq -rc ".secret" <<< "${keypair_forgers_list[0]}")'","vrfSecret":"'$(jq -rc ".vrfSecret" <<< "${vrfkeypair_forgers_list[0]}")'"}')"


######
# Populating settings shared between all the nodes
#####
echo "" && echo "=== Populating settings shared between all the nodes ===" && echo ""

# Populating SC_GENISIS variables
sed -i "s/SCNODE_GENESIS_BLOCKHEX=.*/SCNODE_GENESIS_BLOCKHEX=$(jq -rc '.scGenesisBlockHex' <<< "${genesisinfo}")/g" .env
sed -i "s/SCNODE_GENESIS_SCID=.*/SCNODE_GENESIS_SCID=${scId}/g" .env
sed -i "s/SCNODE_GENESIS_POWDATA=.*/SCNODE_GENESIS_POWDATA=$(jq -rc '.powData' <<< "${genesisinfo}")/g" .env
sed -i "s/SCNODE_GENESIS_MCBLOCKHEIGHT=.*/SCNODE_GENESIS_MCBLOCKHEIGHT=$(jq -rc '.mcBlockHeight' <<< "${genesisinfo}")/g" .env
sed -i "s/SCNODE_GENESIS_MCNETWORK=.*/SCNODE_GENESIS_MCNETWORK=$(jq -rc '.mcNetwork' <<< "${genesisinfo}")/g" .env
sed -i "s/SCNODE_GENESIS_WITHDRAWALEPOCHLENGTH=.*/SCNODE_GENESIS_WITHDRAWALEPOCHLENGTH=$(jq -rc '.withdrawalEpochLength' <<< "${genesisinfo}")/g" .env
sed -i "s/SCNODE_GENESIS_COMMTREEHASH=.*/SCNODE_GENESIS_COMMTREEHASH=$(jq -rc '.initialCumulativeCommTreeHash' <<< "${genesisinfo}")/g" .env

sed -i "s/SCNODE_CERT_SIGNERS_MAXPKS=.*/SCNODE_CERT_SIGNERS_MAXPKS=${maxpks}/g" .env
sed -i "s/SCNODE_CERT_SIGNERS_THRESHOLD=.*/SCNODE_CERT_SIGNERS_THRESHOLD=${signers_threshold}/g" .env


######
# Getting SCNODE_CERT_SIGNERS_PUBKEYS list
######
# shellcheck disable=SC2116
sc_name_cert_signers_pubkeys="$(echo "${signersPubKeys:1:-1}")"
sed -i "s/SCNODE_CERT_SIGNERS_PUBKEYS=.*/SCNODE_CERT_SIGNERS_PUBKEYS='$sc_name_cert_signers_pubkeys'/g" .env


######
# Getting ALLOWED Forgers list
#####
for index in "${!keypair_forgers_list[@]}"; do
  # shellcheck disable=SC2027
  allowed_forgers_args+="{blockSignProposition: \""$(jq -rc '.publicKey' <<< "${keypair_forgers_list[${index}]}")"\"\\\\n vrfPublicKey: \""$(jq -rc '.vrfPublicKey' <<< "${vrfkeypair_forgers_list[${index}]}")"\"}\\\\n"
done
# Trimming final version of the string
allowed_forgers="${allowed_forgers_args%???}"
sed -i "s/SCNODE_FORGER_ALLOWED_FORGERS=.*/SCNODE_FORGER_ALLOWED_FORGERS=${allowed_forgers}/g" .env


######
# Creating .env files for all the nodes
#####
echo "" && echo "=== Generating env files per Sidechain node ===" && echo ""
if [ ! -d "${workdir}/env_files" ]; then
  mkdir "${workdir}/env_files"
else
  rm -rf "${workdir}/env_files/*"
fi

# Forgers nodes env files
for i in "${!forger_seed_phrases_list[@]}"; do
  file_index=$((i+1))
  cp -a "${workdir}/.env" "${workdir}/env_files/env_forger_${file_index}"
  cd "${workdir}/env_files"

  sed -i "s/SCNODE_CERT_SIGNERS_SECRETS=.*/SCNODE_CERT_SIGNERS_SECRETS='\"$(jq -rc '.signerSecret' <<< "${cert_forgers_list[$i]}")\"'/g" "env_forger_${file_index}"
  sed -i "s/SCNODE_WALLET_GENESIS_SECRETS=.*/SCNODE_WALLET_GENESIS_SECRETS='\"$(jq -rc ".secret" <<< "${keypair_forgers_list[$i]}")\",\"$(jq -rc ".vrfSecret" <<< "${vrfkeypair_forgers_list[$i]}")\",\"$(jq -rc ".accountSecret" <<< "${account_keypair_forgers_list[$i]}")\"'/g" "env_forger_${file_index}"
  sed -i "s/SCNODE_WALLET_SEED=.*/SCNODE_WALLET_SEED=${forger_seed_phrases_list[$i]}/g" "env_forger_${file_index}"
  sed -i "s/SCNODE_NET_NODENAME=.*/SCNODE_NET_NODENAME=${compose_project_name}_forger${file_index}/g" "env_forger_${file_index}"

  # Vars for forger stake command
  sed -i "s/SCNODE_FWT_ACCOUNT_ADDRESS_FORGER=.*/SCNODE_FWT_ACCOUNT_ADDRESS_FORGER=\"$(jq -rc ".accountProposition" <<< "${account_keypair_forgers_list[$i]}")\"/g" "env_forger_${file_index}"
  sed -i "s/SCNODE_FWT_AMOUNT=.*/SCNODE_FWT_AMOUNT=${ftAmount}/g" "env_forger_${file_index}"
  sed -i "s/SCNODE_FWT_PUBKEY_PAIR_FORGER=.*/SCNODE_FWT_PUBKEY_PAIR_FORGER=\"$(jq -rc ".publicKey" <<< "${keypair_forgers_list[$i]}")\"/g" "env_forger_${file_index}"
  sed -i "s/SCNODE_FWT_VRF_PUBKEY_PAIR_FORGER=.*/SCNODE_FWT_VRF_PUBKEY_PAIR_FORGER=\"$(jq -rc ".vrfPublicKey" <<< "${vrfkeypair_forgers_list[$i]}")\"/g" "env_forger_${file_index}"
done


######
# Forward transfer to the forger nodes
#####
# Sending forward transfer ONLY if more than ONE forger nodes configured
if [ "${#account_keypair_forgers_list[@]}" -gt 1 ];then
  echo "" && echo "=== Running forward transfer=${ftAmount} ZEN to all FORGER nodes ===" && echo ""
  mcReturnAddress="$(zen-cli_cmd listaddresses | jq -rc '.[0]')"

  sc_send_args="["
  for ((i=1; i<${#account_keypair_forgers_list[*]}; i++)); do
    sc_send_args+="{"
    sc_send_args+="\"scid\":\"${scId}\","
    sc_send_args+="\"toaddress\":\"$(jq -rc ".accountProposition" <<< "${account_keypair_forgers_list[$i]}")\","
    sc_send_args+="\"amount\":${ftAmount},"
    sc_send_args+="\"mcReturnAddress\":\"${mcReturnAddress}\""
    sc_send_args+="},"
  done

  # Trimming final version of the string
  sc_send_string="${sc_send_args%?}]"
  zen-cli_cmd sc_send "${sc_send_string}"
  echo "" && echo "=== Forward transfer was sent. Verify the balance(wallet/getBalance) on every FORGER node ===" && echo ""
fi


######
# The END
######
echo "" && echo "=== Done ===" && echo ""


######
# Notifying user about UTXO requirement for ZEND nodes
######
echo "" && echo "=== Make sure to fund ZEND node running on forger instances using at LEAST 5 UTXOs. It is a requirement for certificate submission HA ===" && echo ""
exit 0