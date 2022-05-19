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
}
