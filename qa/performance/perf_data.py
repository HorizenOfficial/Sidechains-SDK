from __future__ import annotations
from enum import Enum

from SidechainTestFramework.sc_boostrap_info import LatencyConfig


class NetworkTopology(Enum):
    DaisyChain = "daisy_chain"
    Ring = "ring"
    Star = "star"


class TestType(Enum):
    Mempool = "mempool"
    Mempool_Timed = "mempool_timed"
    Transactions_Per_Second = "transactions_per_second"
    All_Transactions = "all_transactions"
