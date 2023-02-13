import json


def createLegacyTransaction(sidechainNode, *, fromAddress=None, toAddress=None, nonce=None, gasLimit=21000,
                            gasPrice=1000000000, value=0, data='',
                            signature_v=None, signature_r=None, signature_s=None, api_key=None):

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
    if api_key is not None:
        response = sidechainNode.transaction_createLegacyTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_createLegacyTransaction(request)

    if "result" in response:
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
