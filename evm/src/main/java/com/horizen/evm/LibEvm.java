package com.horizen.evm;

import com.fasterxml.jackson.databind.type.TypeFactory;
import com.sun.jna.Native;
import com.sun.jna.Pointer;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

final class LibEvm {
    static native void Free(Pointer ptr);

    private static native void SetCallbackProxy(LibEvmCallback.CallbackProxy callback);

    private static native void SetupLogging(int callbackHandle, String level);

    private static native JsonPointer Invoke(String method, JsonPointer args);

    private static final Logger logger = LogManager.getLogger();
    private static final GlogCallback logCallback = new GlogCallback(logger);

    private static String getOSLibExtension() {
        var os = System.getProperty("os.name").toLowerCase();
        if (os.contains("mac os")) {
            return "dylib";
        } else if (os.contains("windows")) {
            return "dll";
        }
        // default to linux file extension
        return "so";
    }

    static {
        var libName = "libevm." + getOSLibExtension();
        logger.info("loading library: {}", libName);
        // bind native methods in this class to libevm
        Native.register(libName);
        // register callback
        SetCallbackProxy(LibEvmCallback.proxy);
        // propagate log4j log level to glog
        SetupLogging(logCallback.handle, GlogCallback.log4jToGlogLevel(logger.getLevel()));
    }

    private LibEvm() {
        // prevent instantiation of this class
    }

    private static class InteropResult<R> {
        public String error;
        public R result;

        public boolean isError() {
            return !error.isEmpty();
        }

        @Override
        public String toString() {
            if (!error.isEmpty()) {
                return String.format("error: %s", error);
            }
            return "success";
        }
    }

    /**
     * Invoke function that has arguments and a return value.
     */
    static <R> R invoke(String method, JsonPointer args, Class<R> responseType) {
        var json = Invoke(method, args);
        // build type information to deserialize to generic type InteropResult<R>
        var type = TypeFactory.defaultInstance().constructParametricType(InteropResult.class, responseType);
        InteropResult<R> response = json.deserialize(type);
        if (response.isError()) {
            throw new LibEvmException(response.error, method, args);
        }
        return response.result;
    }

    /**
     * Invoke function that has no arguments, but a return value.
     */
    static <R> R invoke(String method, Class<R> responseType) {
        return invoke(method, null, responseType);
    }

    /**
     * Invoke function that has arguments, but no return value.
     */
    static void invoke(String method, JsonPointer args) {
        invoke(method, args, Void.class);
    }

    /**
     * Invoke function that has no arguments and no return value.
     */
    static void invoke(String method) {
        invoke(method, null, Void.class);
    }
}
