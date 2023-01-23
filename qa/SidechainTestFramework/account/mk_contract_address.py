import rlp
import logging
from eth_utils import keccak, to_checksum_address, to_bytes


def mk_contract_address(sender: str, nonce: int) -> str:
    """Create a contract address using eth-utils.

    # https://ethereum.stackexchange.com/a/761/620
    """
    sender_bytes = to_bytes(hexstr=sender)
    raw = rlp.encode([sender_bytes, nonce])
    h = keccak(raw)
    address_bytes = h[12:]
    return to_checksum_address(address_bytes)


if __name__ == "__main__":
    logging.info(
        to_checksum_address(mk_contract_address(to_checksum_address("0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0"), 1)))
    logging.info("0x343c43a37d37dff08ae8c4a11544c718abb4fcf8")
    assert mk_contract_address(to_checksum_address("0x6ac7ea33f8831ea9dcc53393aaa88b25a785dbf0"), 1) == \
           to_checksum_address("0x343c43a37d37dff08ae8c4a11544c718abb4fcf8")
