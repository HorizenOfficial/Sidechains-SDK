import json


# executes a block/getFeePayments call
def http_block_getFeePayments(sidechain_node, block_id):
    j = {
        "blockId": block_id
    }
    request = json.dumps(j)
    response = sidechain_node.block_getFeePayments(request)
    return response["result"]
