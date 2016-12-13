/**
 * 
 */
package com.notnoop.apns;

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.Socket;
import java.util.LinkedList;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.internal.Utilities;
import com.notnoop.exceptions.ApnsDeliveryErrorException;

/**
 * 表示本地ip和端口
 * 
 * @author sean@guoqude.com
 *
 */
public class MonitorConnection {
	private static final Logger logger = LoggerFactory.getLogger(MonitorConnection.class);
	
	public static final MonitorConnection DEFAULT = new MonitorConnection();

	/**
	 * 本地address
	 */
	private InetAddress address;

	/**
	 * 本地端口
	 */
	private int port;

	/**
	 * 本地host
	 */
	private String host;

	/**
	 * 是否可用
	 */
	private volatile boolean available = true;

	/**
	 * 在资源池里的位置
	 */
	private int index;

	/**
	 * 链接
	 */
	private Socket socket;

	/**
	 * 是否启动监听线程
	 */
	private volatile boolean monitor = false;
	
	private static ThreadFactory threadFactory = getMonitorThreadFactory();
	
	private ConnectionHolder connectionHolder;
	
	private final ConcurrentLinkedQueue<ApnsNotification> cachedNotifications;

	public MonitorConnection(InetAddress address, String host, int port, int index) {
		this();
		this.address = address;
		this.host = host;
		this.port = port;
		this.index = index;
		
	}

	public MonitorConnection() {
		this.cachedNotifications = new ConcurrentLinkedQueue<ApnsNotification>();
	}

	public InetAddress getAddress() {
		return address;
	}

	public void setAddress(InetAddress address) {
		this.address = address;
	}

	public int getPort() {
		return port;
	}

	public void setLocalPort(int port) {
		this.port = port;
	}

	public boolean isSocketClosed() {
		return (this.socket == null || this.socket.isClosed());
	}

	public void setAvailable(boolean available) {
		this.available = available;
	}
	
	public boolean isAvailable() {
		return this.available;
	}

	public int getIndex() {
		return index;
	}

	public void setIndex(int index) {
		this.index = index;
	}

	@Override
	public String toString() {
		return "LocalAddress [address=" + address + ", socket=" + socket  +", host=" + host + ", port=" + port + ", available=" + available + ", monitor=" + monitor + ", index=" + index + "]";
	}

	public void closeSocket() throws IOException {
		if (this.socket != null) {
			logger.info("closing socket {}", this);
			this.socket.close();
		}
	}
	
	
	
	public void cacheNotification(ApnsNotification notification) {
		cachedNotifications.add(notification);
		while (cachedNotifications.size() > cacheLength) {
			cachedNotifications.poll();
			logger.debug("Removing notification from cache " + notification);
		}
	}
	
	final static int EXPECTED_SIZE = 6;
	
	private Thread monitorThread;

	private int cacheLength;
	
	public void setCacheLength(int cacheLength) {
		this.cacheLength = cacheLength;
	}

	public void startMonitor(final ApnsDelegate delegate, final boolean autoAdjustCacheLength, final ConcurrentLinkedQueue<ApnsNotification> notificationsBuffer) {
		if (monitorThread != null) {
			try {
				monitorThread.join();
			} catch (InterruptedException e) {
				Utilities.sleep(5);
			}
		}
		
		monitorThread = threadFactory.newThread(new Runnable() {

			@Override
			public void run() {
				logger.debug("Launching Monitoring Thread for socket {}", this);
				try {
					byte[] bytes = new byte[EXPECTED_SIZE];
					while (readResponse(bytes)) {
						logger.debug("Error-response packet {}", Utilities.encodeHex(bytes));
						// Quickly close socket, so we won't ever try to send push notifications
						// using the defective socket.
						returnAddress();

						int command = bytes[0] & 0xFF;
						if (command != 8) {
							throw new IOException("Unexpected command byte " + command);
						}
						int statusCode = bytes[1] & 0xFF;
						DeliveryError e = DeliveryError.ofCode(statusCode);

						int id = Utilities.parseBytes(bytes[2], bytes[3], bytes[4], bytes[5]);

						logger.debug("Closed connection cause={}; id={}", e, id);
						delegate.connectionClosed(e, id);

						Queue<ApnsNotification> tempCache = new LinkedList<ApnsNotification>();
						ApnsNotification notification = null;
						boolean foundNotification = false;

						while (!cachedNotifications.isEmpty()) {
							notification = cachedNotifications.poll();
							logger.debug("Candidate for removal, message id {}", notification.getIdentifier());

							if (notification.getIdentifier() == id) {
								logger.debug("Bad message found {}", notification.getIdentifier());
								foundNotification = true;
								break;
							}
							tempCache.add(notification);
						}

						if (foundNotification) {
							logger.debug("delegate.messageSendFailed, message id {}", notification.getIdentifier());
							delegate.messageSendFailed(notification, new ApnsDeliveryErrorException(e));
						} else {
							cachedNotifications.addAll(tempCache);
							int resendSize = tempCache.size();
							logger.warn("Received error for message that wasn't in the cache...");
							if (autoAdjustCacheLength) {
								cacheLength = cacheLength + (resendSize / 2);
								delegate.cacheLengthExceeded(cacheLength);
							}
							logger.debug("delegate.messageSendFailed, unknown id");
							delegate.messageSendFailed(null, new ApnsDeliveryErrorException(e));
						}

						int resendSize = 0;

						while (!cachedNotifications.isEmpty()) {

							resendSize++;
							final ApnsNotification resendNotification = cachedNotifications.poll();
							logger.debug("Queuing for resend {}", resendNotification.getIdentifier());
							notificationsBuffer.add(resendNotification);
						}
						delegate.notificationsResent(resendSize);
					}
					logger.debug("Monitoring input stream closed by EOF");

				} catch (IOException e) {
					// An exception when reading the error code is non-critical, it will cause another retry
					// sending the message. Other than providing a more stable network connection to the APNS
					// server we can't do much about it - so let's not spam the application's error log.
					logger.info("Exception while waiting for error code", e);
					delegate.connectionClosed(DeliveryError.UNKNOWN, -1);
				} finally {
					returnAddress();
					setMonitor(false);
				}
			}

		});
		monitorThread.setName("MonitoringThread-" + getLocalHost() + ":"+ getLocalPort());
		monitorThread.start();
		
		// waiting for monitor staring
		while(isMonitor()) {
			Utilities.sleep(1);
		}
	}

	public boolean readResponse(byte[] bytes) throws IOException {
		InputStream in;
		try {
			in = socket.getInputStream();
		} catch (IOException ioe) {
			in = null;
		}

		setMonitor(true);
		
		if (in != null && readPacket(in, bytes)) {
			return true;
		}
		return false;
	}
	
	

	/**
	 * Read a packet like in.readFully(bytes) does - but do not throw an exception and return false if nothing could be read at all.
	 * 
	 * @param in
	 *            the input stream
	 * @param bytes
	 *            the array to be filled with data
	 * @return true if a packet as been read, false if the stream was at EOF right at the beginning.
	 * @throws IOException
	 *             When a problem occurs, especially EOFException when there's an EOF in the middle of the packet.
	 */
	private boolean readPacket(final InputStream in, final byte[] bytes) throws IOException {
		final int len = bytes.length;
		int n = 0;
		while (n < len) {
			try {
				int count = in.read(bytes, n, len - n);
				if (count < 0) {
					throw new EOFException("EOF after reading " + n + " bytes of new packet.");
				}
				n += count;
			} catch (IOException ioe) {
				if (n == 0)
					return false;
				throw new IOException("Error after reading " + n + " bytes of packet", ioe);
			}
		}
		return true;
	}
	
	private static ThreadFactory getMonitorThreadFactory() {
		return new ThreadFactory() {
			ThreadFactory wrapped = Executors.defaultThreadFactory();

			@Override
			public Thread newThread(Runnable r) {
				Thread result = wrapped.newThread(r);
				result.setDaemon(true);
				return result;
			}
		};
	}
	
	public  void returnAddress() {
		try {
			if (!isSocketClosed()) { // socket is open
				closeSocket();
			}
			connectionHolder.returnAddress(this);
		} catch (final IOException e) {
			logger.debug("error while closing socket", e);
		}
	}


	public String getLocalHost() {
		return this.host;
	}

	public void setLocalHost(String host) {
		this.host = host;
	}

	public int getLocalPort() {
		return this.port;
	}

	public void send(ApnsNotification m) throws IOException {
		socket.getOutputStream().write(m.marshall());
		socket.getOutputStream().flush();
	}

	public void setSocket(Socket socket) {
		this.socket = socket;
	}

	private boolean isMonitor() {
		return this.monitor;
	}

	private void setMonitor(boolean monitor) {
		this.monitor = monitor;
	}

	public ConnectionHolder getConnectionHolder() {
		return connectionHolder;
	}

	public  void setConnectionHolder(ConnectionHolder connectionHolder) {
		this.connectionHolder = connectionHolder;
	}

}
