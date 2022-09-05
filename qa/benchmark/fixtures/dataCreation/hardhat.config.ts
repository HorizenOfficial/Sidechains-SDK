import * as dotenv from "dotenv";

import { HardhatUserConfig } from "hardhat/config";
import "@typechain/hardhat";
import "solidity-coverage";
import "./tasks/deploy";

dotenv.config();

const networks: any = {};
if (process.env.PRIVATE_KEY) {
  console.log(`${process.env.RPC_ENDPOINT_URL}`);

  networks["evm-benchmark"] = {
    url: `${process.env.RPC_ENDPOINT_URL}`,
    accounts: [`0x${process.env.PRIVATE_KEY}`],
    gas: 100000,
    gasPrice: 10,
  };
}

const config: HardhatUserConfig = {
  defaultNetwork: "evm-benchmark",
  solidity: "0.8.4",
  networks
};

export default config;
