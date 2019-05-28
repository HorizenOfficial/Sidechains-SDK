Issues:
-Conflict between python2 and python3: 
--->Using python3 the following error prompts when using AuthServiceProxy class: 
   "Unexpected exception caught during testing: getsockaddrarg: AF_INET address must be tuple, not int";
--->Using python2 I get: "Fatal Python error: initfsencoding: Unable to get the locale encoding
    File "/usr/lib/python2.7/encodings/__init__.py", line 123
    raise CodecRegistryError,\
                            ^
    SyntaxError: invalid syntax"
    Has something to do with Scorex.
 Note that using one of the two I don't get the other error and viceversa.
-Once bringing up a Scorex node, a way must be found to wait in order to get it fully up
-Once solved the previous issues surely new one will arises when dealing with AuthServiceProxy and Scorex API Calls
MC_Only test works with python2.