import json


# execute a backup/getSidechainBlockIdForBackup call
def getBlockIdForBackup(sidechainNode):
    response = sidechainNode.backup_getSidechainBlockIdForBackup()
    return response