package main

// // the following will be compiled by CGO and linked into the GO binary
// // this is not just a comment!
// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
// #include <stdlib.h>
// #include "main.h"
import "C"
import (
	"fmt"
	"github.com/ethereum/go-ethereum/log"
	"libevm/interop"
	"libevm/lib"
	"unsafe"
)

const logLevel = log.LvlDebug

// instance holds a singleton of lib.Service
var instance *lib.Service

var logFormatter = log.JSONFormatEx(false, false)

func logToCallback(r *log.Record) error {
	// see comments on stack.Call.Format for available format specifiers
	r.Ctx = append(r.Ctx,
		// path of source file
		"file", fmt.Sprintf("%+s", r.Call),
		// line number
		"line", fmt.Sprintf("%d", r.Call),
		// function name (without additional path qualifiers because the filename will already be qualified)
		"fn", fmt.Sprintf("%n", r.Call),
	)
	msg := C.CString(string(logFormatter.Format(r)))
	defer C.free(unsafe.Pointer(msg))
	C.invokeLog(msg)
	return nil
}

// static initializer
func init() {
	// initialize logger
	var logger = log.NewGlogHandler(log.FuncHandler(logToCallback))
	//var logger = log.NewGlogHandler(log.StreamHandler(os.Stdout, log.TerminalFormat(true)))
	logger.Verbosity(logLevel)
	log.Root().SetHandler(logger)
	// initialize instance of our service
	instance = lib.New()
}

// main function is required by cgo, but doesn't do anything nor is it ever called
func main() {
}

//export Invoke
func Invoke(method *C.char, args *C.char) *C.char {
	jsonString := interop.Invoke(instance, C.GoString(method), C.GoString(args))
	if jsonString == "" {
		return nil
	}
	return C.CString(jsonString)
}
