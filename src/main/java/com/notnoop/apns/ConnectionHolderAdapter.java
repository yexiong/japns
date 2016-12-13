/**
 * 
 */
package com.notnoop.apns;

import javax.net.SocketFactory;


/**
 * @author sean@guoqude.com
 *
 */
public abstract class ConnectionHolderAdapter implements ConnectionHolder {

	private SocketFactory socketFactory;


	@Override
	public void setSocketFactory(SocketFactory socketFactory) {
		this.socketFactory = socketFactory;
	}
	
	protected SocketFactory getSocketFactory() {
		return this.socketFactory;
	}

}
