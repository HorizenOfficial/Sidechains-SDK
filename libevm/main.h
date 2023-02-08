// used by GO to invoke the log callback, as GO cannot invoke C function pointers
void invokeLogCallback(char *msg);

// used by GO to invoke the log callback, as GO cannot invoke C function pointers
char* invokeBlockHashCallback(int handle, char *blockNumber);
