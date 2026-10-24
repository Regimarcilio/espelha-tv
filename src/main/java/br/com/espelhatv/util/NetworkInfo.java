package br.com.espelhatv.util;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.Enumeration;
import java.util.Locale;

/** Descoberta simples do IPv4 da LAN para exibir a URL no console. */
public final class NetworkInfo {
    private NetworkInfo() {
    }

    public static String localIpv4() {
        String routed = defaultRouteIpv4();
        if (routed != null) {
            return routed;
        }

        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            if (interfaces == null) {
                return "127.0.0.1";
            }
            while (interfaces.hasMoreElements()) {
                NetworkInterface network = interfaces.nextElement();
                if (!network.isUp() || network.isLoopback() || network.isVirtual()
                        || isLikelyVirtual(network.getName())) {
                    continue;
                }
                Enumeration<InetAddress> addresses = network.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && address.isSiteLocalAddress()) {
                        return address.getHostAddress();
                    }
                }
            }
        } catch (SocketException ignored) {
            // cai no fallback
        }
        return "127.0.0.1";
    }

    /**
     * Conecta um socket UDP a um endereço TEST-NET apenas para o kernel escolher
     * a interface/rota de saída. Nenhum pacote é enviado.
     */
    private static String defaultRouteIpv4() {
        try (DatagramSocket socket = new DatagramSocket()) {
            socket.connect(new InetSocketAddress(InetAddress.getByName("192.0.2.1"), 9));
            InetAddress local = socket.getLocalAddress();
            if (local instanceof Inet4Address && local.isSiteLocalAddress()
                    && !local.isLoopbackAddress()) {
                return local.getHostAddress();
            }
        } catch (IOException | RuntimeException ignored) {
            // sem rota padrão; usa a varredura de interfaces
        }
        return null;
    }

    static boolean isLikelyVirtual(String interfaceName) {
        String name = interfaceName.toLowerCase(Locale.ROOT);
        return name.startsWith("docker")
                || name.startsWith("br-")
                || name.startsWith("veth")
                || name.startsWith("virbr")
                || name.startsWith("vmnet")
                || name.startsWith("cni")
                || name.startsWith("flannel")
                || name.startsWith("podman")
                || name.startsWith("lxc")
                || name.startsWith("lxd")
                || name.startsWith("kube")
                || name.startsWith("tun")
                || name.startsWith("tap")
                || name.startsWith("wg")
                || name.startsWith("zt")
                || name.startsWith("tailscale");
    }
}
