#!/bin/bash
set -euo pipefail

# Add local zenbuilder user, either use LOCAL_USER_ID:LOCAL_GRP_ID
# if set via environment or fallback to 9001:9001
USER_ID="${LOCAL_USER_ID:-9001}"
GRP_ID="${LOCAL_GRP_ID:-9001}"
if [ "$USER_ID" != "0" ]; then
    export USERNAME=zenbuilder
    getent group "$GRP_ID" &> /dev/null || groupadd -g "$GRP_ID" "$USERNAME"
    id -u "$USERNAME" &> /dev/null || useradd --shell /bin/bash -u "$USER_ID" -g "$GRP_ID" -o -c "" -m "$USERNAME"
    CURRENT_UID="$(id -u "$USERNAME")"
    CURRENT_GID="$(id -g "$USERNAME")"
    export HOME=/home/"$USERNAME"
    if [ "$USER_ID" != "$CURRENT_UID" ] || [ "$GRP_ID" != "$CURRENT_GID" ]; then
        echo "WARNING: User with differing UID ${CURRENT_UID}/GID ${CURRENT_GID} already exists, most likely this container was started before with a different UID/GID. Re-create it to change UID/GID."
    fi
else
    export USERNAME=root
    export HOME=/root
    CURRENT_UID="$USER_ID"
    CURRENT_GID="$GRP_ID"
    echo "WARNING: Starting container processes as root. This has some security implications and goes against docker best practice."
fi

# Installing dependency for EVM project
if [ -d /build/libevm ]; then
  echo "" && echo "=== Installing apt dependencies for EVM project!!! ===" && echo ""
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y software-properties-common
  add-apt-repository -y ppa:ethereum/ethereum
  apt-get update
  DEBIAN_FRONTEND=noninteractive apt-get install --no-install-recommends -y gcc libc6-dev solc
  apt-get -y clean
  apt-get -y autoclean
  rm -rf /var/lib/apt/lists/* /var/cache/apt/archives/*.deb
fi

# Print information
echo "" && echo "=== Environment Info ===" && echo ""
java --version
echo
lscpu
echo
free -h
echo
echo "Username: $USERNAME, HOME: $HOME, UID: $CURRENT_UID, GID: $CURRENT_GID"

if [ "$USERNAME" = "root" ]; then
  exec /build/ci/docker/entrypoint_setup_gpg.sh "$@"
else
  exec gosu "$USERNAME" /build/ci/docker/entrypoint_setup_gpg.sh "$@"
fi

