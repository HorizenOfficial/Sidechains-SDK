import {
  serialize,
  UnsignedTransaction,
  parse,
} from "@ethersproject/transactions";
import { HardhatUserConfig, task } from "hardhat/config";
import "@nomiclabs/hardhat-ethers";
import fs from "fs";
import "dotenv/config";
import { BigNumber, BigNumberish } from "@ethersproject/bignumber";
import assert from "assert";
task("sighashes", "Creates all the sighashes", async (taskArgs, hre) => {
  const artifactPaths = await hre.artifacts.getArtifactPaths();
  console.log(artifactPaths);
  for (const p of artifactPaths) {
    const filename = p.split("/")[p.split("/").length - 1];
    console.log("Generating sighashes for", filename + "...");
    const content = JSON.parse(fs.readFileSync(p, "utf-8"));
    const interf = new hre.ethers.utils.Interface(content.abi);
    for (const fn of interf.fragments) {
      if (fn.type === "constructor") continue;
      console.log("\tGenerating sighash for", fn.format() + "...");
      if (content.sighashes === undefined) content.sighashes = {};
      content.sighashes[fn.format()] = interf.getSighash(fn);
      fs.writeFileSync(p, JSON.stringify(content, null, 2));
    }
  }
});
task(
  "eip1559tx",
  "creates an eip-1559 transaction and returns the signable data",
  async (
    {
      to,
      value,
      gasLimit,
      maxFeePerGas,
      maxPriorityFeePerGas,
      nonce,
      chainId,
      data,
    },
    hre
  ) => {
    const assertBN = (
      a?: BigNumberish | undefined,
      b?: BigNumberish | undefined
    ) => {
      assert((!a && !b) || BigNumber.from(a).eq(b!));
    };
    const tx: UnsignedTransaction = {
      to,
      value: BigNumber.from(value),
      gasLimit: BigNumber.from(gasLimit),
      type: 2,
      maxFeePerGas: BigNumber.from(maxFeePerGas),
      maxPriorityFeePerGas: BigNumber.from(maxPriorityFeePerGas),
      nonce: Number.parseInt(nonce),
      chainId: Number.parseInt(chainId),
      data: data,
    };
    const serializedTx = serialize(tx);
    const parsedTx = <UnsignedTransaction>parse(serializedTx);
    assert(tx.to == parsedTx.to);
    assertBN(tx.value, parsedTx.value);
    assertBN(tx.gasLimit, parsedTx.gasLimit);
    assert(tx.type === parsedTx.type);
    assertBN(tx.maxFeePerGas, parsedTx.maxFeePerGas);
    assertBN(tx.maxPriorityFeePerGas, parsedTx.maxPriorityFeePerGas);
    assertBN(tx.nonce, parsedTx.nonce);
    assert(tx.data == parsedTx.data);
    assert(tx.chainId == parsedTx.chainId);
    console.log("rawtx " + serialize(parsedTx));
  }
)
  .addParam("to")
  .addOptionalParam("value")
  .addParam("gasLimit")
  .addParam("maxFeePerGas")
  .addParam("maxPriorityFeePerGas")
  .addParam("nonce")
  .addOptionalParam("data", undefined, "0x")
  .addParam("chainId");
task(
  "legacytx",
  "creates a legacy transaction and returns the signable data",
  async ({ to, value, gasLimit, gasPrice, nonce, data }, hre) => {
    const assertBN = (
      a?: BigNumberish | undefined,
      b?: BigNumberish | undefined
    ) => {
      assert((!a && !b) || BigNumber.from(a).eq(b!));
    };
    const tx: UnsignedTransaction = {
      to,
      value: BigNumber.from(value),
      gasLimit: BigNumber.from(gasLimit),
      gasPrice: BigNumber.from(gasPrice),
      nonce: Number.parseInt(nonce),
      data,
    };
    const serializedTx = serialize(tx);
    const parsedTx = <UnsignedTransaction>parse(serializedTx);
    assert(tx.to == parsedTx.to);
    assertBN(tx.value, parsedTx.value);
    assertBN(tx.gasLimit, parsedTx.gasLimit);
    assertBN(tx.gasPrice, parsedTx.gasPrice);
    assertBN(tx.nonce, parsedTx.nonce);
    assert(tx.data == parsedTx.data);
    console.log("rawtx " + serialize(parsedTx));
  }
)
  .addParam("to")
  .addOptionalParam("value", undefined, "0x0")
  .addParam("gasLimit")
  .addParam("gasPrice")
  .addParam("nonce")
  .addOptionalParam("data", undefined, "0x");

const config: HardhatUserConfig = {
  solidity: {
    compilers: [{ version: "0.8.23" }],
    overrides: {
      "contracts/NativeInteropShanghai.sol": {
        version: "0.8.23",
        settings: { 
	   evmVersion: "shanghai"
        }
      },
      "contracts/StorageShanghai.sol": {
        version: "0.8.23",
        settings: { 
	   evmVersion: "shanghai"
        }
      },
      "contracts/SimpleProxyShanghai.sol": {
        version: "0.8.23",
        settings: {
           evmVersion: "shanghai"
        }
      },

    }
  },
};

export default config;
