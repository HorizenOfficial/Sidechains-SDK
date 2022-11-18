# libevm

libevm implements a shared library to access a standalone instance of the go-ethereum EVM and its state storage layer StateDB and underlying LevelDB.

For simplicity all exported library functions take one parameter and return one value, which are all typed as C-strings and contain JSON.

## Build

The project can be build via Maven or the standard Go tooling:

`go build -buildmode c-shared -o bin/linux-x86-64/libevm.so`

As defined in the file `go.mod`, the required minimum version of Go is `1.18`. When building via Maven the mvn-golang plugin will automatically download and use the version of Go defined in the modules pom file.

## Tests

Note: Some tests require smart contract code which is compiled during `go generate` using the Solidity compiler `solc`. Make sure to have `solc` installed and run `go generate ./...` before tests.

Run all tests via Go tooling:

`go test ./...`
