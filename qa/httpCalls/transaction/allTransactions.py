import json

# execute a transaction/allTransactions call (list of all mempool transactions)
def allTransactions(sidechainNode, format):
    j = {"format": format }
    request = json.dumps(j)
    response = sidechainNode.transaction_allTransactions(request)
    return response["result"]