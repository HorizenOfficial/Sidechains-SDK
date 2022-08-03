#executes a wallet/reindex call
def http_wallet_reindex(sidechainNode, api_key = None):
    if (api_key != None):
        response = sidechainNode.wallet_reindex({}, api_key)
    else:
        response = sidechainNode.wallet_reindex()
    return response['result']['started']

#executes a wallet/reindexStatus call
def http_wallet_reindex_status(sidechainNode, api_key = None):
    if (api_key != None):
        response = sidechainNode.wallet_reindexStatus({}, api_key)
    else:
        response = sidechainNode.wallet_reindexStatus()
    return response['result']['status']

#executes a debug/reindexStep call
def http_debug_reindex_step(sidechainNode, api_key = None):
    if (api_key != None):
        response = sidechainNode.debug_reindexStep({}, api_key)
    else:
        response = sidechainNode.debug_reindexStep()
    return response['result']
