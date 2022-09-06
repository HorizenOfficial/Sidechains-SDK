import json


# execute a transaction/allWithdrawalRequests call on account sidechain
def all_withdrawal_requests(sidechain_node, epoch_number):
    j = {
        "epochNum": epoch_number,

    }
    request = json.dumps(j)
    response = sidechain_node.transaction_allWithdrawalRequests(request)
    return response["result"]
