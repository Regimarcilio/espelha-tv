package br.com.espelhatv.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

class NetworkInfoTest {

    @Test
    void returnsFallbackOrSiteLocalIpv4() {
        String ip = NetworkInfo.localIpv4();
        assertNotNull(ip);
        assertFalse(ip.isBlank());
        assertFalse(ip.contains(":"));
    }
}
