import json

def sendVoteMessage(sidechain_node, message_type, sender, receiver_sidechain, receiver, payload):
    j = {
        "messageType": message_type,
        "sender": sender,
        "receiverSidechain": receiver_sidechain,
        "receiver": receiver,
        "payload": payload
    }
    request = json.dumps(j)
    response = sidechain_node.vote_sendVoteMessage(request)
    return response['result']['transactionBytes']
