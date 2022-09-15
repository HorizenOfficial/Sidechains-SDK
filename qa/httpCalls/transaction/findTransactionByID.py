import json


# executes a  transaction/findById call
def http_transaction_findById(sidechainNode, txid):
    j = {
        "transactionId": txid,
        "format": True
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_findById(request)

    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
