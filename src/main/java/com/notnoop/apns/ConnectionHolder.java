/**
 * 
 */
package com.notnoop.apns;

import java.io.IOException;

import javax.net.SocketFactory;

import com.notnoop.apns.internal.DefaultConnectionHolder;

/**
 * 本地ip和端口切换
 * 
 * @author sean@guoqude.com
 *
 */
public interface ConnectionHolder {
	public static final ConnectionHolder EMPTY = new DefaultConnectionHolder();

	/**
	 * 取到本地ip和端口
	 * 
	 * @return null if address is empty
	 */
	MonitorConnection takeAddress();

	/**
	 * 释放本地ip和端口资源
	 * 
	 * @param localAddress
	 * @return
	 * @throws IOException 
	 */
	void returnAddress(MonitorConnection localAddress) throws IOException;
	
	/**
	 * @param socketFactory
	 */
	void setSocketFactory(SocketFactory socketFactory);

	/**
	 * @param localAddress
	 * @param remoteHost
	 * @param remotePort
	 * @param readTimeout
	 * @throws IOException
	 */
	void connect(MonitorConnection localAddress, String remoteHost, int remotePort, int readTimeout) throws IOException;


}
