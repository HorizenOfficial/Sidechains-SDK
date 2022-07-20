#include <stdlib.h>

void Free(void *ptr) {
    free(ptr);
}

typedef void (*logFunction)(char *msg);

// global log callback function pointer
static logFunction log = NULL;

// exported symbol to set the log function pointer
void RegisterLogCallback(logFunction callback) {
    log = callback;
}

// used by GO to invoke the log callback, as GO cannot invoke C function pointers
void invokeLog(char *msg) {
    if (log != NULL) log(msg);
}
