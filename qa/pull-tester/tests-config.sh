#!/bin/bash
# Copyright (c) 2013-2014 The Bitcoin Core developers
# Distributed under the MIT software license, see the accompanying
# file COPYING or http://www.opensource.org/licenses/mit-license.php.

BUILDDIR="/home/osboxes/git/Sidechains-SDK"
EXEEXT=""

# These will turn into comments if they were disabled when configuring.
ENABLE_WALLET=1
ENABLE_UTILS=1
ENABLE_BITCOIND=1
ENABLE_ZMQ=1
#ENABLE_PROTON=1

REAL_BITCOIND="$BUILDDIR/qa/ZenCore/src/zend${EXEEXT}"
REAL_BITCOINCLI="$BUILDDIR/qa/ZenCore/src/zen-cli${EXEEXT}"

