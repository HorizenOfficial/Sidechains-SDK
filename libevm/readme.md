# libevm

libevm implements a shared library to access a standalone instance of the go-ethereum EVM.

For simplicity all exported library functions take one parameter and return one value, which are all typed as C-strings and contain JSON.

## Build

The project can be build via maven or the standard Go tooling:

`go build -buildmode c-shared -o bin/linux-x86-64/libevm.so`

## Tests

Run all tests via Go tooling:

`go test ./...`
