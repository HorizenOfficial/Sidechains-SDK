from __future__ import annotations
from enum import Enum
from typing_extensions import TypedDict


class PerformanceData(TypedDict):
    test_type: str
    test_run_time: int
    block_rate: int
    initial_txs: int
    network_topology: str
    nodes: list[NodeData]


class NodeData(TypedDict, total=False):
    latency_settings: int
    forger: bool
    tx_creator: bool


class NetworkTopology(Enum):
    DaisyChain = "daisy_chain"
    Ring = "ring"
    Star = "star"


class TestType(Enum):
    Mempool = "mempool"
    Mempool_Timed = "mempool_timed"
    Transactions_Per_Second = "transactions_per_second"
    All_Transactions = "all_transactions"
