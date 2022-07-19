//go:build cgo

package main

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
//#include <stdlib.h>
//
//void Free(void *ptr) {
//    free(ptr);
//}
//
//typedef void (*logFunction)(char *msg);
//
//// global log callback function pointer
//static logFunction log = NULL;
//
//// exported symbol to set the log function pointer
//void RegisterLogCallback(logFunction callback) {
//    log = callback;
//}
//
//// used by GO to invoke the log callback, as GO cannot invoke C function pointers
//void invokeLog(char *msg) {
//    if (log != NULL) log(msg);
//}
import "C"
import (
	"github.com/ethereum/go-ethereum/log"
	"libevm/lib"
	"unsafe"
)

const logLevel = log.LvlDebug

// instance holds a singleton of lib.Service
var instance *lib.Service

var logFormatter = log.JSONFormatEx(false, false)

func logToCallback(r *log.Record) error {
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
