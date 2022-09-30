import os
import subprocess
import logging

nodeModulesInstalled = False
cwd = None


def get_cwd():
    global cwd
    if cwd is None:
        cwd = os.environ.get("SIDECHAIN_SDK") + "/qa/SidechainTestFramework/account/smart_contract_resources"
    return cwd


def install_npm_packages():
    global nodeModulesInstalled
    if not nodeModulesInstalled:
        logging.info("Installing node packages...")
        logging.info("The first time this runs on your machine, it can take a few minutes...")
        proc = subprocess.run(
            ["yarn", "install", "--quiet", "--non-interactive", "--frozen-lockfile"],
            cwd=get_cwd(),
            stdout=subprocess.PIPE)
        proc.check_returncode()
        logging.info("Done!")
        nodeModulesInstalled = True


def legacy(*, to: str,
           value: int = None,
           gas_limit: int,
           gas_price: int,
           nonce: int,
           data: str = None):
    install_npm_packages()
    cmd = ['npx', 'hardhat', 'legacytx']
    cmd += ['--to', to]
    if value is not None:
        cmd += ['--value', str(value)]
    else:
        cmd += ['--value', '0x0']
    cmd += ['--gas-limit', str(gas_limit)]
    cmd += ['--gas-price', str(gas_price)]
    cmd += ['--nonce', str(nonce)]
    if data is not None:
        cmd += ['--data', str(data)]
    else:
        cmd += ['--data', '0x']
    logging.info("Calling", ' '.join(cmd) + '...')
    proc = subprocess.run(
        cmd,
        cwd=get_cwd(),
        capture_output=True)
    if proc.returncode != 0:
        logging.info("Error in call: ", proc.stderr.decode())
        raise RuntimeError("Something went wrong: " + proc.stderr.decode())
    else:
        output = proc.stdout.decode()
        output = output.split(' ')[1].strip()
        logging.info("Returned raw transaction:", '"' + output + '"')
        return output


def eip1559(*, to: str,
            value: int = None,
            gas_limit: int,
            max_fee_per_gas: int,
            max_priority_fee_per_gas: int,
            chain_id: int,
            nonce: int,
            data: str = None):
    install_npm_packages()
    cmd = ['npx', 'hardhat', 'eip1559tx']
    cmd += ['--to', to]
    if value is not None:
        cmd += ['--value', str(value)]
    else:
        cmd += ['--value', '0x0']
    cmd += ['--gas-limit', str(gas_limit)]
    cmd += ['--max-fee-per-gas', str(max_fee_per_gas)]
    cmd += ['--max-priority-fee-per-gas', str(max_priority_fee_per_gas)]
    cmd += ['--nonce', str(nonce)]
    cmd += ['--chain-id', str(chain_id)]
    if data is not None:
        cmd += ['--data', str(data)]
    else:
        cmd += ['--data', '0x']
    logging.info("Calling", ' '.join(cmd) + '...')
    proc = subprocess.run(
        cmd,
        cwd=get_cwd(),
        capture_output=True)
    if proc.returncode != 0:
        logging.info("Error in call: ", proc.stderr.decode())
        raise RuntimeError("Something went wrong: " + proc.stderr.decode())
    else:
        output = proc.stdout.decode()
        output = output.split(' ')[1].strip()
        logging.info("Returned raw transaction:", '"' + output + '"')
        return output


# def load_contract_abi(contractPath: str)


if __name__ == '__main__':
    assert ("0xda0101019470997970c51812dc3a010c7d01b50e0d17dc79c88080" == legacy(
        to='0x70997970C51812dc3A010C7d01b50e0d17dc79C8', value=0, gas_limit=1, gas_price=1, nonce=1, data='0x'))
    assert ("0x02df820539010101019470997970c51812dc3a010c7d01b50e0d17dc79c88080c0" == eip1559(
        to='0x70997970C51812dc3A010C7d01b50e0d17dc79C8', value=0, gas_limit=1, max_fee_per_gas=1,
        max_priority_fee_per_gas=1, chain_id=1337, nonce=1, data='0x'))
