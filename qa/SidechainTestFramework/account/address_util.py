from eth_utils import to_checksum_address


def format_evm(add: str):
    return to_checksum_address(add)


def format_eoa(add: str):
    if add.startswith('0x'):
        return add[2:].lower()
    else:
        return add.lower()
