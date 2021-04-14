#!/bin/bash

set -eo pipefail

SIMPLE_APP_VERSION="${SIMPLE_APP_VERSION:-0.2.7}"

if [ -d "$1" ] && [ -f "$2" ]; then
  path_to_jemalloc="$(ldconfig -p | grep "$(arch)" | grep 'libjemalloc\.so\.1$' | tr -d ' ' | cut -d '>' -f 2)"
  if [ -f "$path_to_jemalloc" ]; then
    echo "Starting sidechain..."
    LD_PRELOAD="$path_to_jemalloc:$LD_PRELOAD" exec java -cp "${1}/target/sidechains-sdk-simpleapp-${SIMPLE_APP_VERSION}.jar:${1}/target/lib/*" com.horizen.examples.SimpleApp "$2"
  else
    echo "Could not find jemalloc library. Please install jemalloc to keep memory consumption in check."
    exit 1
  fi
else
  echo "Usage: runsh.sh <path_to_app_dir> <path_to_config_file>"
  exit 1
fi
