from __future__ import annotations
from typing_extensions import TypedDict


class PerformanceData(TypedDict):
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
