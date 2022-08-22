from enum import Enum


class CallMethod(Enum):
    RPC_LEGACY = 0
    RPC_EIP155 = 1
    RPC_EIP1559 = 2
