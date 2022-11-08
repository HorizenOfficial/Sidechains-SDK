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

// instance holds a singleton of lib.Service
var instance *lib.Service

// initialize logger
var logger = log.NewGlogHandler(log.FuncHandler(logToCallback))
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
	// set default log level to trace
	logger.Verbosity(log.LvlTrace)
	log.Root().SetHandler(logger)
	// initialize instance of our service
	instance = lib.New()
}

// main function is required by cgo, but doesn't do anything nor is it ever called
func main() {
}

//export SetLogLevel
func SetLogLevel(level *C.char) {
	parsedLevel, err := log.LvlFromString(C.GoString(level))
	if err != nil {
		log.Error("unable to parse log level", "error", err)
		return
	}
	logger.Verbosity(parsedLevel)
}

//export Invoke
func Invoke(method *C.char, args *C.char) *C.char {
	jsonString := interop.Invoke(instance, C.GoString(method), C.GoString(args))
	if jsonString == "" {
		return nil
	}
	return C.CString(jsonString)
}
