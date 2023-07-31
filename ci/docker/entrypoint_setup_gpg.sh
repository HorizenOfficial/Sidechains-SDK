#!/bin/bash

set -euo pipefail

if [ "${IS_A_RELEASE}" = "true" ] && [ -s "/key.asc" ]; then
  # shellcheck disable=SC2155
  export GNUPGHOME="$(mktemp -d 2>/dev/null || mktemp -d -t 'GNUPGHOME')"
  # gpg: setting pinentry mode 'loopback' failed: Not supported https://www.fluidkeys.com/tweak-gpg-2.1.11/
  echo "allow-loopback-pinentry" > "${GNUPGHOME}"/gpg-agent.conf
  gpg2 --batch --fast-import /key.asc
fi

exec "$@"