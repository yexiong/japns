/**
 * 
 */
package com.notnoop.apns.internal;

import java.io.IOException;
import java.net.Socket;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ConnectionHolderAdapter;
import com.notnoop.apns.MonitorConnection;

/**
 * 
 * @author sean@guoqude.com
 *
 */
public class DefaultConnectionHolder extends ConnectionHolderAdapter {
	private static final Logger logger = LoggerFactory.getLogger(DefaultConnectionHolder.class);

	public DefaultConnectionHolder() {
	}

	@Override
	public MonitorConnection takeAddress() {
		MonitorConnection conn = new MonitorConnection();
		conn.setAvailable(false);
		conn.setConnectionHolder(this);
		return conn;
	}
	
	@Override
	public void returnAddress(MonitorConnection localAddress) throws IOException {
		localAddress.setAvailable(true);
		logger.info("address return success {}", localAddress);
	}

	@Override
	public void connect(MonitorConnection localAddress, String remoteHost, int remotePort, int readTimeout) throws IOException {
		Socket socket = getSocketFactory().createSocket(remoteHost, remotePort);
		socket.setSoTimeout(readTimeout);
		socket.setKeepAlive(true);
		localAddress.setSocket(socket);
		localAddress.setLocalHost(socket.getLocalAddress().getHostAddress());
		localAddress.setLocalPort(socket.getLocalPort());
	}

}
