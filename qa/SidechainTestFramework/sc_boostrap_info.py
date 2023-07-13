# default value for a large withdrawal epoch length; such value has impacts on RAM and HD usage
# Note: the maximum withdrawal_epoch_length allowed is around 900, otherwise snark keys size check will fail
# because of too complex circuit from MC perspective.
# LARGE_WITHDRAWAL_EPOCH_LENGTH = 148
LARGE_WITHDRAWAL_EPOCH_LENGTH = 900

SC_CREATION_VERSION_0 = 0
SC_CREATION_VERSION_1 = 1
SC_CREATION_VERSION_2 = 2

NO_KEY_ROTATION_CIRCUIT = 'NaiveThresholdSignatureCircuit'
KEY_ROTATION_CIRCUIT = 'NaiveThresholdSignatureCircuitWithKeyRotation'

DEFAULT_API_KEY = "TopSecret"

# Default value of max difference between tx nonce and state nonce allowed by mempool.
DEFAULT_MAX_NONCE_GAP = 16
# Default value of max number of slots a single account transactions can occupy
DEFAULT_MAX_ACCOUNT_SLOTS = 16
# Default value of max number of mempool slots transactions can occupy
DEFAULT_MAX_MEMPOOL_SLOTS = 6144
# Default value of max number of non exec sub slots transactions can occupy
DEFAULT_MAX_NONEXEC_POOL_SLOTS = 1024
# Default value of max time a tx can stay in the mempool waiting to be included in a block, in seconds
DEFAULT_TX_LIFETIME = 10800
"""
All information needed to bootstrap sidechain network within specified mainchain node.
The JSON representation is only for documentation.

SCCreationInfo: {
    "mc_node": Mainchain node
    "forward_amount": first Forward Transfer coin amount
    "withdrawal_epoch_length": length of Withdrawal Epoch
    "btr_data_length": size of scRequestData array for MBTRs. 0 if MBTRs are not supported at all.
    "sc_creation_version": sidechain version
    "cert_max_keys": defines the max number of Certificate proofs generation participants
    "cert_sig_threshold": the minimum set of the participants required for a valid proof creation
    "csw_enabled": true if the Ceased Sidechain Withdrawal should be enabled on the sidechain
    "circuit_type" is the type of circuit for certificate submitter
}
"""


class SCCreationInfo(object):

    # Note: the maximum withdrawal_epoch_length allowed is around 900, otherwise snark keys size check will fail
    # because of too complex circuit from MC perspective.
    def __init__(self, mc_node, forward_amount=100, withdrawal_epoch_length=LARGE_WITHDRAWAL_EPOCH_LENGTH,
                 btr_data_length=0, sc_creation_version=SC_CREATION_VERSION_1,
                 cert_max_keys=7, cert_sig_threshold=5, csw_enabled=False, is_non_ceasing=False,
                 circuit_type=NO_KEY_ROTATION_CIRCUIT):
        self.mc_node = mc_node
        self.forward_amount = forward_amount
        self.withdrawal_epoch_length = withdrawal_epoch_length
        self.btr_data_length = btr_data_length
        self.sc_creation_version = sc_creation_version
        self.cert_max_keys = cert_max_keys
        self.cert_sig_threshold = cert_sig_threshold
        self.csw_enabled = csw_enabled
        self.non_ceasing = is_non_ceasing

        if csw_enabled and is_non_ceasing:
            raise RuntimeError('Cannot enable CSW and Non-ceasing options simultaneously.')

        if sc_creation_version != SC_CREATION_VERSION_2 and is_non_ceasing:
            raise RuntimeError('Cannot initialize non-ceasing sidechain with version different '
                               'from ' + str(SC_CREATION_VERSION_2) + '. Found ' + str(sc_creation_version))
        self.circuit_type = circuit_type


"""
Sidechain websocket configuration to be added inside the configuration file.
The JSON representation is only for documentation.

MCConnectionInfo: {
    "address":
    "connectionTimeout":
    "reconnectionDelay":
    "reconnectionMaxAttempts":
}
"""


class MCConnectionInfo(object):

    def __init__(self, address="ws://localhost:8888", connectionTimeout=100, reconnectionDelay=1,
                 reconnectionMaxAttempts=1):
        self.address = address
        self.connectionTimeout = connectionTimeout
        self.reconnectionDelay = reconnectionDelay
        self.reconnectionMaxAttempts = reconnectionMaxAttempts


"""
Configuration that enables the possibility to restrict the forging phase
 to a specific list of forgers.
"""


class SCForgerConfiguration(object):
    def __init__(self, restrict_forgers=False, allowed_forgers=[]):
        self.restrict_forgers = restrict_forgers
        self.allowed_forgers = []
        for forger in allowed_forgers:
            self.allowed_forgers.append(
                '{ blockSignProposition = "' + forger[0] + '" NEW_LINE vrfPublicKey = "' + forger[1] + '" }')


"""
Information needed to start a sidechain node connected to specific mainchain node.
The JSON representation is only for documentation.

SCNodeConfiguration: {
    "mc_connection_info":{
        "address":
        "connectionTimeout":
        "reconnectionDelay":
        "reconnectionMaxAttempts":
    }
}
"""


class SCNodeConfiguration(object):

    # Currently we have Cert Signature threshold snark proof with the max PK number = 7
    def __init__(self,
                 mc_connection_info=MCConnectionInfo(),
                 cert_submitter_enabled=True,
                 cert_signing_enabled=True,
                 submitter_private_keys_indexes=None,
                 max_incoming_connections=100,
                 max_outgoing_connections=100,
                 get_peers_interval="2m",
                 automatic_fee_computation=True,
                 certificate_fee=0.0001,
                 forger_options=SCForgerConfiguration(),
                 mempool_max_size=300,
                 mempool_min_fee_rate=0,
                 api_key=DEFAULT_API_KEY,
                 max_fee=10000000,
                 initial_private_keys=[],
                 remote_keys_manager_enabled=False,
                 remote_keys_server_address=None,
                 known_peers=[],
                 declared_address=None,
                 initial_signing_private_keys=[],
                 storage_backup_interval='15m',
                 storage_backup_delay='5m',
                 websocket_server_enabled=False,
                 websocket_server_port=0,
                 allow_unprotected_txs=True,
                 max_nonce_gap=DEFAULT_MAX_NONCE_GAP,
                 max_account_slots=DEFAULT_MAX_ACCOUNT_SLOTS,
                 max_mempool_slots=DEFAULT_MAX_MEMPOOL_SLOTS,
                 max_nonexec_pool_slots=DEFAULT_MAX_NONEXEC_POOL_SLOTS,
                 tx_lifetime=DEFAULT_TX_LIFETIME,
                 handling_txs_enabled=True,
                 magic_bytes=[12, 34, 56, 78],
                 sc2sc_proving_key_file_path=None,
                 sc2sc_verification_key_file_path=None
                 ):
        if submitter_private_keys_indexes is None:
            submitter_private_keys_indexes = list(range(7))
        self.mc_connection_info = mc_connection_info
        self.cert_submitter_enabled = cert_submitter_enabled
        self.cert_signing_enabled = cert_signing_enabled
        self.submitter_private_keys_indexes = submitter_private_keys_indexes
        self.max_incoming_connections = max_incoming_connections
        self.max_outgoing_connections = max_outgoing_connections
        self.get_peers_interval = get_peers_interval
        self.automatic_fee_computation = automatic_fee_computation
        self.certificate_fee = certificate_fee
        self.forger_options = forger_options
        self.api_key = api_key
        self.max_fee = max_fee
        self.mempool_max_size = mempool_max_size
        self.mempool_min_fee_rate = mempool_min_fee_rate
        self.initial_private_keys = initial_private_keys
        self.initial_signing_private_keys = initial_signing_private_keys
        self.remote_keys_manager_enabled = remote_keys_manager_enabled
        if remote_keys_manager_enabled:
            self.remote_keys_server_address = remote_keys_server_address
        self.known_peers = known_peers
        if declared_address is not None:
            self.declared_address = declared_address
        self.storage_backup_interval = storage_backup_interval
        self.storage_backup_delay = storage_backup_delay
        self.websocket_server_enabled = websocket_server_enabled
        self.websocket_server_port = websocket_server_port
        self.allow_unprotected_txs = allow_unprotected_txs
        self.max_nonce_gap = max_nonce_gap
        self.max_account_slots = max_account_slots
        self.max_mempool_slots = max_mempool_slots
        self.max_nonexec_pool_slots = max_nonexec_pool_slots
        self.tx_lifetime = tx_lifetime
        self.handling_txs_enabled=handling_txs_enabled
        self.magic_bytes = magic_bytes
        self.sc2sc_proving_key_file_path = sc2sc_proving_key_file_path
        self.sc2sc_verification_key_file_path = sc2sc_verification_key_file_path

    def update_websocket_config(self, websocket_server_enabled, websocket_server_port):
        self.websocket_server_enabled = websocket_server_enabled
        self.websocket_server_port = websocket_server_port


"""
The full network of many sidechain nodes connected to many mainchain nodes.
The JSON representation is only for documentation.

SCNetworkConfiguration: {
    SCCreationInfo, 
    [
        SCNodeConfiguration_0
        ...
        SCNodeConfiguration_i
    ]
}
"""



class SCNetworkConfiguration(object):
    def __init__(self, sc_creation_info, *sc_nodes_configuration):
        self.sc_creation_info = sc_creation_info
        self.sc_nodes_configuration = sc_nodes_configuration


# class SCMultiNetworkConfiguration(SCNetworkConfiguration):
#
#     def __init__(self, sc_creation_info, sc_nodes_configuration):
#         self.sc_creation_info = sc_creation_info
#         self.sc_nodes_configuration = sc_nodes_configuration


"""
An account.
The JSON representation is only for documentation.

Account: {
    "secret":
    "publicKey": "a public key"
}
"""


class Account(object):

    def __init__(self, secret, publicKey):
        self.secret = secret
        self.publicKey = publicKey


"""
A Vrf key.
The JSON representation is only for documentation.

VrfAccount : {
    "vrfSecret":
    "vrfPublicKey": "a public key"
}
"""


class VrfAccount(object):
    def __init__(self, secret, publicKey):
        self.secret = secret
        self.publicKey = publicKey

class AccountKey(object):
    def __init__(self, secret, proposition):
        self.secret = secret
        self.proposition = proposition

"""
A Schnorr key.
The JSON representation is only for documentation.

SchnorrAccount : {
    "schnorrSecret":
    "schnorrPublicKey": "a public key"
}
"""


class SchnorrAccount(object):

    def __init__(self, secret, publicKey):
        self.secret = secret
        self.publicKey = publicKey


"""
Withdrawal certificate proof info  data .
The JSON representation is only for documentation.

CertificateProofInfo : {
    "threshold":
    "genSysConstant":"5e7b..0000"
    "verificationKey":"caa..000"
    "schnorr_signers_secrets": certificate signer secret keys in byte hex representation
    "schnorr_masters_secrets": certificate master keys in byte hex representation (only for key rotation circuit)
    "public_signing_keys": public keys in byte hex representation
    "public_master_keys": public keys in byte hex representation (only for key rotation circuit)
}
"""


class CertificateProofInfo(object):

    def __init__(self, threshold, genSysConstant, verificationKey, schnorr_signers_secrets=[], public_signing_keys=[],
                 schnorr_masters_secrets=[], public_master_keys=[]):
        self.threshold = threshold
        self.genSysConstant = genSysConstant
        self.verificationKey = verificationKey
        self.schnorr_signers_secrets = schnorr_signers_secrets
        self.schnorr_masters_secrets = schnorr_masters_secrets
        self.public_signing_keys = public_signing_keys
        self.public_master_keys = public_master_keys


"""
Information about sidechain network already bootstrapped.
The JSON representation is only for documentation.

SCBootstrapInfo: {
    "sidechain_id":
    "genesis_account": an instance of Account
    "genesis_account_balance":
    "mainchain_block_height": the height of the mainchain block at which the sidechain has been created (useful for future checks of mainchain block reference inclusion)
    "sidechain_genesis_block_hex":
    "pow_data":
    "network":
    "withdrawal_epoch_length":
    "genesis_vrf_account": an instance of VrfAccount
    "certificate_proof_info": an instance of CertificateProofInfo
    "initial_cumulative_comm_tree_hash": CommTreeHash data for the genesis MC block
    "cert_keys_paths": an instance of ProofKeysPaths for certificate
    "csw_keys_paths": an instance of ProofKeysPaths for ceased sidechain withdrawal
    "genesis_evm_account": an instance of Account for EVM Sidechain
}
"""
class SCBootstrapInfo(object):

    def __init__(self, sidechain_id, genesis_account, genesis_account_balance, mainchain_block_height,
                 sidechain_genesis_block_hex, pow_data, network, withdrawal_epoch_length, genesis_vrf_account,
                 certificate_proof_info, initial_cumulative_comm_tree_hash, is_non_ceasing, cert_keys_paths, csw_keys_paths,
                 genesis_evm_account, circuit_type):
        self.sidechain_id = sidechain_id
        self.genesis_account = genesis_account
        self.genesis_account_balance = genesis_account_balance
        self.mainchain_block_height = mainchain_block_height
        self.sidechain_genesis_block_hex = sidechain_genesis_block_hex
        self.pow_data = pow_data
        self.network = network
        self.is_non_ceasing = is_non_ceasing
        self.withdrawal_epoch_length = withdrawal_epoch_length
        self.genesis_vrf_account = genesis_vrf_account
        self.certificate_proof_info = certificate_proof_info
        self.initial_cumulative_comm_tree_hash = initial_cumulative_comm_tree_hash
        self.cert_keys_paths = cert_keys_paths
        self.csw_keys_paths = csw_keys_paths
        self.genesis_evm_account = genesis_evm_account
        self.circuit_type = circuit_type


class ProofKeysPaths(object):

    def __init__(self, proving_key_path, verification_key_path):
        self.proving_key_path = proving_key_path
        self.verification_key_path = verification_key_path
