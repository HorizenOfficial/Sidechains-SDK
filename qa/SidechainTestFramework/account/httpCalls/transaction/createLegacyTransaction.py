import json


def createLegacyTransaction(sidechainNode, *, fromAddress=None, toAddress=None, nonce=None, gasLimit=21000,
                            gasPrice=1000000000, value=0, data='', signature_v=None, signature_r=None,
                            signature_s=None, api_key=None, output_raw_bytes=False):

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
        "signature_s": signature_s,
        "outputRawBytes": output_raw_bytes
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechainNode.transaction_createLegacyTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_createLegacyTransaction(request)

    if "result" in response:
        if output_raw_bytes:
            return response["result"]["transactionBytes"]
        if "transactionId" in response["result"]:
            return response["result"]["transactionId"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
