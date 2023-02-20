package com.horizen.utils;

import java.security.Provider;
import java.security.Security;

public class ChaChaPrngSecureRandomProvider extends Provider {
    protected static final String NAME = "HorizenCrypto";

    private static boolean providerInitialized = false;
    public static void init() {
        if (providerInitialized) {
            // one time only initialization
            return;
        }

        if (Security.getProvider(ChaChaPrngSecureRandomProvider.NAME) == null) {
            int pos = Security.addProvider(new ChaChaPrngSecureRandomProvider());
            if (pos < 0) {
                throw new RuntimeException("Could not add " + ChaChaPrngSecureRandomProvider.NAME + " provider for algorithm " + ChaChaPrngSecureRandom.ALGO);
            }
        }
        providerInitialized = true;
    }

    public ChaChaPrngSecureRandomProvider() {
        // Add here other cryptographic services to this provider if needed. Currently we have just one.
        super(NAME,
                "1.0",
                NAME +
                        " Provider (Implements a Cryptographically-Secure PRNG based on " +
                        ChaChaPrngSecureRandom.ALGO + ")");
        put("SecureRandom." + ChaChaPrngSecureRandom.ALGO, ChaChaPrngSecureRandom.class.getName());
        put("SecureRandom." + ChaChaPrngSecureRandom.ALGO + " ImplementedIn", "Software");
    }
}
