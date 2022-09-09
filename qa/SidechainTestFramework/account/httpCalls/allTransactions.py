import json


# execute a transaction/allTransactions call
def allTransactions(sidechainNode, *, format=False):
    j = {
        "format": format
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_allTransactions(request)

    if "result" in response:
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
