import { task } from "hardhat/config";
import "@nomiclabs/hardhat-waffle";
import {BigNumber, providers, Wallet} from "ethers";
import generateSignatures from "./helpers/generateSignatures";

// random receiver wallet for tests
const TEST_TRANSFER_RECEIVER = "0x000000000000000000000000000000000000dEaD";
interface DeploymentArguments {
  type:string,
  name:string,
  symbol:string
}

task("deploy", "Deploys a test token", async (args: DeploymentArguments, hre) => {
  // Ensure artifacts are cleaned and compiled
  await hre.run("clean");
  await hre.run("compile");

  const signers = await hre.ethers.getSigners();

  const contractFileNamePrefix = `Test${args.type.toUpperCase()}`;
  const TestToken = await hre.ethers.getContractFactory(contractFileNamePrefix);

  /* const balance = await hre.ethers.provider.getBalance(signers[0].address);
  console.log(`The account ${signers[0].address} has ${balance} for gas!`);
  if (balance.eq(BigNumber.from(0))) {
    throw new Error(`0 is too less for gas!`);
  } */

  // const compiledContracts = await generateSignatures(hre, contractFileNamePrefix, false);
  /* if (compiledContracts.length === 0) {
    throw new Error(`There was no contract found with prefix ${contractFileNamePrefix}`);
  }

  // we assume there is only one. If more, than the first is chosen
  const compiledContract = compiledContracts[0];

  console.info(`${contractFileNamePrefix} signature ${compiledContract.bytecode}`);*/
  console.info(`Deploying ${contractFileNamePrefix} contract`);

  // const rpc =  hre.ethers.provider.sendTransaction();


  // const wallet = new Wallet( process.env.PRIVATE_KEY, rpc);

  const testToken = await TestToken.deploy(args.name, args.symbol);
  // const testToken = await hre.ethers.getContractAt(, contractAddress);
  await testToken.deployed();

  console.info(`${contractFileNamePrefix} deployed at:`);
  // console.info(testToken.address);

  console.info(`Now running some test transfers`);
/*  switch (args.type) {
    case "erc20":
      await testToken.transfer(
          TEST_TRANSFER_RECEIVER,
          hre.ethers.utils.parseEther("100")
      );
      break;
    case "erc721":
      await testToken.transferFrom(
          signers[0].address,
          TEST_TRANSFER_RECEIVER,
          1
      );
      break;
    case "erc1155":
      await testToken.safeTransferFrom(
          signers[0].address,
          TEST_TRANSFER_RECEIVER,
          1,
          1,
          "0x"
      );
      break;
  }*/
})
  .addParam("name", "The name of the token", "EVMTestToken")
  .addParam("symbol", "The symbol of the token", "ETST")
  .addParam("type", "The token type <erc20|erc721|erc1155>", "erc20");
