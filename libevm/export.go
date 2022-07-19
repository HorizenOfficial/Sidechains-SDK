//go:build cgo

package main

// #cgo CFLAGS: -g -Wall -O3 -fpic -Werror
import "C"
import "libevm/interop"

//export Invoke
func Invoke(method *C.char, args *C.char) *C.char {
	jsonString := interop.Invoke(instance, C.GoString(method), C.GoString(args))
	if jsonString == "" {
		return nil
	}
	return C.CString(jsonString)
}
