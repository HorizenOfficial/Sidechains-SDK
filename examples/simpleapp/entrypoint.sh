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

# set file ownership
find /simpleapp/output /tmp -writable -print0 | xargs -0 -I{} -P64 -n1 chown -f "${CURRENT_UID}":"${CURRENT_GID}" "{}" &> /dev/null

# if we have no commands or "$1" appears to be a subcommand, inject "java -jar sctool.jar"
if [ "$#" -eq 0 ] || [ "${1}" = "/usr/bin/true" ]; then
  set -- java -jar /simpleapp/"${SC_JAR_NAME}-${SDK_VERSION}.jar" help
elif [[ "${1}" == +(help|generatekey|generateVrfKey|generateCertProofInfo|generateCswProofInfo|genesisinfo|generateAccountKey|generateCertificateSignerKey) ]]; then
  set -- java -jar /simpleapp/"${SC_JAR_NAME}-${SDK_VERSION}.jar" "$@"
fi

if [ "${USERNAME}" = "user" ]; then
    exec /usr/local/bin/gosu user "$@"
else
    exec "$@"
fi





#THIS IS RUNNING FINE