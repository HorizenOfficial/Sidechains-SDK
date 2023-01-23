import json


# executes a  block/findById call
def http_block_findById(sidechainNode, blockId):
    j = {
        "blockId": blockId
    }
    request = json.dumps(j)
    response = sidechainNode.block_findById(request)

    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
