import json


def sendVoteMessageToSidechain(sidechainNode, proposition, vote, receivingSidechain, receivingAddress, fee):
    j = {
        "proposition": proposition,
        "vote": vote,
        "receivingSidechain": receivingSidechain,
        "receivingAddress": receivingAddress,
        "fee": fee
    }
    request = json.dumps(j)
    response = sidechainNode.vote_sendToSidechain(request)
    if "result" in response:
        return response["result"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
