import json


# execute a transaction/sendTransaction call
def http_send_transaction(sidechainNode, byte_array, api_key=None):
    request = json.dumps(byte_array)
    if (api_key != None):
        response = sidechainNode.transaction_sendTransaction(request, api_key)
    else:
        response = sidechainNode.transaction_sendTransaction(request)
    return response["result"]