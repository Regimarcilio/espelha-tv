package br.com.espelhatv.util;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

class NetworkInfoTest {

    @Test
    void returnsFallbackOrSiteLocalIpv4() {
        String ip = NetworkInfo.localIpv4();
        assertNotNull(ip);
        assertFalse(ip.isBlank());
        assertFalse(ip.contains(":"));
    }

    @Test
    void ignoresContainerAndVpnInterfaces() {
        assertTrue(NetworkInfo.isLikelyVirtual("docker0"));
        assertTrue(NetworkInfo.isLikelyVirtual("br-507ceb6d50ce"));
        assertTrue(NetworkInfo.isLikelyVirtual("veth1234"));
        assertTrue(NetworkInfo.isLikelyVirtual("tailscale0"));
        assertTrue(NetworkInfo.isLikelyVirtual("tun0"));
        assertFalse(NetworkInfo.isLikelyVirtual("enp1s0"));
        assertFalse(NetworkInfo.isLikelyVirtual("wlan0"));
    }
}
