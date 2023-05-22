import json


def redeemTransaction(sidechainNode, proposition, certificateDataHash, nextCertificateDataHash, scCommitmentTreeRoot,
           nextScCommitmentTreeRoot,
           proof, messageType, senderSidechain, sender, receiverSidechain, receiver, payload, fee):
    j = {
        "proposition": proposition,
        "certificateDataHash": certificateDataHash,
        "nextCertificateDataHash": nextCertificateDataHash,
        "scCommitmentTreeRoot": scCommitmentTreeRoot,
        "nextScCommitmentTreeRoot": nextScCommitmentTreeRoot,
        "proof": proof,
        "messageType": messageType,
        "senderSidechain": senderSidechain,
        "sender": sender,
        "receiverSidechain": receiverSidechain,
        "receiver": receiver,
        "payload": payload,
        "fee": fee
    }
    body = json.dumps(j)
    response = sidechainNode.vote_redeem(body)
    return response
