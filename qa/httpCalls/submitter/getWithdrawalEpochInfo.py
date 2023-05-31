def getWithdrawalEpochInfo(sidechainNode):
    response = sidechainNode.submitter_getWithdrawalEpochInfo()
    return response['result']