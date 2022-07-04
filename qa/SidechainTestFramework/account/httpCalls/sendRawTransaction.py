import json


# execute a transaction/sendRawTransaction call
def sendRawTransaction(sidechainNode, *, fromAddress=None, payload):
    j = {
        "from": fromAddress,
        "payload": payload
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_sendRawTransaction(request)
    return response["result"]["transactionId"]
