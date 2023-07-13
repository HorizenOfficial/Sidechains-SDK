import json


def createAccountRedeemMessage(sidechainNode, messageType, sender, receiverSidechain, receiver, payload, scId):
    message = {
        "messageType": messageType,
        "sender": sender,
        "receiverSidechain": receiverSidechain,
        "receiver": receiver,
        "payload": payload
    }
    j = {
        "message": message,
        "scId": scId
    }
    body = json.dumps(j)
    response = sidechainNode.sc2sc_createAccountRedeemMessage(body)
    print(response)
    return response
