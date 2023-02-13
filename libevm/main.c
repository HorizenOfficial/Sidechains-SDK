#include <stdlib.h>

void Free(void *ptr) {
    free(ptr);
}

typedef char* (*callbackProxy)(int handle, char *args);

// global callback function pointer
static callbackProxy proxy = NULL;

// exported symbol to set the callback function pointer
void SetCallbackProxy(callbackProxy func) {
    proxy = func;
}

// used by GO to invoke the callback, as GO cannot invoke C function pointers
char* invokeCallbackProxy(int handle, char *args) {
    if (proxy == NULL) return NULL;
    return proxy(handle, args);
}
