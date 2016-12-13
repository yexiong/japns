/**
 * 
 */
package com.notnoop.apns.internal;

import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.locks.ReentrantLock;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.MonitorConnection;
import com.notnoop.apns.ConnectionHolderAdapter;

/**
 * 循环更换本地ip和端口
 * 
 * @author sean@guoqude.com
 *
 */
public class LoopSwithConnectionHolder extends ConnectionHolderAdapter {
	private final static Logger logger = LoggerFactory.getLogger(LoopSwithConnectionHolder.class);

	private List<MonitorConnection> localAddresses = new ArrayList<MonitorConnection>();

	private int addressChoseIndex = 0;

	private ReentrantLock addressChoseLock = new ReentrantLock();

	public LoopSwithConnectionHolder(String ipPrefix, int[] ports) {
		List<InetAddress> addresses = LocalAddressHolder.get(ipPrefix);
		init(addresses, ports);
	}

	private void init(List<InetAddress> addresses, int[] ports) {
		if (addresses == null || addresses.size() == 0 || ports == null || ports.length == 0) {
			throw new IllegalArgumentException("address or ports invalid");
		}
		if (ports.length > 1000) {
			throw new IllegalArgumentException("ports length invalid:" + ports.length);
		}
		if (addresses.size() > 50) {
			throw new IllegalArgumentException("addresses length invalid:" + ports.length);
		}
		int i = 0;
		for (int port : ports) { // switch port first
			for (InetAddress address : addresses) { // switch ip second
				MonitorConnection localAddress = new MonitorConnection(address, address.getHostAddress(), port, i);
				localAddress.setConnectionHolder(this);
				localAddresses.add(localAddress);
				i++;
			}
		}
	}

	@Override
	public void returnAddress(MonitorConnection localAddress) throws IOException {
		addressChoseLock.lock();
		try {
			localAddress.setAvailable(true);
			logger.info("address return success {}", localAddress);
		} finally {
			addressChoseLock.unlock();
		}

	}

	/*
	 * 
	 * 不同的InetAddress可以用同一个端口去连同一个远程端口
	 * 
	 * @see com.notnoop.apns.LocalAddressSwitcher#getLocalAddress()
	 */
	@Override
	public MonitorConnection takeAddress() {
		int choseIndex = -1;
		MonitorConnection chosedAddress = null;
		boolean hasAvailable = false;
		int poolSize = localAddresses.size();
		addressChoseLock.lock();
		try {
			choseIndex = addressChoseIndex;
			for (int i = 0; i < poolSize; i++) {
				choseIndex = choseIndex % poolSize;
				MonitorConnection address = this.localAddresses.get(choseIndex);
				if (address.isAvailable()) {
					hasAvailable = true;
					address.setAvailable(false);
					chosedAddress = address;
					break;
				}
				choseIndex++;
			}
			if (hasAvailable) {
				addressChoseIndex = choseIndex + 1;
			}
		} finally {
			addressChoseLock.unlock();
		}
		logger.info("address chosed {}", chosedAddress);
		return chosedAddress;
	}
	
	@Override
	public void connect(MonitorConnection localAddress, String remoteHost, int remotePort, int readTimeout) throws IOException{
		Socket socket = getSocketFactory().createSocket(remoteHost, remotePort, localAddress.getAddress(), localAddress.getLocalPort());
		socket.setSoTimeout(readTimeout);
		socket.setKeepAlive(true);
		socket.setSoLinger(true, 0);
		localAddress.setSocket(socket);
	}

	

	public int getSize() {
		return this.localAddresses.size();
	}

}
