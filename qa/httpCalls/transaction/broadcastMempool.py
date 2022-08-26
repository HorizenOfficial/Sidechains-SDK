
def http_broadcast_mempool(sidechainNode):
    response = sidechainNode.transaction_broadcastMempool()
    return response["result"]
