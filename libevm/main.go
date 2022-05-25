package main

import (
	"github.com/ethereum/go-ethereum/log"
	"os"
)

const logLevel = log.LvlDebug

// static initializer
func init() {
	var logger = log.NewGlogHandler(log.StreamHandler(os.Stdout, log.TerminalFormat(true)))
	logger.Verbosity(logLevel)
	log.Root().SetHandler(logger)
}

// main function is required by cgo, but doesn't do anything nor is it ever called
func main() {
	//_ = initialize("test-db")
	//_, _ = invoke("StateOpen", `{"Root":"0x56e81f171bcc55a6ff8345e692c0f86e5b48e01b996cadc001622fb5e363b421"}`)
}
