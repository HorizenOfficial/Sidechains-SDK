import json


# execute a transaction/signTransaction call
def signTransaction(sidechainNode, *, fromAddress=None, payload):
    j = {
        "from": fromAddress,
        "payload": payload
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_signTransaction(request)
    return response["result"]["transactionId"]
