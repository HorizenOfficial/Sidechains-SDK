#include <stdlib.h>

void Free(void *ptr) {
    free(ptr);
}

typedef void (*logCallback)(char *msg);
typedef char* (*blockHashCallback)(int handle, char *blockNumber);

// global log callback function pointer
static logCallback logCb = NULL;
static blockHashCallback blockHashCb = NULL;

// exported symbol to set the log function pointer
void SetLogCallback(logCallback callback) {
    logCb = callback;
}

// exported symbol to set the block hash function pointer
void SetBlockHashCallback(blockHashCallback callback) {
    blockHashCb = callback;
}

// used by GO to invoke the log callback, as GO cannot invoke C function pointers
void invokeLogCallback(char *msg) {
    if (logCb != NULL) logCb(msg);
}

// used by GO to invoke the log callback, as GO cannot invoke C function pointers
char* invokeBlockHashCallback(int handle, char *blockNumber) {
    if (blockHashCb != NULL) return blockHashCb(handle, blockNumber);
    return NULL;
}
