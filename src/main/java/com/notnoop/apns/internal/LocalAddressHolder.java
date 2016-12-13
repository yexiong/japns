package com.notnoop.apns.internal;

import java.net.InetAddress;
import java.net.NetworkInterface;
import java.net.SocketException;
import java.util.ArrayList;
import java.util.Enumeration;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

public class LocalAddressHolder {


	public static List<InetAddress> get(String ipPrefix) {
		List<InetAddress> localAddresses = new ArrayList<InetAddress>();
		Enumeration<NetworkInterface> enumeration = null;
		try {
			enumeration = NetworkInterface.getNetworkInterfaces();
		} catch (SocketException e) {
			throw new IllegalStateException(e);
		}
		while (enumeration.hasMoreElements()) {
			NetworkInterface networkInterface = enumeration.nextElement();
			try {
				if (networkInterface.isUp()) {
					Enumeration<InetAddress> addressEnumeration = networkInterface.getInetAddresses();
					while (addressEnumeration.hasMoreElements()) {
						InetAddress address = addressEnumeration.nextElement();
						String ip = address.getHostAddress();
						if (StringUtils.startsWith(ip, ipPrefix)) {
							localAddresses.add(address);
						}
					}
				}
			} catch (SocketException e) {
				throw new IllegalStateException(e);
			}
		}
		return localAddresses;
	}

}
