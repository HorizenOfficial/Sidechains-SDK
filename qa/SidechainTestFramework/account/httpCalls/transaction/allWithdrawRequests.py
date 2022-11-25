import json


# execute a transaction/allWithdrawalRequests call on account sidechain
def all_withdrawal_requests(sidechain_node, epoch_number):
    j = {
        "epochNum": epoch_number
    }
    request = json.dumps(j)
    response = sidechain_node.transaction_allWithdrawalRequests(request)

    if "result" in response:
        return response["result"]["listOfWR"]

    raise RuntimeError("Something went wrong, see {}".format(str(response)))
