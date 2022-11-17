import json


# execute a transaction/allTransactions call
def allTransactions(sidechainNode, format=False):
    j = {
        "format": format
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_allTransactions(request)

    if "result" in response:
        if format:
            if "transactions" in response["result"]:
                return response["result"]["transactions"]
        else:
            if "transactionIds" in response["result"]:
                return response["result"]["transactionIds"]


    raise RuntimeError("Something went wrong, see {}".format(str(response)))
