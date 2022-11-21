import json

# execute a transaction/allTransactions call (list of all mempool transactions)
def allTransactions(sidechainNode, format = True):
    j = {"format": format}
    request = json.dumps(j)
    response = sidechainNode.transaction_allTransactions(request)
    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))