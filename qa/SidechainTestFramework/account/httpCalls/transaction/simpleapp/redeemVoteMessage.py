import json


def redeemVoteMessage(sidechain_node, message_type, sender_sidechain, sender, receiver_sidechain, receiver, payload,
                      certificate_data_hash, next_certificate_data_hash, sc_commitment_tree_root,
                      next_sc_commitment_tree_root, proof):
    j = {
        "messageType": message_type,
        "senderSidechain": sender_sidechain,
        "sender": sender,
        "receiverSidechain": receiver_sidechain,
        "receiver": receiver,
        "payload": payload,
        "certificateDataHash": certificate_data_hash,
        "nextCertificateDataHash": next_certificate_data_hash,
        "scCommitmentTreeRoot": sc_commitment_tree_root,
        "nextScCommitmentTreeRoot": next_sc_commitment_tree_root,
        "proof": proof
    }
    request = json.dumps(j)
    response = sidechain_node.vote_redeemVoteMessage(request)
    print(f'RESPONSE TO REDEEM VOTE: {response}')
    return response['result']['transactionBytes']
