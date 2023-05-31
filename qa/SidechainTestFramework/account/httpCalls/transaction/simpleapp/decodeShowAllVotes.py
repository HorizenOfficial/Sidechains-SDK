import json


def decodeShowAllVotes(sidechain_node, bytes):
    j = {"byteMessage": bytes}
    request = json.dumps(j)
    response = sidechain_node.vote_decodeShowAllVotes(request)
    return response['result']['transactionBytes']