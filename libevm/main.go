//go:build cgo

package main

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
// #include <stdlib.h>
import "C"
import (
	_ "embed"
	"github.com/ethereum/go-ethereum/log"
	"libevm/interop"
	"libevm/lib"
	"os"
	"unsafe"
)

const logLevel = log.LvlDebug

// instance holds a singleton of lib.Service
var instance *lib.Service

// static initializer
func init() {
	// initialize logger
	var logger = log.NewGlogHandler(log.StreamHandler(os.Stdout, log.TerminalFormat(true)))
	logger.Verbosity(logLevel)
	log.Root().SetHandler(logger)
	// initialize instance of our service
	instance = lib.New()
}

// main function is required by cgo, but doesn't do anything nor is it ever called
func main() {}

//export Free
func Free(ptr unsafe.Pointer) {
	C.free(ptr)
}

//export Invoke
func Invoke(method *C.char, args *C.char) *C.char {
	jsonString := interop.Invoke(instance, C.GoString(method), C.GoString(args))
	if jsonString == "" {
		return nil
	}
	return C.CString(jsonString)
}
