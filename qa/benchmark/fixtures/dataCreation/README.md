# Test Tokens

1. Install dependencies via npm:

`npm install -g typescript && npm install`

2. Set a private and the RPC endpoint for the benchmark net key via:

`export PRIVATE_KEY=<your-private-key> export RPC_ENDPOINT_URL=<rpc_endpoint>`


3. Run the following command to deploy a test token. Name, symbol and type can be passed as arguments and the deployer will automatically receive some tokens.

`npx hardhat deploy --name EVMTestToken --symbol ETST --type erc20`

Type can be one of:

- `erc20`
- `erc721`
- `erc1155`
