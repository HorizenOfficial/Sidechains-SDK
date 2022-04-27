import json


# execute a csw/getBlockIdToRollback call
def getBlockIdToRollback(sidechainNode):
    response = sidechainNode.csw_getSidechainBlockIdToRollback()
    return response