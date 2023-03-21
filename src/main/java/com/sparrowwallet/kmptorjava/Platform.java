package com.sparrowwallet.kmptorjava;

import java.security.AccessController;
import java.security.PrivilegedAction;

/**
 * Represents operating system with appropriate properties
 *
 */
public enum Platform {

    WINDOWS("windows"), //$NON-NLS-1$
    OSX("mac"), //$NON-NLS-1$
    UNIX("unix"), //$NON-NLS-1$
    UNKNOWN(""); //$NON-NLS-1$

    private static Platform current = getCurrentPlatform();

    private String platformId;

    Platform( String platformId ) {
        this.platformId = platformId;
    }

    /**
     * Returns platform id. Usually used to specify platform dependent styles
     * @return platform id
     */
    public String getPlatformId() {
        return platformId;
    }

    /**
     * @return the current OS.
     */
    public static Platform getCurrent() {
        return current;
    }

    private static Platform getCurrentPlatform() {
        String osName = System.getProperty("os.name");
        if ( osName.startsWith("Windows") ) return WINDOWS;
        if ( osName.startsWith("Mac") )     return OSX;
        if ( osName.startsWith("SunOS") )   return UNIX;
        if ( osName.startsWith("Linux") ) {
            String javafxPlatform = System.getProperty("javafx.platform");
            if (! ( "android".equals(javafxPlatform) || "Dalvik".equals(System.getProperty("java.vm.name")) ) ) // if not Android
                return UNIX;
        }
        return UNKNOWN;
    }

}

