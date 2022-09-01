from __future__ import annotations
from enum import Enum
from typing_extensions import TypedDict


class PerformanceData(TypedDict):
    test_type: int
    test_run_time: int
    block_rate: int
    initial_txs: int
    network_topology: int
    nodes: list[NodeData]


class NodeData(TypedDict, total=False):
    latency_settings: int
    forger: bool
    tx_creator: bool
    throughput: int
    total_execution_time: int


class NetworkTopology(Enum):
    DaisyChain = 1
    Ring = 2
    Star = 3


class TestType(Enum):
    Mempool = 1
    Mempool_Timed = 2
    Transactions_Per_Second = 3
    All_Transactions = 4
