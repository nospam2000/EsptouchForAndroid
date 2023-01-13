package com.espressif.iot.esptouch2.provision;

import android.net.ConnectivityManager;
import android.net.DhcpInfo;
import android.net.LinkAddress;
import android.net.LinkProperties;
import android.net.Network;
import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.DatagramSocket;
import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.net.UnknownHostException;
import java.util.Enumeration;
import java.util.List;

public class TouchNetUtil {
    public static boolean isWifiConnected(WifiManager wifiManager) {
        WifiInfo wifiInfo = wifiManager.getConnectionInfo();
        return wifiInfo != null
                && wifiInfo.getNetworkId() != -1
                && !"<unknown ssid>".equals(wifiInfo.getSSID());
    }

    public static byte[] getRawSsidBytes(WifiInfo info) {
        try {
            Method method = info.getClass().getMethod("getWifiSsid");
            method.setAccessible(true);
            Object wifiSsid = method.invoke(info);
            if (wifiSsid == null) {
                return null;
            }
            method = wifiSsid.getClass().getMethod("getOctets");
            method.setAccessible(true);
            return (byte[]) method.invoke(wifiSsid);
        } catch (NoSuchMethodException | IllegalAccessException | InvocationTargetException | NullPointerException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static byte[] getRawSsidBytesOrElse(WifiInfo info, byte[] orElse) {
        byte[] raw = getRawSsidBytes(info);
        return raw != null ? raw : orElse;
    }

    public static String getSsidString(WifiInfo info) {
        String ssid = info.getSSID();
        if (ssid.startsWith("\"") && ssid.endsWith("\"")) {
            ssid = ssid.substring(1, ssid.length() - 1);
        }
        return ssid;
    }

    public static InetAddress getBroadcastAddress(WifiManager wifi, ConnectivityManager connectivity) {
        Network network = connectivity.getActiveNetwork();
        LinkAddress linkAddr = getLinkAddressFromLinkProperties(connectivity.getLinkProperties(network));
        if (linkAddr != null) {
            int netmask = getNetmaskFromLinkAddress(linkAddr);
            int ipAddress = getIpAddressFromLinkAddress(linkAddr);

            int broadcast = (ipAddress & netmask) | ~netmask;
            byte[] quads = new byte[4];
            for (int k = 0; k < 4; k++) {
                quads[k] = (byte) ((broadcast >> k * 8) & 0xFF);
            }
            try {
                return InetAddress.getByAddress(quads);
            } catch (UnknownHostException e) {
                e.printStackTrace();
            }
        }

        try {
            return InetAddress.getByName("255.255.255.255");
        } catch (UnknownHostException e) {
            e.printStackTrace();
        }
        // Impossible arrive here
        return null;
    }

    private static LinkAddress getLinkAddressFromLinkProperties(LinkProperties linkProperties) {
        List<LinkAddress> addresses = linkProperties.getLinkAddresses();
        for (LinkAddress address : addresses) {
            if (address.getAddress() instanceof Inet4Address) {
                return address;
            }
        }
        return null;
    }

    private static int getNetmaskFromLinkAddress(LinkAddress address) {
        int prefix = address.getPrefixLength();
        return 0xFFFFFFFF << (32 - prefix);
    }

    private static int getIpAddressFromLinkAddress(LinkAddress address) {
        int result = 0;
        for (byte b : address.getAddress().getAddress()) {
            result <<= 8;
            result |= b;
        }
        return result;
    }

    public static boolean is5G(int frequency) {
        return frequency > 4900 && frequency < 5900;
    }

    public static InetAddress getAddress(int ipAddress) {
        byte[] ip = new byte[]{
                (byte) (ipAddress & 0xff),
                (byte) ((ipAddress >> 8) & 0xff),
                (byte) ((ipAddress >> 16) & 0xff),
                (byte) ((ipAddress >> 24) & 0xff)
        };

        try {
            return InetAddress.getByAddress(ip);
        } catch (UnknownHostException e) {
            e.printStackTrace();
            // Impossible arrive here
            return null;
        }
    }

    private static InetAddress getAddress(boolean isIPv4) {
        try {
            Enumeration<NetworkInterface> enums = NetworkInterface.getNetworkInterfaces();
            while (enums.hasMoreElements()) {
                NetworkInterface ni = enums.nextElement();
                Enumeration<InetAddress> addrs = ni.getInetAddresses();
                while (addrs.hasMoreElements()) {
                    InetAddress address = addrs.nextElement();
                    if (!address.isLoopbackAddress()) {
                        if (isIPv4 && address instanceof Inet4Address) {
                            return address;
                        }
                        if (!isIPv4 && address instanceof Inet6Address) {
                            return address;
                        }
                    }
                }
            }
        } catch (SocketException e) {
            e.printStackTrace();
        }
        return null;
    }

    public static InetAddress getIPv4Address() {
        return getAddress(true);
    }

    public static InetAddress getIPv6Address() {
        return getAddress(false);
    }

    /**
     * @param bssid the bssid like aa:bb:cc:dd:ee:ff
     * @return byte array converted from bssid
     */
    public static byte[] convertBssid2Bytes(String bssid) {
        String[] bssidSplits = bssid.split(":");
        if (bssidSplits.length != 6) {
            throw new IllegalArgumentException("Invalid bssid format");
        }
        byte[] result = new byte[bssidSplits.length];
        for (int i = 0; i < bssidSplits.length; i++) {
            result[i] = (byte) Integer.parseInt(bssidSplits[i], 16);
        }
        return result;
    }

    public static DatagramSocket createUdpSocket() {
        for (int port = 23233; port < 0xffff; ++port) {
            try {
                return new DatagramSocket(port);
            } catch (SocketException ignored) {
            }
        }

        return null;
    }

}
