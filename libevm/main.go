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
var logCallbackHandle int

func callback(handle int, args string) string {
	argsStr := C.CString(args)
	defer C.free(unsafe.Pointer(argsStr))
	var result *C.char
	result = C.invokeCallback(C.int(handle), argsStr)
	defer C.free(unsafe.Pointer(result))
	if result == nil {
		return ""
	}
	return C.GoString(result)
}

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
	msg := string(logFormatter.Format(r))
	callback(logCallbackHandle, msg)
	return nil
}

// static initializer
func init() {
	// set default log level to trace
	logger.Verbosity(log.LvlTrace)
	log.Root().SetHandler(logger)
	// initialize instance of our service
	instance = lib.New()
	lib.SetCallbackProxy(callback)
}

// main function is required by cgo, but doesn't do anything nor is it ever called
func main() {
}

//export SetupLogging
func SetupLogging(handle int, level *C.char) {
	parsedLevel, err := log.LvlFromString(C.GoString(level))
	if err != nil {
		log.Error("unable to parse log level", "error", err)
		return
	}
	logger.Verbosity(parsedLevel)
	logCallbackHandle = handle
}

//export Invoke
func Invoke(method *C.char, args *C.char) *C.char {
	jsonString := interop.Invoke(instance, C.GoString(method), C.GoString(args))
	if jsonString == "" {
		return nil
	}
	return C.CString(jsonString)
}
