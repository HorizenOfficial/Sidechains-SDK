import json

def showAllVotesByAddress(sidechain_node, user_address, smart_contract_address):
    j = {
        "userAddress": user_address,
        "smartContractAddress": smart_contract_address
    }
    request = json.dumps(j)
    response = sidechain_node.sc2sc_getAllRedeemedMessagesByAddress(request)
    print(f'THE RESPONSE IS : {response}')
    return response['result']['redeemedMessages']
