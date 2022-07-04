import json


# execute a transaction/createLegacyTransaction call
def createLegacyTransaction(sidechainNode, *, fromAddress=None, toAddress=None, nonce, gasLimit,
                            gasPrice, value=0, data='', signature_v=None, signature_r=None,
                            signature_s=None):
    j = {
        "from": fromAddress,
        "to": toAddress,
        "nonce": nonce,
        "gasLimit": gasLimit,
        "gasPrice": gasPrice,
        "value": value,
        "data": data,
        "signature_v": signature_v,
        "signature_r": signature_r,
        "signature_s": signature_s
    }
    request = json.dumps(j)
    response = sidechainNode.transaction_createLegacyTransaction(request)
    return response["result"]["transactionId"]
