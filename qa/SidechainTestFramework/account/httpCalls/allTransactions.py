import json


# execute a transaction/allTransactions call
def allTransactions(sidechainNode, *, format=False):
    j = {
        "format": format
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_allTransactions(request)
    return response["result"]["transactionId"]
