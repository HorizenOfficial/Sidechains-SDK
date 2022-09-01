import json


# execute a transaction/sendTransaction call
def sendTransaction(sidechainNode, tx_bytes):
    j = {
        "transactionBytes": tx_bytes
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_sendTransaction(request)
    return response