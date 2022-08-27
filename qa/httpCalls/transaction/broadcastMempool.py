
# execute a transaction/broadcastMempool call (used only for debug purpose. Boradcast alll the mempool transactions to the network)
def http_broadcast_mempool(sidechainNode):
    response = sidechainNode.transaction_broadcastMempool()
    return response["result"]