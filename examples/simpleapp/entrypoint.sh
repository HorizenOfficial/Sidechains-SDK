#!/bin/bash

set -euo pipefail

USER_ID="${LOCAL_USER_ID:-9001}"
GRP_ID="${LOCAL_GRP_ID:-9001}"
LD_PRELOAD="${LD_PRELOAD:-}"

if [ "$USER_ID" != "0"  ]; then
    getent group "$GRP_ID" &> /dev/null || groupadd -g "$GRP_ID" user
    id -u user &> /dev/null || useradd --shell /bin/bash -u "$USER_ID" -g "$GRP_ID" -o -c "" -m user
    CURRENT_UID="$(id -u user)"
    CURRENT_GID="$(id -g user)"
    if [ "$USER_ID" != "$CURRENT_UID" ] || [ "$GRP_ID" != "$CURRENT_GID" ]; then
        echo -e "WARNING: User with differing UID $CURRENT_UID/GID $CURRENT_GID already exists, most likely this container was started before with a different UID/GID. Re-create it to change UID/GID.\n"
    fi
else
    CURRENT_UID="$USER_ID"
    CURRENT_GID="$GRP_ID"
    echo -e "WARNING: Starting container processes as root. This has some security implications and goes against docker best practice.\n"
fi

# set $HOME
if [ "$CURRENT_UID" != "0" ]; then
    export USERNAME=user
    export HOME=/home/"$USERNAME"
else
    export USERNAME=root
    export HOME=/root
fi

# detect external IPv4 address
SCNODE_NET_DECLAREDADDRESS="$(dig -4 +short +time=2 @resolver1.opendns.com A myip.opendns.com | grep -v ";" || true)"
if [ -z "${SCNODE_NET_DECLAREDADDRESS}" ]; then
  echo "Error: Failed to detect external IPv4 address, using internal address."
  SCNODE_NET_DECLAREDADDRESS="$(hostname -I)"
  SCNODE_NET_DECLAREDADDRESS="${SCNODE_NET_DECLAREDADDRESS%% }"
fi
export SCNODE_NET_DECLAREDADDRESS

to_check=(
  "SCNODE_CERT_SIGNERS_MAXPKS"
  "SCNODE_CERT_SIGNERS_PUBKEYS"
  "SCNODE_CERT_SIGNERS_THRESHOLD"
  "SCNODE_CERT_SIGNING_ENABLED"
  "SCNODE_CERT_SUBMITTER_ENABLED"
  "SCNODE_FORGER_ENABLED"
  "SCNODE_FORGER_RESTRICT"
  "SCNODE_GENESIS_BLOCKHEX"
  "SCNODE_GENESIS_SCID"
  "SCNODE_GENESIS_POWDATA"
  "SCNODE_GENESIS_MCBLOCKHEIGHT"
  "SCNODE_GENESIS_MCNETWORK"
  "SCNODE_GENESIS_WITHDRAWALEPOCHLENGTH"
  "SCNODE_GENESIS_COMMTREEHASH"
  "SCNODE_NET_DECLAREDADDRESS"
  "SCNODE_NET_KNOWNPEERS"
  "SCNODE_NET_MAGICBYTES"
  "SCNODE_NET_NODENAME"
  "SCNODE_NET_P2P_PORT"
  "SCNODE_REST_PORT"
  "SCNODE_WALLET_SEED"
)
for var in "${to_check[@]}"; do
  if [ -z "${!var:-}" ]; then
    echo "Error: Environment variable ${var} required."
    sleep 5
    exit 1
  fi
done

if [ "${SCNODE_FORGER_RESTRICT:-}" = "true" ]; then
  if [ -z "${SCNODE_FORGER_ALLOWED_FORGERS:-}" ]; then
    echo "Error: Environment variable SCNODE_FORGER_ALLOWED_FORGERS required when SCNODE_FORGER_RESTRICT=true."
    sleep 5
    exit 1
  fi
fi

if [ "${SCNODE_CERT_SIGNING_ENABLED:-}" = "true" ] || [ "${SCNODE_CERT_SUBMITTER_ENABLED:-}" = "true" ]; then
  if [ -z "${SCNODE_CERT_SIGNERS_SECRETS:-}" ]; then
    echo "Error: Environment variable SCNODE_CERT_SIGNERS_SECRETS required when SCNODE_CERT_SIGNING_ENABLED=true or SCNODE_CERT_SUBMITTER_ENABLED=true."
    sleep 5
    exit 1
  fi
fi

if [ "${SCNODE_FORGER_ENABLED:-}" = "true" ] || [ "${SCNODE_CERT_SUBMITTER_ENABLED:-}" = "true" ]; then
  if [ -z "${SCNODE_WS_ZEN_FQDN:-}" ]; then
    echo "Error: Environment variable SCNODE_WS_ZEN_FQDN is required when SCNODE_FORGER_ENABLED=true or SCNODE_CERT_SUBMITTER_ENABLED=true."
    sleep 5
    exit 1
  fi
fi

# set REST API password hash
SCNODE_REST_APIKEYHASH=""
if [ -n "${SCNODE_REST_PASSWORD:-}" ]; then
  SCNODE_REST_APIKEYHASH="$(echo -en "\n        apiKeyHash = \"$(echo -n "$SCNODE_REST_PASSWORD" | b2sum -l256 | cut -d" " -f1)\"")"
fi
export SCNODE_REST_APIKEYHASH

# download and extract backup for import
backupdir="/sidechain/datadir/backupStorage"
if [ -n "${SCNODE_BACKUP_TAR_GZ_URL:-}" ] && ! [ -f "${backupdir}/.import_done" ]; then
  echo "Importing backup from '${SCNODE_BACKUP_TAR_GZ_URL}' to '${backupdir}'."
  mkdir -p "${backupdir}"
  curl -L "${SCNODE_BACKUP_TAR_GZ_URL}" | tar -xzf - -C "${backupdir}"
  touch "${backupdir}/.import_done"
fi

# detect zend container IP, check zend is up and websocket port is reachable
WS_ADDRESS=""
if [ -n "${SCNODE_WS_ZEN_FQDN:-}" ]; then
  to_check=(
    "RPC_PASSWORD"
    "RPC_PORT"
    "RPC_USER"
    "SCNODE_WS_ZEN_PORT"
  )
  for var in "${to_check[@]}"; do
    if [ -z "${!var:-}" ]; then
      echo "Error: Environment variable ${var} required when SCNODE_WS_ZEN_FQDN is set."
      sleep 5
      exit 1
    fi
  done

  # detect IP address
  i=0
  while true; do
    SCNODE_WS_ZEN_IP="$(dig A +short "${SCNODE_WS_ZEN_FQDN}" | grep -v ";" || true)"
    if [ -n "${SCNODE_WS_ZEN_IP:-}" ]; then
      break
    fi
    sleep 5
    i="$((i+1))"
    if [ "$i" -gt 48 ]; then
      echo "Error: Failed to detect IP address of '${SCNODE_WS_ZEN_FQDN}' container after 4 minutes."
      exit 1
    fi
  done
  export SCNODE_WS_ZEN_IP
  echo "Detected IP address '${SCNODE_WS_ZEN_IP}' of '${SCNODE_WS_ZEN_FQDN}' container."

  # make sure zend is up by checking RPC interface
  i=0
  while ! curl --data-binary '{"jsonrpc": "1.0", "id":"curltest", "method": "getblockcount", "params": [] }' -H 'content-type: text/plain;' "http://${RPC_USER}:${RPC_PASSWORD}@${SCNODE_WS_ZEN_IP}:${RPC_PORT}/" &> /dev/null; do
    echo "Waiting for '${SCNODE_WS_ZEN_FQDN}' container to be ready."
    sleep 5
    i="$((i+1))"
    if [ "$i" -gt 48 ]; then
      echo "Error: '${SCNODE_WS_ZEN_FQDN}' container not ready after 4 minutes."
      exit 1
    fi
  done
  echo "RPC server of '${SCNODE_WS_ZEN_FQDN}' container ready."

  # make sure websocket port is reachable
  i=0
  while ! nc -z "${SCNODE_WS_ZEN_IP}" "${SCNODE_WS_ZEN_PORT}" &> /dev/null; do
    echo "Waiting for '${SCNODE_WS_ZEN_FQDN}' container websocket port to be ready."
    sleep 5
    i="$((i+1))"
    if [ "$i" -gt 48 ]; then
      echo "Error: '${SCNODE_WS_ZEN_FQDN}' container websocket port not ready after 4 minutes."
      exit 1
    fi
  done
  echo "Websocket server of '${SCNODE_WS_ZEN_FQDN}' container ready."

  # Setting websocket address option under conf file
  WS_ADDRESS="ws://${SCNODE_WS_ZEN_IP}:${SCNODE_WS_ZEN_PORT}"
fi
export WS_ADDRESS

# convert literal '\n' to newlines
SCNODE_CERT_SIGNERS_PUBKEYS="$(echo -e "${SCNODE_CERT_SIGNERS_PUBKEYS:-}")"
SCNODE_CERT_SIGNERS_SECRETS="$(echo -e "${SCNODE_CERT_SIGNERS_SECRETS:-}")"
SCNODE_FORGER_ALLOWED_FORGERS="$(echo -e "${SCNODE_FORGER_ALLOWED_FORGERS:-}")"
SCNODE_NET_KNOWNPEERS="$(echo -e "${SCNODE_NET_KNOWNPEERS:-}")"
export SCNODE_CERT_SIGNERS_PUBKEYS
export SCNODE_CERT_SIGNERS_SECRETS
export SCNODE_FORGER_ALLOWED_FORGERS
export SCNODE_NET_KNOWNPEERS

# substitute env vars in scnode config file
# shellcheck disable=SC2016,SC2026
SUBST='$SCNODE_CERT_SIGNERS_MAXPKS:$SCNODE_CERT_SIGNERS_PUBKEYS:$SCNODE_CERT_SIGNERS_SECRETS:$SCNODE_CERT_SIGNERS_THRESHOLD:$SCNODE_CERT_SIGNING_ENABLED:'\
'$SCNODE_CERT_SUBMITTER_ENABLED:$SCNODE_GENESIS_BLOCKHEX:$SCNODE_GENESIS_SCID:$SCNODE_GENESIS_POWDATA:$SCNODE_GENESIS_MCBLOCKHEIGHT:$SCNODE_GENESIS_MCNETWORK:'\
'$SCNODE_GENESIS_WITHDRAWALEPOCHLENGTH:$SCNODE_GENESIS_COMMTREEHASH:$SCNODE_FORGER_ALLOWED_FORGERS:$SCNODE_FORGER_ENABLED:$SCNODE_FORGER_RESTRICT:'\
'$SCNODE_NET_DECLAREDADDRESS:$SCNODE_NET_KNOWNPEERS:$SCNODE_NET_MAGICBYTES:$SCNODE_NET_NODENAME:$SCNODE_NET_P2P_PORT:$SCNODE_REST_APIKEYHASH:$SCNODE_REST_PORT:'\
'$SCNODE_WALLET_GENESIS_SECRETS:$SCNODE_WALLET_MAXTX_FEE:$SCNODE_WALLET_SEED:$WS_ADDRESS'
export SUBST
envsubst "${SUBST}" < /sidechain/config/sc_settings.conf.tmpl > /sidechain/config/sc_settings.conf
unset SUBST

# set file ownership
find /sidechain -writable -print0 | xargs -0 -I{} -P64 -n1 chown -f "${CURRENT_UID}":"${CURRENT_GID}" "{}"

# preload libjemalloc2
path_to_jemalloc="$(ldconfig -p | grep "$(arch)" | grep 'libjemalloc\.so\.2$' | tr -d ' ' | cut -d '>' -f 2)"
export LD_PRELOAD="${path_to_jemalloc}:${LD_PRELOAD}"

if [ "${1}" = "/usr/bin/true" ]; then
  set -- java -cp '/sidechain/'"${SC_JAR_NAME}"'-'"${SDK_VERSION}"'.jar:/sidechain/lib/*' $SC_MAIN_CLASS $SC_CONF_PATH
fi

echo "Username: ${USERNAME}, UID: ${CURRENT_UID}, GID: ${CURRENT_GID}"
echo "LD_PRELOAD: ${LD_PRELOAD}"
echo "Starting sidechain node."
echo "Command: '$@'"

if [ "${USERNAME}" = "user" ]; then
    exec /usr/local/bin/gosu user "$@"
else
    exec "$@"
fi