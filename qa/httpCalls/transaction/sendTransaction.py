import json


# execute a transaction/sendTransaction call
def sendTransaction(sidechainNode, tx_bytes, api_key = None):
    j = {
        "transactionBytes": tx_bytes
    }
    request = json.dumps(j)
    if (api_key != None):
        response = sidechainNode.transaction_sendTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_sendTransaction(request)
    return response
