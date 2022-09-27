#!/bin/bash

if [ $# != 1 ]; then
  echo "please provide a python test file"
  exit 1
fi

#set -e

#kill $(pgrep -f "evmapp|zend")
pkill "evmapp|zend"

rm -rf /tmp/sc_test*

python3 $1 --noshutdown

cat /tmp/sc_test*/sc_node0/node0.conf | grep -A 1 restApi
