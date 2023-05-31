import json


def showAllVotes(sidechain_node, address):
    j = {"address": address}
    request = json.dumps(j)
    response = sidechain_node.vote_showAllVotes(request)
    print(f'THIS IS THE RESPONSE TO SHOW ALL VOTES {response}')
    return response['result']['transactionBytes']