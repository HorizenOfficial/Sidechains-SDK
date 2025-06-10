import logging
import os
import sys
import json
import subprocess
import time
import socket
from contextlib import closing
from decimal import Decimal
from SidechainTestFramework.sc_boostrap_info import MCConnectionInfo, SCBootstrapInfo, SCNetworkConfiguration, Account, \
    AccountKey, VrfAccount, SchnorrAccount, CertificateProofInfo, SCNodeConfiguration, ProofKeysPaths, \
    LARGE_WITHDRAWAL_EPOCH_LENGTH, DEFAULT_API_KEY, KEY_ROTATION_CIRCUIT, \
    NO_KEY_ROTATION_CIRCUIT

from SidechainTestFramework.sidechainauthproxy import SidechainAuthServiceProxy
from test_framework.mc_test.mc_test import generate_random_field_element_hex
from test_framework.util import initialize_new_sidechain_in_mainchain, get_spendable, swap_bytes, assert_equal, \
    assert_false, get_field_element_with_padding

WAIT_CONST = 1

SNAPSHOT_VERSION_TAG = "0.13.0-RC5"

# log levels of the log4j trace system used by java applications
APP_LEVEL_OFF = "off"
APP_LEVEL_FATAL = "fatal"
APP_LEVEL_ERROR = "error"
APP_LEVEL_WARN = "warn"
APP_LEVEL_INFO = "info"
APP_LEVEL_DEBUG = "debug"
APP_LEVEL_TRACE = "trace"
APP_LEVEL_ALL = "all"

# log levels of the python logging trace system
TEST_LEVEL_NOTSET = "notset"
TEST_LEVEL_FATAL = "fatal"
TEST_LEVEL_ERROR = "error"
TEST_LEVEL_WARN = "warn"
TEST_LEVEL_INFO = "info"
TEST_LEVEL_DEBUG = "debug"


# timeout in secs for rest api
DEFAULT_REST_API_TIMEOUT = 5

# max P2P message size for a Modifier
DEFAULT_MAX_PACKET_SIZE = 5*1024*1024+100
DEFAULT_ACCOUNT_MODEL_MAX_PACKET_SIZE = 7*1024*1024+100

SLOTS_IN_EPOCH = 720
SIMPLE_APP_SLOT_TIME = 120  # seconds
EVM_APP_SLOT_TIME = 12  # seconds

DEFAULT_SIMPLE_APP_GENESIS_TIMESTAMP_REWIND = SLOTS_IN_EPOCH * SIMPLE_APP_SLOT_TIME * 5  # 5 epochs
DEFAULT_EVM_APP_GENESIS_TIMESTAMP_REWIND = SLOTS_IN_EPOCH * EVM_APP_SLOT_TIME * 5  # 5 epochs

# Parallel Testing
parallel_test = 0

# flag for jacoco code coverage analysis
is_jacoco_included = False


class TimeoutException(Exception):
    def __init__(self, operation):
        Exception.__init__(self)
        self.operation = operation


class LogInfo(object):
    def __init__(self, logFileLevel=APP_LEVEL_ALL, logConsoleLevel=APP_LEVEL_ERROR):
        self.logFileLevel = logFileLevel
        self.logConsoleLevel = logConsoleLevel


def set_sc_parallel_test(n):
    global parallel_test
    parallel_test = n

def set_jacoco(value):
    global is_jacoco_included
    is_jacoco_included = value

def start_port_modifier():
    if parallel_test > 0:
        # Adjust this multiplier if port clashing due to many nodes
        return (parallel_test - 1) * 20


def sc_p2p_port(n):
    start_port = 8300
    if parallel_test > 0:
        start_port = 8500 + start_port_modifier()
        return start_port + n
    else:
        return start_port + n + os.getpid() % 999


def sc_rpc_port(n):
    start_port = 8200
    if parallel_test > 0:
        start_port += start_port_modifier()
        return start_port + n
    else:
        return start_port + n + os.getpid() % 999


# To be removed
def wait_for_next_sc_blocks(node, expected_height, wait_for=25):
    """
    Wait until blockchain height won't reach the expected_height, for wait_for seconds
    """
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise TimeoutException("Waiting blocks")
        height = int(node.block_best()["result"]["height"])
        if height >= expected_height:
            break
        time.sleep(WAIT_CONST)


def wait_for_sc_node_initialization(nodes):
    """
    Wait for SC Nodes to be fully initialized. This is done by pinging a node until its socket will be fully open
    """
    for i in range(len(nodes)):
        rpc_port = sc_rpc_port(i)
        with closing(socket.socket(socket.AF_INET, socket.SOCK_STREAM)) as sock:
            while not sock.connect_ex(("127.0.0.1", rpc_port)) == 0:
                time.sleep(WAIT_CONST)


def sync_sc_blocks(api_connections, wait_for=25, p=False):
    """
    Wait for maximum wait_for seconds for everybody to have the same block count
    """
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise TimeoutException("Syncing blocks")
        counts = [int(x.block_currentHeight()["result"]["height"]) for x in api_connections]
        if p:
            logging.info(counts)
        if counts == [counts[0]] * len(counts):
            break
        time.sleep(WAIT_CONST)


def sync_sc_mempools(api_connections, wait_for=25, mempool_cardinality_only=False):
    """
    Wait for maximum wait_for seconds for everybody to have the same transactions in their memory pools
    """
    if mempool_cardinality_only:
        format = False
        tag = "transactionIds"
    else:
        format = True
        tag = "transactions"


    j = {"format": format}
    request = json.dumps(j)

    start = time.time()
    while True:
        refpool = api_connections[0].transaction_allTransactions(request)["result"][tag]
        if time.time() - start >= wait_for:
            raise TimeoutException("Syncing mempools")
        num_match = 1
        for i in range(1, len(api_connections)):
            nodepool = api_connections[i].transaction_allTransactions(request)["result"][tag]
            if nodepool == refpool:
                num_match = num_match + 1
        if num_match == len(api_connections):
            break
        time.sleep(WAIT_CONST)


sidechainclient_processes = {}


def launch_bootstrap_tool(command_name, json_parameters, model):
    bootstrapping_tool_path = EVM_BOOTSTRAPPING_TOOL if model == 'account' else UTXO_BOOTSTRAPPING_TOOL
    json_param = json.dumps(json_parameters)
    java_ps = subprocess.Popen(["java", "-jar", bootstrapping_tool_path,
                                command_name, json_param], stdout=subprocess.PIPE)
    sc_bootstrap_output = java_ps.communicate()[0]
    try:
        jsone_node = json.loads(sc_bootstrap_output)
        return jsone_node
    except ValueError as e:
        logging.info("Bootstrap tool error occurred for command= {}\nparams: {}\nError: {}\nException: {}\n"
                     .format(command_name, json_param, sc_bootstrap_output.decode(), str(e)))
        raise Exception("Bootstrap tool error occurred")


def launch_db_tool(dirName, storageNames, command_name, json_parameters):
    '''
    we use "blockchain" postfix for specifying the dataDir (see qa/resources/template.conf:
        dataDir = "%(DIRECTORY)s/sc_node%(NODE_NUMBER)s/blockchain"
    '''
    storagesPath = dirName + "/blockchain"

    json_param = json.dumps(json_parameters)
    java_ps = subprocess.Popen(["java", "-jar",
                                os.getenv("SIDECHAIN_SDK",
                                          "..") + "/tools/dbtool/target/sidechains-sdk-dbtools-"+SNAPSHOT_VERSION_TAG+".jar",
                                storagesPath, storageNames, command_name, json_param], stdout=subprocess.PIPE)
    db_tool_output = java_ps.communicate()[0]
    try:
        jsone_node = json.loads(db_tool_output)
        return jsone_node
    except ValueError:
        logging.info("DB tool error occurred for command= {}\nparams: {}\nError: {}\n"
                     .format(command_name, json_param, db_tool_output.decode()))
        raise Exception("DB tool error occurred")


"""
Generate a genesis info by calling ScBootstrappingTool with command "genesisinfo"
Parameters:
 - genesis_info: genesis info provided by a mainchain node
 - genesis_secret: private key 25519 secret to sign SC block
 - vrf_secret: vrk secret key to check consensus rules SC block
 - block_timestamp_rewind: rewind genesis block timestamp by some value
 - virtual_withdrawal_epoch_length - actual withdrawal epoch length from SC perspective in case of non-ceasing sidechain
    Note: must be undefined or 0 in case of ceasing sidechain; >0 in case of non-ceasing sidechain.
 
Output: a JSON object to be included in the settings file of the sidechain node nth.
{
    "scId": "id of the sidechain node",
    "scGenesisBlockHex": "some value",
    "powData": "some value",
    "mcBlockHeight": xxx,
    "mcNetwork": regtest|testnet|mainnet
    "withdrawalEpochLength": xxx
    "initialCumulativeCommTreeHash": xxx
}
"""


def generate_genesis_data(genesis_info, genesis_secret, vrf_secret, block_timestamp_rewind, model, virtual_withdrawal_epoch_length):
    jsonParameters = {"model": model,
                      "secret": genesis_secret, "vrfSecret": vrf_secret, "info": genesis_info,
                      "regtestBlockTimestampRewind": block_timestamp_rewind, "virtualWithdrawalEpochLength": virtual_withdrawal_epoch_length}
    jsonNode = launch_bootstrap_tool("genesisinfo", jsonParameters, model)
    return jsonNode


"""
Generate secrets by calling ScBootstrappingTools with command "generatekey"
Parameters:
 - seed
 - number_of_accounts: the number of keys to be generated
 
Output: an array of instances of Account (see sc_bootstrap_info.py).
"""


def generate_secrets(seed, number_of_accounts, model):
    accounts = []
    secrets = []
    for i in range(number_of_accounts):
        jsonParameters = {"seed": "{0}_{1}".format(seed, i + 1)}
        secrets.append(launch_bootstrap_tool("generatekey", jsonParameters, model))

    for i in range(len(secrets)):
        secret = secrets[i]
        accounts.append(Account(secret["secret"], secret["publicKey"]))
    return accounts


"""
Generate Vrf keys by calling ScBootstrappingTools with command "generateVrfKey"
Parameters:
 - seed
 - number_of_accounts: the number of keys to be generated

Output: an array of instances of VrfKey (see sc_bootstrap_info.py).
"""


def generate_vrf_secrets(seed, number_of_vrf_keys, model):
    vrf_keys = []
    secrets = []
    for i in range(number_of_vrf_keys):
        jsonParameters = {"seed": "{0}_{1}".format(seed, i + 1)}
        secrets.append(launch_bootstrap_tool("generateVrfKey", jsonParameters, model))

    for i in range(len(secrets)):
        secret = secrets[i]
        vrf_keys.append(VrfAccount(secret["vrfSecret"], secret["vrfPublicKey"]))
    return vrf_keys


def generate_account_proposition(seed, number_of_acc_props, model):
    acc_props = []
    secrets = []
    for i in range(number_of_acc_props):
        jsonParameters = {"seed": "{0}_{1}".format(seed, i + 1)}
        secrets.append(launch_bootstrap_tool("generateAccountKey", jsonParameters, model))

    for i in range(len(secrets)):
        secret = secrets[i]
        acc_props.append(AccountKey(secret["accountSecret"], secret["accountProposition"]))
    return acc_props

"""
Generate Schnorr keys by calling ScBootstrappingTools with command "generateCertificateSignerKey"
Parameters:
 - seed
 - number_of_accounts: the number of keys to be generated

Output: an array of instances of SchnorrKey (see sc_bootstrap_info.py).
"""
def generate_cert_signer_secrets(seed, number_of_schnorr_keys, model):
    schnorr_keys = []
    secrets = []
    for i in range(number_of_schnorr_keys):
        jsonParameters = {"seed": "{0}_{1}".format(seed, i + 1)}
        secrets.append(launch_bootstrap_tool("generateCertificateSignerKey", jsonParameters, model))

    for i in range(len(secrets)):
        secret = secrets[i]
        schnorr_keys.append(SchnorrAccount(secret["signerSecret"], secret["signerPublicKey"]))
    return schnorr_keys


# Maybe should we give the possibility to customize the configuration file by adding more fields ?

"""
Generate withdrawal certificate proof info calling ScBootstrappingTools with command "generateProofInfo"
Parameters:
 - seed
 - number_of_signer_keys: the number of schnorr keys to be generated
 - threshold: the minimum set of the participants required for a valid proof creation
 - keys_paths: instance of ProofKeysPaths. Contains paths to load/generate Coboundary Marlin snark keys
 - isCSWEnabled: if ceased sidechain withdrawal is enabled or not

Output: CertificateProofInfo (see sc_bootstrap_info.py).
"""


def generate_certificate_proof_info(seed, number_of_signer_keys, threshold, keys_paths,
                                    is_csw_enabled, circuit_type, model):
    signer_keys = generate_cert_signer_secrets(seed, number_of_signer_keys, model)

    signer_secrets = []
    public_signing_keys = []
    master_secrets = []
    public_master_keys = []
    for i in range(len(signer_keys)):
        signer_key = signer_keys[i]
        signer_secrets.append(signer_key.secret)
        public_signing_keys.append(signer_key.publicKey)

    json_parameters = {
        "signersPublicKeys": public_signing_keys,
        "threshold": threshold,
        "provingKeyPath": keys_paths.proving_key_path,
        "verificationKeyPath": keys_paths.verification_key_path,
        "isCSWEnabled": is_csw_enabled
    }

    if circuit_type == KEY_ROTATION_CIRCUIT:
        master_keys = generate_cert_signer_secrets("master" + seed, number_of_signer_keys, model)
        for i in range((len(master_keys))):
            master_key = master_keys[i]
            master_secrets.append(master_key.secret)
            public_master_keys.append(master_key.publicKey)

        json_parameters["mastersPublicKeys"] = public_master_keys

    output = launch_bootstrap_tool("generateCertProofInfo",
                                   json_parameters, model) if circuit_type == NO_KEY_ROTATION_CIRCUIT else \
        launch_bootstrap_tool("generateCertWithKeyRotationProofInfo", json_parameters, model)

    threshold = output["threshold"]
    verification_key = output["verificationKey"]
    gen_sys_constant = output["genSysConstant"]

    certificate_proof_info = CertificateProofInfo(threshold, gen_sys_constant, verification_key, signer_secrets,
                                                  public_signing_keys, master_secrets, public_master_keys)
    return certificate_proof_info


"""
return a string like '["127.0.0.1:xxxx","127.0.0.1:xxxx"]' to be set as known Peers in the configuration.
Based on a index vector [0, 2, 3] where all peers could be [0,1,2,3,4]

Parameters:
 - known_peers_indexes: indexes of the known peers
"""


def get_known_peers(known_peers_indexes):
    addresses = []
    for index in known_peers_indexes:
        addresses.append("\"" + ("127.0.0.1:" + str(sc_p2p_port(index))) + "\"")
    peers = "[" + ",".join(addresses) + "]"
    return peers


"""
Generate ceased sidechain withdrawal proof info calling ScBootstrappingTools with command "generateCswProofInfo"
Parameters:
 - withdrawalEpochLen
 - keys_paths - instance of ProofKeysPaths. Contains paths to load/generate Coboundary Marlin snark keys

Output: Verification key
"""


def generate_csw_proof_info(withdrawal_epoch_len, keys_paths, model):
    json_parameters = {
        "withdrawalEpochLen": withdrawal_epoch_len,
        "provingKeyPath": keys_paths.proving_key_path,
        "verificationKeyPath": keys_paths.verification_key_path
    }
    output = launch_bootstrap_tool("generateCswProofInfo", json_parameters, model)

    verification_key = output["verificationKey"]
    return verification_key


"""
Create directories for each node and configuration files inside them.
For each node put also genesis data in configuration files.

Parameters:
 - dirname: directory name
 - n: sidechain node nth
 - bootstrap_info: an instance of SCBootstrapInfo (see sc_bootstrap_info.py)
 - websocket_config: an instance of MCConnectionInfo (see sc_boostrap_info.py)
"""


def initialize_sc_datadir(dirname, n, model, bootstrap_info=SCBootstrapInfo, sc_node_config=SCNodeConfiguration(),
                          log_info=LogInfo(), rest_api_timeout=DEFAULT_REST_API_TIMEOUT):
    apiAddress = "127.0.0.1"
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    mc0datadir = os.path.join(dirname, "node0")
    websocket_config = sc_node_config.mc_connection_info
    if not os.path.isdir(datadir):
        os.makedirs(datadir)
    resourcesDir = get_resources_dir()
    customFileName = resourcesDir + '/template_' + str(n + 1) + '.conf'
    fileToOpen = resourcesDir + '/template.conf'
    if os.path.isfile(customFileName):
        fileToOpen = customFileName

    with open(fileToOpen, 'r') as templateFile:
        tmpConfig = templateFile.read()

    genesis_secrets = []
    if bootstrap_info.genesis_vrf_account is not None:
        genesis_secrets.append(bootstrap_info.genesis_vrf_account.secret)

    if bootstrap_info.genesis_account is not None:
        genesis_secrets.append(bootstrap_info.genesis_account.secret)

    if bootstrap_info.genesis_evm_account is not None:
        genesis_secrets.append(bootstrap_info.genesis_evm_account.secret)

    all_signers_private_keys = bootstrap_info.certificate_proof_info.schnorr_signers_secrets
    signer_private_keys = [all_signers_private_keys[idx] for idx in sc_node_config.submitter_private_keys_indexes]
    api_key_hash = ""
    if sc_node_config.api_key != "":
        api_key_hash = calculateApiKeyHash(sc_node_config.api_key, model)

    if bootstrap_info.genesis_account is not None:
        # we choose to tell the secrtes only to bootstrapped node 0
        genesis_secrets += sc_node_config.initial_private_keys

    if (sc_node_config.forger_options.restrict_forgers and
        bootstrap_info.genesis_vrf_account is not None and
        bootstrap_info.genesis_account is not None):
        sc_node_config.forger_options.allowed_forgers.append(
            '{ blockSignProposition = "' + bootstrap_info.genesis_account.publicKey + '" NEW_LINE vrfPublicKey = "' + bootstrap_info.genesis_vrf_account.publicKey + '" }')

    if model == AccountModel:
        max_modifiers_spec_message_size = DEFAULT_ACCOUNT_MODEL_MAX_PACKET_SIZE
    else:
        max_modifiers_spec_message_size = DEFAULT_MAX_PACKET_SIZE

    config = tmpConfig % {
        'NODE_NUMBER': n,
        'DIRECTORY': dirname,
        'LOG_FILE_LEVEL': log_info.logFileLevel,
        'LOG_CONSOLE_LEVEL': log_info.logConsoleLevel,
        'WALLET_SEED': "sidechain_seed_{0}".format(n),
        'API_ADDRESS': "127.0.0.1",
        'API_PORT': str(apiPort),
        'API_KEY_HASH': f'apiKeyHash = "{api_key_hash}"' if (api_key_hash != "") else '\r',
        'API_TIMEOUT': (str(rest_api_timeout) + "s"),
        'BIND_PORT': str(bindPort),
        'MAX_INCOMING_CONNECTIONS': sc_node_config.max_incoming_connections,
        'MAX_OUTGOING_CONNECTIONS': sc_node_config.max_outgoing_connections,
        'GET_PEERS_INTERVAL': sc_node_config.get_peers_interval,
        'DECLARED_ADDRESS': f'declaredAddress = "{sc_node_config.declared_address}"' if hasattr(sc_node_config, 'declared_address') else "",
        'KNOWN_PEERS': json.dumps(sc_node_config.known_peers),
        'STORAGE_BACKUP_INTERVAL': json.dumps(sc_node_config.storage_backup_interval),
        'STORAGE_BACKUP_DELAY': json.dumps(sc_node_config.storage_backup_delay),
        'ALLOW_UNPROTECTED_TXS': json.dumps(sc_node_config.allow_unprotected_txs),
        'OFFLINE_GENERATION': "false",
        'GENESIS_SECRETS': json.dumps(genesis_secrets),
        'MAX_TX_FEE': sc_node_config.max_fee,
        'MEMPOOL_MAX_SIZE': sc_node_config.mempool_max_size,
        'MEMPOOL_MIN_FEE_RATE': sc_node_config.mempool_min_fee_rate,
        'SIDECHAIN_ID': bootstrap_info.sidechain_id,
        'GENESIS_DATA': bootstrap_info.sidechain_genesis_block_hex,
        'POW_DATA': bootstrap_info.pow_data,
        'BLOCK_HEIGHT': bootstrap_info.mainchain_block_height,
        'NETWORK': bootstrap_info.network,
        'NON_CEASING': ("true" if bootstrap_info.is_non_ceasing else "false"),
        'WITHDRAWAL_EPOCH_LENGTH': bootstrap_info.withdrawal_epoch_length,
        'INITIAL_COMM_TREE_CUMULATIVE_HASH': bootstrap_info.initial_cumulative_comm_tree_hash,
        'WEBSOCKET_ADDRESS': websocket_config.address,
        'CONNECTION_TIMEOUT': websocket_config.connectionTimeout,
        'RECONNECTION_DELAY': websocket_config.reconnectionDelay,
        'RECONNECTION_MAX_ATTEMPTS': websocket_config.reconnectionMaxAttempts,
        'WEBSOCKET_SERVER_ENABLED': "true" if sc_node_config.websocket_server_enabled else "false",
        'WEBSOCKET_SERVER_PORT': sc_node_config.websocket_server_port,
        "THRESHOLD": bootstrap_info.certificate_proof_info.threshold,
        "SUBMITTER_CERTIFICATE": ("true" if sc_node_config.cert_submitter_enabled else "false"),
        "CERTIFICATE_SIGNING": ("true" if sc_node_config.cert_signing_enabled else "false"),
        "SIGNER_PUBLIC_KEY": json.dumps(bootstrap_info.certificate_proof_info.public_signing_keys),
        "SIGNER_PRIVATE_KEY": json.dumps(signer_private_keys),
        "MASTER_PUBLIC_KEY": json.dumps(bootstrap_info.certificate_proof_info.public_master_keys),
        # This should be NON empty only in case of Key Rotation Circuit
        "MAX_PKS": len(bootstrap_info.certificate_proof_info.public_signing_keys),
        "CERT_PROVING_KEY_PATH": bootstrap_info.cert_keys_paths.proving_key_path,
        "CERT_VERIFICATION_KEY_PATH": bootstrap_info.cert_keys_paths.verification_key_path,
        "AUTOMATIC_FEE_COMPUTATION": ("true" if sc_node_config.automatic_fee_computation else "false"),
        "CERTIFICATE_FEE": sc_node_config.certificate_fee,
        "CSW_PROVING_KEY_PATH": bootstrap_info.csw_keys_paths.proving_key_path if bootstrap_info.csw_keys_paths is not None else "",
        "CSW_VERIFICATION_KEY_PATH": bootstrap_info.csw_keys_paths.verification_key_path if bootstrap_info.csw_keys_paths is not None else "",
        "RESTRICT_FORGERS": ("true" if sc_node_config.forger_options.restrict_forgers else "false"),
        "ALLOWED_FORGERS_LIST": sc_node_config.forger_options.allowed_forgers,
        "MAX_MODIFIERS_SPEC_MESSAGE_SIZE": int(max_modifiers_spec_message_size),
        "CIRCUIT_TYPE": bootstrap_info.circuit_type,
        "REMOTE_KEY_MANAGER_ENABLED": ("true" if sc_node_config.remote_keys_manager_enabled else "false"),
        "REMOTE_SERVER_ADDRESS": (sc_node_config.remote_keys_server_address if sc_node_config.remote_keys_manager_enabled else ""),
        'MAX_NONCE_GAP': sc_node_config.max_nonce_gap,
        'MAX_ACCOUNT_SLOTS': sc_node_config.max_account_slots,
        'MAX_MEMPOOL_SLOTS': sc_node_config.max_mempool_slots,
        'MAX_NONEXEC_SLOTS': sc_node_config.max_nonexec_pool_slots,
        'TX_LIFETIME': sc_node_config.tx_lifetime,
        'HANDLING_TXS_ENABLED': ("true" if sc_node_config.handling_txs_enabled else "false"),
        'FORGER_REWARD_ADDRESS': sc_node_config.forger_options.forger_reward_address,
        'EVM_STATE_DUMP_ENABLED': ("true" if sc_node_config.evm_state_dump_enabled else "false"),

    }
    config = config.replace("'", "")
    config = config.replace("NEW_LINE", "\n")
    configsData.append({
        "name": "node" + str(n),
        "url": "http://" + apiAddress + ":" + str(apiPort)
    })

    with open(os.path.join(datadir, "node" + str(n) + ".conf"), 'w+') as configFile:
        configFile.write(config)

    return configsData


"""
Create directories for each node and default configuration files inside them.
For each node put also genesis data in configuration files.
"""
def initialize_default_sc_datadir(dirname, n, api_key):
    apiAddress = "127.0.0.1"
    configsData = []
    apiPort = sc_rpc_port(n)
    bindPort = sc_p2p_port(n)
    datadir = os.path.join(dirname, "sc_node" + str(n))
    if not os.path.isdir(datadir):
        os.makedirs(datadir)

    ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
    if not os.path.isdir(ps_keys_dir):
        os.makedirs(ps_keys_dir)

    cert_keys_paths = cert_proof_keys_paths(ps_keys_dir, cert_threshold_sig_max_keys=7, isCSWEnabled=False)
    csw_keys_paths = csw_proof_keys_paths(ps_keys_dir,
                                          LARGE_WITHDRAWAL_EPOCH_LENGTH)  # withdrawal epoch length taken from the config file.
    resourcesDir = get_resources_dir()
    with open(resourcesDir + '/template_predefined_genesis.conf', 'r') as templateFile:
        tmpConfig = templateFile.read()

    config = tmpConfig % {
        'NODE_NUMBER': n,
        'DIRECTORY': dirname,
        'WALLET_SEED': "sidechain_seed_{0}".format(n),
        'API_ADDRESS': "127.0.0.1",
        'API_PORT': str(apiPort),
        'API_TIMEOUT': "5s",
        'BIND_PORT': str(bindPort),
        'MAX_INCOMING_CONNECTIONS': 100,
        'MAX_OUTGOING_CONNECTIONS': 100,
        'KNOWN_PEERS': [],
        'STORAGE_BACKUP_INTERVAL': "15m",
        'STORAGE_BACKUP_DELAY': "5m",
        'ALLOW_UNPROTECTED_TXS': "true",
        'GET_PEERS_INTERVAL': "2m",
        'OFFLINE_GENERATION': "false",
        "SUBMITTER_CERTIFICATE": "false",
        "CERTIFICATE_SIGNING": "false",
        "CERT_PROVING_KEY_PATH": cert_keys_paths.proving_key_path,
        "CERT_VERIFICATION_KEY_PATH": cert_keys_paths.verification_key_path,
        "CSW_PROVING_KEY_PATH": csw_keys_paths.proving_key_path,
        "CSW_VERIFICATION_KEY_PATH": csw_keys_paths.verification_key_path,
        "RESTRICT_FORGERS": "false",
        "ALLOWED_FORGERS_LIST": [],
        "MAX_MODIFIERS_SPEC_MESSAGE_SIZE": DEFAULT_MAX_PACKET_SIZE,
        "REMOTE_KEY_MANAGER_ENABLED": "false",
        "REMOTE_SERVER_ADDRESS": ""
    }

    configsData.append({
        "name": "node" + str(n),
        "url": "http://" + apiAddress + ":" + str(apiPort)
    })
    with open(os.path.join(datadir, "node" + str(n) + ".conf"), 'w+') as configFile:
        configFile.write(config)

    return configsData


def initialize_default_sc_chain_clean(test_dir, num_nodes, api_key=""):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        initialize_default_sc_datadir(test_dir, i, api_key)


def initialize_sc_chain_clean(test_dir, num_nodes, model, genesis_secrets, genesis_info, array_of_MCConnectionInfo=[]):
    """
    Create an empty blockchain and num_nodes wallets.
    Useful if a test case wants complete control over initialization.
    """
    for i in range(num_nodes):
        sc_node_config = SCNodeConfiguration(get_websocket_configuration(i, array_of_MCConnectionInfo))
        initialize_sc_datadir(test_dir, i, model, genesis_secrets[i], genesis_info[i], sc_node_config)


def get_websocket_configuration(index, array_of_MCConnectionInfo):
    return array_of_MCConnectionInfo[index] if index < len(array_of_MCConnectionInfo) else MCConnectionInfo()



def get_lib_separator():
    lib_separator = ":"
    if sys.platform.startswith('win'):
        lib_separator = ";"
    return lib_separator

def get_examples_dir():
    return os.path.abspath(os.path.join(os.path.dirname( __file__ ), '../..', 'examples'))

SIMPLE_APP_BINARY = get_examples_dir() + "/utxo/simpleapp/target/sidechains-sdk-simpleapp-"+SNAPSHOT_VERSION_TAG+".jar" + get_lib_separator() + get_examples_dir() + "/utxo/simpleapp/target/lib/* io.horizen.examples.SimpleApp"
EVM_APP_BINARY = get_examples_dir() + "/account/evmapp/target/sidechains-sdk-evmapp-"+SNAPSHOT_VERSION_TAG+".jar" + get_lib_separator() + get_examples_dir() + "/account/evmapp/target/lib/* io.horizen.examples.EvmApp"
UTXO_BOOTSTRAPPING_TOOL = get_examples_dir() + "/utxo/utxoapp_sctool/target/sidechains-sdk-utxoapp_sctool-"+SNAPSHOT_VERSION_TAG+".jar"
EVM_BOOTSTRAPPING_TOOL = get_examples_dir() + "/account/evmapp_sctool/target/sidechains-sdk-evmapp_sctool-"+SNAPSHOT_VERSION_TAG+".jar"


def start_sc_node(i, dirname, extra_args=None, rpchost=None, timewait=None, binary=None, print_output_to_file=False,
                  auth_api_key=None):
    """
    Start a SC node and returns API connection to it
    """
    # Will we have  extra args for SC too ?
    datadir = os.path.join(dirname, "sc_node" + str(i))
    if binary is None:
        binary = SIMPLE_APP_BINARY
    #        else if platform.system() == 'Linux':
    '''
    In order to effectively attach a debugger (e.g IntelliJ) to the simpleapp, it is necessary to start the process
    enabling the debug agent which will act as a server listening on the specified port.
    '''
    dbg_agent_opt = ''
    additional_params = ''

    # Cmd line arguments are positional, so parameters might need to provide default values
    val_mc_block_delay_ref = "0"
    val_all_forks = "0"
    val_max_hist_rew_len = "100" # default value defined in io/horizen/history/AbstractHistory.scala, it can be overridden only in regtest

    if extra_args is not None:
        if "-agentlib" in extra_args:
            dbg_agent_opt = ' -agentlib:jdwp=transport=dt_socket,server=y,suspend=y,address=*:5005'
        if "-mc_block_delay_ref" in extra_args:
            val_mc_block_delay_ref = extra_args[extra_args.index("-mc_block_delay_ref") + 1]

        if "-all_forks" in extra_args:
            val_all_forks = extra_args[extra_args.index("-all_forks") + 1]

        if "-max_hist_rew_len" in extra_args:
            val_max_hist_rew_len = extra_args[extra_args.index("-max_hist_rew_len") + 1]

        additional_params = val_mc_block_delay_ref + " " + val_all_forks + " " + val_max_hist_rew_len

    cfgFileName = datadir + ('/node%s.conf' % i)
    '''
    Some tools and libraries use reflection to access parts of the JDK that are meant for internal use only.
    This illegal reflective access will be disabled in a future release of the JDK.
    Currently, it is permitted by default and a warning is issued.
    The --add-opens VM option remove this warning.
    '''

    user_home = os.path.expanduser("~")
    jacoco_agent_path = os.path.join(user_home, ".m2", "repository", "org", "jacoco", "org.jacoco.agent", "0.8.9",
                                     "org.jacoco.agent-0.8.9-runtime.jar")
    jacoco_cmd = f'-javaagent:{jacoco_agent_path}=destfile=../coverage-reports/sidechains-sdk-{SNAPSHOT_VERSION_TAG}/sidechains-sdk-{SNAPSHOT_VERSION_TAG}-jacoco-report.exec,append=true'

    if is_jacoco_included:
        bashcmd = 'java --add-opens java.base/java.lang=ALL-UNNAMED ' + jacoco_cmd + dbg_agent_opt + ' -cp ' + binary + " " + cfgFileName \
              + " " + additional_params
    else:
        bashcmd = 'java --add-opens java.base/java.lang=ALL-UNNAMED ' + dbg_agent_opt + ' -cp ' + binary + " " + cfgFileName \
              + " " + additional_params

    if print_output_to_file:
        with open(datadir + "/log_out.txt", "wb") as out, open(datadir + "/log_err.txt", "wb") as err:
            sidechainclient_processes[i] = subprocess.Popen(bashcmd.split(), stdout=out, stderr=err)
    else:
        sidechainclient_processes[i] = subprocess.Popen(bashcmd.split())
    url = "http://%s:%d" % ('127.0.0.1' or rpchost, sc_rpc_port(i))
    proxy = SidechainAuthServiceProxy(url, auth_api_key=auth_api_key)
    proxy.url = url  # store URL on proxy for info
    proxy.dataDir = datadir  # store the name of the datadir
    return proxy


def start_sc_nodes(num_nodes, dirname, extra_args=None, rpchost=None, binary=None, print_output_to_file=False,
                   auth_api_key=DEFAULT_API_KEY):
    """
    Start multiple SC clients, return connections to them
    """
    if extra_args is None: extra_args = [None for i in range(num_nodes)]
    if binary is None: binary = [None for i in range(num_nodes)]
    nodes = [
        start_sc_node(i, dirname, extra_args[i], rpchost, binary=binary[i], print_output_to_file=print_output_to_file,
                      auth_api_key=auth_api_key)
        for i in range(num_nodes)]
    wait_for_sc_node_initialization(nodes)
    return nodes


def check_sc_node(i):
    '''
    Check subprocess return code.
    '''
    sidechainclient_processes[i].poll()
    return sidechainclient_processes[i].returncode


def stop_sc_node(node, i):
    node.node_stop()
    if i in sidechainclient_processes:
        sc_proc = sidechainclient_processes[i]
        sc_proc.wait()
        del sidechainclient_processes[i]


def stop_sc_nodes(nodes):
    global sidechainclient_processes
    for idx in range(0, len(nodes)):
        if idx in sidechainclient_processes:
            stop_sc_node(nodes[idx], idx)
    del nodes[:]


def set_sc_node_times(nodes, t):
    pass


def wait_sidechainclients():
    # Wait for all the processes to cleanly exit
    for sidechainclient in sidechainclient_processes.values():
        sidechainclient.wait()
    sidechainclient_processes.clear()


def get_sc_node_pids():
    return [process.pid for process in sidechainclient_processes.values()]


def connect_sc_nodes(from_connection, node_num, wait_for=25):
    """
    Connect a SC node, from_connection, to another one, specifying its node_num. 
    Method will attempt to create the connection for maximum wait_for seconds.
    """
    j = {"host": "127.0.0.1", \
         "port": str(sc_p2p_port(node_num))}
    ip_port = "127.0.0.1:" + str(sc_p2p_port(node_num))
    logging.info("Connecting to '" + ip_port + "'")
    from_connection.node_connect(json.dumps(j))
    start = time.time()
    while True:
        if time.time() - start >= wait_for:
            raise (TimeoutException("Trying to connect to node{0}".format(node_num)))
        if any(i for i in (from_connection.node_connectedPeers()["result"]["peers"]) if
               i.get("remoteAddress") == "/" + ip_port):
            break
        time.sleep(WAIT_CONST)


def disconnect_sc_nodes(from_connection, node_num):
    """
    Disconnect a SC node, from_connection, to another one, specifying its node_num.
    """
    j = {"host": "127.0.0.1", \
         "port": str(sc_p2p_port(node_num))}
    ip_port = "\"127.0.0.1:" + str(sc_p2p_port(node_num)) + "\""
    logging.info("Disconnecting from " + ip_port)
    from_connection.node_disconnect(json.dumps(j))


def sc_connected_peers(node):
    return node.node_connectedPeers()["result"]["peers"]


def disconnect_sc_nodes_bi(nodes, a, b):
    disconnect_sc_nodes(nodes[a], b)
    disconnect_sc_nodes(nodes[b], a)
    time.sleep(WAIT_CONST)


def connect_sc_nodes_bi(nodes, a, b):
    connect_sc_nodes(nodes[a], b)
    connect_sc_nodes(nodes[b], a)


def connect_to_mc_node(sc_node, mc_node, *kwargs):
    pass


def assert_equal(expected, actual, message=""):
    if expected != actual:
        if message:
            message = "; %s" % message
        raise AssertionError("(left == right)%s\n  left: <%s>\n right: <%s>" % (message, str(expected), str(actual)))


def assert_true(condition, message=""):
    if not condition:
        raise AssertionError(message)


"""
Verify if a mainchain block data is equal to mainchain block reference info data.

Parameters:
 - mc_block_reference_info: the JSON representation of a mainchain block reference info. See io.horizen.node.util.MainchainBlockReferenceInfo
 - expected_mc_block: the JSON representation of a mainchain block
"""


def check_mainchain_block_reference_info(mc_block_reference_info, expected_mc_block):
    try:

        parent_hash = mc_block_reference_info["blockReferenceInfo"]["parentHash"]
        hash = mc_block_reference_info["blockReferenceInfo"]["hash"]
        height = mc_block_reference_info["blockReferenceInfo"]["height"]

        expected_mc_block_hash = expected_mc_block["hash"]
        expected_mc_block_height = expected_mc_block["height"]

        assert_equal(expected_mc_block_hash, hash)
        assert_equal(expected_mc_block_height, height)

        expected_mc_block_previousblockhash = expected_mc_block["previousblockhash"]

        assert_equal(expected_mc_block_previousblockhash, parent_hash)

        return True
    except Exception:
        return False


"""
Verify if a mainchain block is included in a sidechain block.

Parameters:
 - sc_block: the JSON representation of a sidechain block. See io.horizen.block.SidechainBlock
 - expected_mc_block: the JSON representation of a mainchain block
"""


def is_mainchain_block_included_in_sc_block(sc_block, expected_mc_block):
    mc_block_headers_json = sc_block["mainchainHeaders"]
    is_mac_block_included = False

    for mc_block_header_json in mc_block_headers_json:
        expected_mc_block_merkleroot = expected_mc_block["merkleroot"]

        sc_mc_block_merkleroot = mc_block_header_json["hashMerkleRoot"]

        if expected_mc_block_merkleroot == sc_mc_block_merkleroot:
            is_mac_block_included = True
            break

    return is_mac_block_included


"""
Verify the wallet coins balance is equal to an expected coins balance.
Note: core coins boxes are: ZenBox and ForgerBox

Parameters:
 - sc_node: a sidechain node
 - expected_wallet_balance
"""


def check_wallet_coins_balance(sc_node, expected_wallet_balance):
    response = sc_node.wallet_coinsBalance()

    if "result" in response:
        balance = response["result"]
        assert_equal(expected_wallet_balance * 100000000, int(balance["balance"]), "Unexpected coins balance")

        return

    raise RuntimeError("Something went wrong, see {}".format(str(response)))


"""
For a given Account verify the number of related boxes by type name (class name) and verify the sum of their balances.

Parameters:
 - sc_node: a sidechain node
 - account: an instance of Account (see sc_bootstrap_info.py)
 - box_class_name: class name of the box or None if want to check all boxes for given account
 - expected_boxes_count: the number of expected boxes for that account
 - expected_balance: expected balance for that account
"""


def check_box_balance(sc_node, account, box_class_name, expected_boxes_count, expected_balance):
    response = sc_node.wallet_allBoxes()

    if "result" in response:
        if "boxes" in response["result"]:
            boxes = response["result"]["boxes"]
            boxes_balance = 0
            boxes_count = 0
            pub_key = account.publicKey
            for box in boxes:
                if box["proposition"]["publicKey"] == pub_key and (
                        box_class_name is None or box["typeName"] == box_class_name):
                    box_value = box["value"]
                    assert_true(box_value > 0,
                                "Non positive value for box: {0} with public key: {1}".format(box["id"], pub_key))
                    boxes_balance += box_value
                    boxes_count += 1

            assert_equal(expected_boxes_count, boxes_count,
                         "Unexpected number of boxes for public key {0}. Expected {1} but found {2}."
                         .format(pub_key, expected_boxes_count, boxes_count))
            assert_equal(expected_balance * 100000000, boxes_balance,
                         "Unexpected sum of balances for public key {0}. Expected {1} but found {2}."
                         .format(pub_key, expected_balance * 100000000, boxes_balance))
            return

    raise RuntimeError("Something went wrong, see {}".format(str(response)))


# In STF we need to create SC genesis block with a timestamp in the past to be able to forge next block
# without receiving "block in future" error. By default we rewind half of the consensus epoch.
DefaultBlockTimestampRewind = 720 * 120 / 2

# UTXO / Account model string
UtxoModel = "utxo"
AccountModel = "account"
DefaultModel = UtxoModel

"""
Bootstrap a network of sidechain nodes.

Parameters:
 - network: an instance of SCNetworkConfiguration (see sc_boostrap_info.py)
                
Example: 2 mainchain nodes and 3 sidechain nodes (with default websocket configuration) bootstrapped, respectively, from mainchain node first, first, and third.
The JSON representation is only for documentation.
{
network: {
    "sc_creation_info":{
            "mainchain_node": mc_node_1,
            "sc_id": "id_1"
            "forward_amout": 200
            "withdrawal_epoch_length": 100
        },
        [
            sidechain_1_configuration: {
                "mc_connection_info":{
                    "address": "ws://mc_node_1_hostname:mc_node_1_ws_port"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            },
            sidechain_2_configuration: {
                "mc_connection_info":{
                    "address": "ws://mc_node_1_hostname:mc_node_1_ws_port"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            
            },
            sidechain_3_configuration: {
                "mc_connection_info":{
                    "address": "ws://mc_node_2_hostname:mc_node_2_ws_port"
                    "connectionTimeout": 100
                    "reconnectionDelay": 1
                    "reconnectionMaxAttempts": 1
                }
            }
        ]
    }
}
 
 Output:
 - bootstrap information of the sidechain nodes. An instance of SCBootstrapInfo (see sc_boostrap_info.py)    
"""


def bootstrap_sidechain_nodes(options, network=SCNetworkConfiguration,
                              block_timestamp_rewind=DefaultBlockTimestampRewind, model=DefaultModel):

    log_info = LogInfo(options.logfilelevel, options.logconsolelevel)
    logging.info(options)
    total_number_of_sidechain_nodes = len(network.sc_nodes_configuration)
    sc_creation_info = network.sc_creation_info
    ps_keys_dir = os.getenv("SIDECHAIN_SDK", "..") + "/qa/ps_keys"
    if not os.path.isdir(ps_keys_dir):
        os.makedirs(ps_keys_dir)
    cert_keys_paths = cert_proof_keys_paths(ps_keys_dir, sc_creation_info.cert_max_keys, sc_creation_info.csw_enabled,
                                            sc_creation_info.circuit_type)
    if sc_creation_info.csw_enabled:
        csw_keys_paths = csw_proof_keys_paths(ps_keys_dir, sc_creation_info.withdrawal_epoch_length)
    else:
        csw_keys_paths = None

    sc_nodes_bootstrap_info = create_sidechain(sc_creation_info,
                                               block_timestamp_rewind,
                                               cert_keys_paths,
                                               csw_keys_paths,
                                               model)
    sc_nodes_bootstrap_info_empty_account = SCBootstrapInfo(sc_nodes_bootstrap_info.sidechain_id,
                                                            None,
                                                            sc_nodes_bootstrap_info.genesis_account_balance,
                                                            sc_nodes_bootstrap_info.mainchain_block_height,
                                                            sc_nodes_bootstrap_info.sidechain_genesis_block_hex,
                                                            sc_nodes_bootstrap_info.pow_data,
                                                            sc_nodes_bootstrap_info.network,
                                                            sc_nodes_bootstrap_info.withdrawal_epoch_length,
                                                            sc_nodes_bootstrap_info.genesis_vrf_account,
                                                            sc_nodes_bootstrap_info.certificate_proof_info,
                                                            sc_nodes_bootstrap_info.initial_cumulative_comm_tree_hash,
                                                            sc_nodes_bootstrap_info.is_non_ceasing,
                                                            cert_keys_paths,
                                                            csw_keys_paths,
                                                            None,
                                                            sc_creation_info.circuit_type)
    for i in range(total_number_of_sidechain_nodes):
        sc_node_conf = network.sc_nodes_configuration[i]
        if i == 0:
            bootstrap_sidechain_node(options.tmpdir, i, sc_nodes_bootstrap_info, sc_node_conf, model, log_info,
                                     options.restapitimeout)
        else:
            bootstrap_sidechain_node(options.tmpdir, i, sc_nodes_bootstrap_info_empty_account, sc_node_conf, model, log_info,
                                     options.restapitimeout)
    return sc_nodes_bootstrap_info


def cert_proof_keys_paths(dirname, cert_threshold_sig_max_keys=7, isCSWEnabled=False,
                          circuit_type=NO_KEY_ROTATION_CIRCUIT):
    # use replace for Windows OS to be able to parse the path to the keys in the config file
    if (circuit_type == NO_KEY_ROTATION_CIRCUIT):
        if isCSWEnabled:
            pk = "cert_marlin_snark_pk"
            vk = "cert_marlin_snark_vk"
        else:
            pk = "cert_marlin_snark_pk_csw_disabled"
            vk = "cert_marlin_snark_vk_csw_disabled"
    elif circuit_type == KEY_ROTATION_CIRCUIT:
        assert_false(isCSWEnabled)
        pk = "cert_marlin_snark_pk_with_key_rotation"
        vk = "cert_marlin_snark_vk_with_key_rotation"
    else:
        assert "type is not supported " + str(circuit_type)

    return ProofKeysPaths(
        os.path.join(dirname, pk + str(cert_threshold_sig_max_keys)).replace("\\", "/"),
        os.path.join(dirname, vk + str(cert_threshold_sig_max_keys)).replace("\\", "/"))


def csw_proof_keys_paths(dirname, withdrawal_epoch_length):
    # use replace for Windows OS to be able to parse the path to the keys in the config file
    return ProofKeysPaths(
        os.path.join(dirname, "csw_marlin_snark_pk_" + str(withdrawal_epoch_length)).replace("\\", "/"),
        os.path.join(dirname, "csw_marlin_snark_vk_" + str(withdrawal_epoch_length)).replace("\\", "/"))


"""
Create a sidechain transaction inside a mainchain node.

Parameters:
 - sc_creation_info: an instance of SCCreationInfo (see sc_boostrap_info.py)
 
 Output:
  - an instance of SCBootstrapInfo (see sc_boostrap_info.py)
"""


def create_sidechain(sc_creation_info, block_timestamp_rewind, cert_keys_paths, csw_keys_paths,
                     model=DefaultModel):
    accounts = generate_secrets("seed", 1, model)
    vrf_keys = generate_vrf_secrets("seed", 1, model)
    genesis_account = accounts[0]
    vrf_key = vrf_keys[0]
    withdrawal_epoch_length = 0 if sc_creation_info.non_ceasing else sc_creation_info.withdrawal_epoch_length
    virtual_withdrawal_epoch_length = sc_creation_info.withdrawal_epoch_length if sc_creation_info.non_ceasing else 0
    certificate_proof_info = generate_certificate_proof_info("seed", sc_creation_info.cert_max_keys,
                                                             sc_creation_info.cert_sig_threshold, cert_keys_paths,
                                                             sc_creation_info.csw_enabled,
                                                             sc_creation_info.circuit_type, model)
    if csw_keys_paths is None:
        csw_verification_key = ""
    else:
        csw_verification_key = generate_csw_proof_info(sc_creation_info.withdrawal_epoch_length, csw_keys_paths, model)

    genesis_evm_account = None
    evm_account_public_key = None
    if (model == AccountModel):
        evm_accounts = generate_account_proposition("seed", 1, model)
        genesis_evm_account = evm_accounts[0]
        evm_account_public_key = genesis_evm_account.proposition

    genesis_info = initialize_new_sidechain_in_mainchain(
        sc_creation_info.mc_node,
        withdrawal_epoch_length,
        genesis_account.publicKey,
        sc_creation_info.forward_amount,
        vrf_key.publicKey,
        certificate_proof_info.genSysConstant,
        certificate_proof_info.verificationKey,
        csw_verification_key,
        sc_creation_info.btr_data_length,
        sc_creation_info.sc_creation_version,
        sc_creation_info.csw_enabled,
        sc_creation_info.circuit_type,
        evm_account_public_key)


    genesis_data = generate_genesis_data(genesis_info[0], genesis_account.secret, vrf_key.secret,
                                         block_timestamp_rewind, model, virtual_withdrawal_epoch_length)
    sidechain_id = genesis_info[2]

    return SCBootstrapInfo(sidechain_id, genesis_account, sc_creation_info.forward_amount, genesis_info[1],
                           genesis_data["scGenesisBlockHex"], genesis_data["powData"], genesis_data["mcNetwork"],
                           sc_creation_info.withdrawal_epoch_length, vrf_key, certificate_proof_info,
                           genesis_data["initialCumulativeCommTreeHash"], sc_creation_info.non_ceasing, cert_keys_paths, csw_keys_paths,
                           genesis_evm_account, sc_creation_info.circuit_type)


def calculateApiKeyHash(auth_api_key, model):
    json_parameters = {
        "string": auth_api_key
    }
    return launch_bootstrap_tool("encodeString", json_parameters, model)["encodedString"]


"""
Bootstrap one sidechain node: create directory and configuration file for the node.

Parameters:
 - n: sidechain node nth: used to create directory "sc_node_n"
 - bootstrap_info: an instance of SCBootstrapInfo (see sc_boostrap_info.py)
 - sc_node_configuration: an instance of SCNodeConfiguration (see sc_boostrap_info.py)
 - log_info: optional, an instance of LogInfo with log file name and levels for the log file and console
 - rest_api_timeout: optional, SC node api timeout, 5 seconds by default.
 
"""


def bootstrap_sidechain_node(dirname, n, bootstrap_info, sc_node_configuration, model,
                             log_info=LogInfo(), rest_api_timeout=DEFAULT_REST_API_TIMEOUT):
    initialize_sc_datadir(dirname, n, model, bootstrap_info, sc_node_configuration, log_info, rest_api_timeout)


def generate_forging_request(epoch, slot, forced_tx):
    return json.dumps({"epochNumber": epoch, "slotNumber": slot, "transactionsBytes": forced_tx})


def get_next_epoch_slot(epoch, slot, slots_in_epoch, force_switch_to_next_epoch=False):
    next_slot = slot + 1
    next_epoch = epoch

    if next_slot > slots_in_epoch or force_switch_to_next_epoch:
        next_slot = 1
        next_epoch += 1
    return next_epoch, next_slot


def generate_next_block(node, node_name, force_switch_to_next_epoch=False, verbose=True, forced_tx=None):
    forging_info = node.block_forgingInfo()["result"]
    slots_in_epoch = forging_info["consensusSlotsInEpoch"]
    best_slot = forging_info["bestBlockSlotNumber"]
    best_epoch = forging_info["bestBlockEpochNumber"]

    next_epoch, next_slot = get_next_epoch_slot(best_epoch, best_slot, slots_in_epoch, force_switch_to_next_epoch)

    forging_request = generate_forging_request(next_epoch, next_slot, forced_tx)
    forge_result = node.block_generate(forging_request)

    # "while" will break if whole epoch no generated block, due changed error code
    # ErrorBlockNotCreated = 0105
    count_slot = slots_in_epoch
    while "error" in forge_result and forge_result["error"]["code"] == "0105":
        if ("no forging stake" in forge_result["error"]["description"]):
            raise AssertionError("No forging stake for the epoch")
        if ("ForgerStakes list can't be empty" in forge_result["error"]["description"]):
            raise AssertionError("Empty forger stakes list")
        if ("top quality certificate" in forge_result["error"]["description"]):
            raise AssertionError("Inconsistent top quality certificate")
        if ("the sidechain has ceased" in forge_result["error"]["description"]):
            raise AssertionError("Sidechain has ceased")
        if ("semantically invalid" in forge_result["error"]["description"]):
            raise AssertionError("One transaction in the block is semantically invalid")
        if ("CertificateKeyRotationTransaction" in forge_result["error"]["description"]):
            raise AssertionError("CertificateKeyRotationTransaction error: {}".format(forge_result["error"]["description"]))
        if ("We can not forge until we have at least a mc block reference" in forge_result["error"]["description"]):
            raise AssertionError("No mc refs in a long row of blocks error: {}".format(forge_result["error"]["description"]))

        count_slot -= 1
        if (count_slot <= 0):
            errMsg = "Could not generate any block in this epoch: {}".format(forge_result["error"]["description"])
            logging.warning("Api Error msg not handled: " + errMsg)
            raise AssertionError(errMsg)

        logging.info("Skip block generation for epoch {epochNumber} slot {slotNumber}".format(epochNumber=next_epoch,
                                                                                       slotNumber=next_slot))
        next_epoch, next_slot = get_next_epoch_slot(next_epoch, next_slot, slots_in_epoch)
        forge_result = node.block_generate(generate_forging_request(next_epoch, next_slot, forced_tx))

    assert_true("result" in forge_result, "Error during block generation for SC {0}".format(node_name))
    block_id = forge_result["result"]["blockId"]
    if verbose == True:
        logging.info("Successfully forged block with id {blockId}".format(blockId=block_id))
    return forge_result["result"]["blockId"]


def generate_next_blocks(node, node_name, blocks_count, verbose=True):
    blocks_ids = []
    for i in range(blocks_count):
        blocks_ids.append(generate_next_block(node, node_name, force_switch_to_next_epoch=False, verbose=verbose))
    return blocks_ids

def try_to_generate_block_in_slot(node, next_epoch, next_slot):

    forging_request = generate_forging_request(next_epoch, next_slot, None)
    forge_result = node.block_generate(forging_request)
    return forge_result

def try_to_generate_block_in_slots(node, slot_count, add_empty_slots=False):
    block_ids = []

    # Get starting slot
    forging_info = node.block_forgingInfo()["result"]
    slots_in_epoch = forging_info["consensusSlotsInEpoch"]
    best_slot = forging_info["bestBlockSlotNumber"]
    best_epoch = forging_info["bestBlockEpochNumber"]
    next_epoch, next_slot = get_next_epoch_slot(best_epoch, best_slot, slots_in_epoch)

    for _ in range(slot_count):
        res = try_to_generate_block_in_slot(node, next_epoch, next_slot)
        if "result" in res and "blockId" in res["result"]:
            block_ids.append(res["result"]["blockId"])
        else:
            if (add_empty_slots):
                block_ids.append("")
        next_epoch, next_slot = get_next_epoch_slot(next_epoch, next_slot, slots_in_epoch)
     
    return block_ids

# Check if the CSW proofs for the required boxes were finished (or absent if was not able to create a proof)
def if_csws_were_generated(sc_node, csw_box_ids, allow_absent=False):
    for box_id in csw_box_ids:
        req = json.dumps({"boxId": box_id})
        status = sc_node.csw_cswInfo(req)["result"]["cswInfo"]["proofInfo"]["status"]
        if status == "Absent" and allow_absent:
            continue
        elif status != "Generated":
            return False
    return True


def get_scinfo_data(scid, mc_node):
    ret = mc_node.getscinfo(scid)['items'][0]
    sc_creating_height = ret['createdAtBlockHeight']
    sc_version = ret['version']
    sc_constant = ret['constant']
    sc_customData = ret['customData']
    epochLen = ret['withdrawalEpochLength']
    current_height = mc_node.getblockcount()
    epoch_number = (current_height - sc_creating_height + 1) // epochLen - 1
    end_epoch_block_hash = mc_node.getblockhash(sc_creating_height - 1 + ((epoch_number + 1) * epochLen))
    epoch_cum_tree_hash = mc_node.getblock(end_epoch_block_hash)['scCumTreeHash']
    return epoch_number, epoch_cum_tree_hash, sc_version, sc_constant, sc_customData


def create_alien_sidechain(mcTest, mc_node, scVersion, epochLength, customHexTag, feCfgList=[]):
    sc_tag = customHexTag
    keyrot = True if scVersion == 2 else False
    vk = mcTest.generate_params(sc_tag, keyrot = keyrot)
    constant = generate_random_field_element_hex()
    # we use this field to store sc_tag, used by mcTool when creating certificates
    customData = sc_tag
    cswVk = ""
    cmtCfg = []

    cmdInput = {
        "version": scVersion,
        "withdrawalEpochLength": epochLength,
        "toaddress": "dada",
        "amount": 0.1,
        "wCertVk": vk,
        "constant": constant,
        'customData': customData,
        'wCeasedVk': cswVk,
        'vFieldElementCertificateFieldConfig': feCfgList,
        'vBitVectorCertificateFieldConfig': cmtCfg
    }
    try:
        ret = mc_node.sc_create(cmdInput)
    except Exception as e:
        logging.error(e)
        assert_true(False)
    # self.sync_all()
    # time.sleep(1) # if we have one node the sync_all won't sleep
    assert_equal(True, ret['txid'] in mc_node.getrawmempool())

    return ret


def create_certificate_for_alien_sc(mcTest, scid, mc_node, fePatternArray, prev_cert_data_hash = None):
    epoch_number_1, epoch_cum_tree_hash_1, sc_version, constant, sc_tag = get_scinfo_data(scid, mc_node)

    logging.info("sc_tag[{}]".format(sc_tag))
    logging.info("constant={}".format(constant))
    logging.info("sc_version={}".format(sc_version))

    vCfe = fePatternArray
    vCmt = []

    MBTR_SC_FEE = 0.0
    FT_SC_FEE = 0.0
    CERT_FEE = Decimal('0.0001')

    # get a UTXO
    utx, change = get_spendable(mc_node, CERT_FEE)

    inputs = [{'txid': utx['txid'], 'vout': utx['vout']}]
    outputs = {mc_node.getnewaddress(): change}

    # serialized fe for the proof has 32 byte size
    feList = []
    for pattern in fePatternArray:
        feList.append(get_field_element_with_padding(pattern, sc_version))
    scid_swapped = str(swap_bytes(scid))

    prev_cert_hash = prev_cert_data_hash
    if sc_version == 2 and prev_cert_data_hash == None:
        prev_cert_hash = 0

    scProof = mcTest.create_test_proof(
        sc_tag, scid_swapped, epoch_number_1, quality=10,
        btr_fee=MBTR_SC_FEE, ft_min_amount=FT_SC_FEE,
        end_cum_comm_tree_root=epoch_cum_tree_hash_1, constant=constant,
        pks=[], amounts=[], custom_fields=feList, prev_cert_hash=prev_cert_hash)

    logging.info("cum =", str(epoch_cum_tree_hash_1))
    params = {
        'scid': scid,
        'quality': 10,
        'endEpochCumScTxCommTreeRoot': epoch_cum_tree_hash_1,
        'scProof': scProof,
        'withdrawalEpochNumber': epoch_number_1,
        'vFieldElementCertificateField': vCfe,
        'vBitVectorCertificateField': vCmt
    }

    try:
        rawcert = mc_node.createrawcertificate(inputs, outputs, [], params)
        signed_cert = mc_node.signrawtransaction(rawcert)
        cert = mc_node.sendrawtransaction(signed_cert['hex'])
    except Exception as e:
        logging.error("Send certificate failed with reason {}".format(e))
        assert (False)

    assert_equal(True, cert in mc_node.getrawmempool())
    return cert


def get_resources_dir():
    return os.path.abspath(os.path.join(os.path.dirname( __file__ ), '..', 'resources'))




def get_withdrawal_epoch(sc_node):
    j = {
        "blockId": sc_node.block_best()["result"]["block"]["id"]
    }
    request = json.dumps(j)
    return sc_node.block_findBlockInfoById(request)["result"]["blockInfo"]["withdrawalEpochInfo"]["epoch"]
