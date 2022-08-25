import { task } from "hardhat/config";
import "@nomiclabs/hardhat-waffle";
import {BigNumber} from "ethers";

// random receiver wallet for tests
const TEST_TRANSFER_RECEIVER = "0x000000000000000000000000000000000000dEaD";

task("deploy", "Deploys a test token", async (args: any, hre) => {
  const signers = await hre.ethers.getSigners();

  const TestToken = await hre.ethers.getContractFactory(
    `Test${args.type.toUpperCase()}`
  );

  const balance = await hre.ethers.provider.getBalance(signers[0].address);
  console.log(`The account ${signers[0].address} has ${balance} for gas!`);
  if (balance.eq(BigNumber.from(0))) {
    throw new Error(`0 is too less for gas!`);
  }

  console.info(`Deploying Test${args.type} contract`);
  const testToken = await TestToken.deploy(args.name, args.symbol);
  await testToken.deployed();

  console.info(`Test ${args.type} deployed at:`);
  console.info(testToken.address);

  console.info(`Now running some test transfers`);
  switch (args.type) {
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
  }
})
  .addParam("name", "The name of the token")
  .addParam("symbol", "The symbol of the token")
  .addParam("type", "The token type <erc20|erc721|erc1155>");
