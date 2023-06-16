import json


def createRedeemMessage(sidechainNode, protocolVersion, messageType, senderSidechain, sender, receiverSidechain,
                        receiver, payload):
    message = {
        "protocolVersion": protocolVersion,
        "messageType": messageType,
        "senderSidechain": senderSidechain,
        "sender": sender,
        "receiverSidechain": receiverSidechain,
        "receiver": receiver,
        "payload": payload
    }
    j = {
        "message": message
    }
    body = json.dumps(j)
    response = sidechainNode.sc2sc_createRedeemMessage(body)
    return response
