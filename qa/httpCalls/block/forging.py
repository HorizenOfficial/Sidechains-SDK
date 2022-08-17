from requests import HTTPError


def http_start_forging(sidechainNode):
    try:
        response = sidechainNode.block_startForging()
        response.raise_for_status()
    except HTTPError:
        raise f"Error sending Start Forging HTTP Request: {HTTPError}"
    return response["result"]
