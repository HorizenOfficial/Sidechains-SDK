import json


# executes a wallet/allBoxes call for a given box type
def http_wallet_allBoxesOfType(sidechain_node, box_type, api_key=None):
    j = {
        "boxTypeClass": box_type
    }
    request = json.dumps(j)
    if api_key is not None:
        response = sidechain_node.wallet_allBoxes(request, api_key)
    else:
        response = sidechain_node.wallet_allBoxes(request)
    return response['result']['boxes']
