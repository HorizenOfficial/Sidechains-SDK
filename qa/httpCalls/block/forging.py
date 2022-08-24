from requests import HTTPError


def http_start_forging(sidechain_node):
    response = sidechain_node.block_startForging()
    return response["result"]
