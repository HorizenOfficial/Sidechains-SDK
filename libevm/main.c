#include <stdlib.h>

void Free(void *ptr) {
    free(ptr);
}

typedef char* (*callbackFunc)(int handle, char *msg);

// global callback function pointer
static callbackFunc callback = NULL;

// exported symbol to set the callback function pointer
void SetCallback(callbackFunc func) {
    callback = func;
}

// used by GO to invoke the callback, as GO cannot invoke C function pointers
char* invokeCallback(int handle, char *msg) {
    if (callback == NULL) return NULL;
    return callback(handle, msg);
}
