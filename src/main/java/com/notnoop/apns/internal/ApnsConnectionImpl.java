/*
 * Copyright 2009, Mahmood Ali.
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are
 * met:
 *
 *   * Redistributions of source code must retain the above copyright
 *     notice, this list of conditions and the following disclaimer.
 *   * Redistributions in binary form must reproduce the above
 *     copyright notice, this list of conditions and the following disclaimer
 *     in the documentation and/or other materials provided with the
 *     distribution.
 *   * Neither the name of Mahmood Ali. nor the names of its
 *     contributors may be used to endorse or promote products derived from
 *     this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR
 * A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT
 * OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT NOT
 * LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS OF USE,
 * DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED AND ON ANY
 * THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY, OR TORT
 * (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.notnoop.apns.internal;

import java.io.IOException;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.atomic.AtomicInteger;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.notnoop.apns.ApnsDelegate;
import com.notnoop.apns.ApnsNotification;
import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.apns.MonitorConnection;
import com.notnoop.apns.ConnectionHolder;
import com.notnoop.exceptions.NetworkIOException;

public class ApnsConnectionImpl implements ApnsConnection {

	private static final Logger logger = LoggerFactory.getLogger(ApnsConnectionImpl.class);

	private final String host;
	private final int port;
	private final int readTimeout;
	private final ConnectionHolder connectionHolder;
	private final ApnsDelegate delegate;
	private int cacheLength;
	private final boolean autoAdjustCacheLength;
	private final ConcurrentLinkedQueue<ApnsNotification> notificationsBuffer;
	private MonitorConnection conn;
	private volatile boolean resenderClosed = false;

	private boolean isClosed() {
		if (resenderClosed) {
			return true;
		}
		return false;
	}

	private static final AtomicInteger resenderThreadId = new AtomicInteger(0);

	public ApnsConnectionImpl(String host, int port) {
		this(host, port, ConnectionHolder.EMPTY, ApnsDelegate.EMPTY);
	}

	private ApnsConnectionImpl(String host, int port, //
			ConnectionHolder addressSwitcher, //
			ApnsDelegate delegate) {
		this(host, port, //
				addressSwitcher, //
				delegate, ApnsConnection.DEFAULT_CACHE_LENGTH, //
				true, ApnsConnection.DEFAULT_READ_TIMEOUT);
	}

	public ApnsConnectionImpl(String host, int port, //
			ConnectionHolder addressSwitcher, //
			ApnsDelegate delegate, int cacheLength, //
			boolean autoAdjustCacheLength, int readTimeout) {
		this.host = host;
		this.port = port;
		this.connectionHolder = addressSwitcher;
		this.delegate = delegate == null ? ApnsDelegate.EMPTY : delegate;
		this.cacheLength = cacheLength;
		this.autoAdjustCacheLength = autoAdjustCacheLength;
		this.readTimeout = readTimeout;
		this.notificationsBuffer = new ConcurrentLinkedQueue<ApnsNotification>();
		this.startResender();
//		try {
//			this.connect(false);
//		} catch (IOException e) {
//			throw new IllegalStateException(e);
//		}
	}

	public void close() {
		this.resenderClosed = true;
		while (true) {
			Utilities.sleep(10);
			if (isClosed()) {
				break;
			}
		}
		returnAddress();
	}

	private void returnAddress() {
		if (conn != null) {
			conn.returnAddress();
		}
	}

	private Thread resenderThread = null;

	private static final int DELAY_IN_RESENDER = 10;

	private void startResender() {
		logger.info("Launching Resender Thread");
		resenderThread = new Thread(new Runnable() {

			public void run() {
				while (true) { // starting status
					brainBuffer();

					if (resenderClosed) {
						break;
					}

					Utilities.sleep(DELAY_IN_RESENDER);
				}
			}
		}, "ResenderThread-" + resenderThreadId.getAndIncrement());
		resenderThread.start();
	}

	private synchronized void connect(boolean resend) throws IOException {
		if (conn == null || conn.isSocketClosed()) { // connection return
			try {

				while (true) {
					conn = connectionHolder.takeAddress();
					if (conn != null) {
						break;
					}
					Utilities.sleep(1);
				}

				connectionHolder.connect(conn, host, port, readTimeout);
				conn.setCacheLength(cacheLength);
				conn.startMonitor(delegate, autoAdjustCacheLength, notificationsBuffer);

				this.delegate.connectionCreate(conn.getLocalHost(), conn.getLocalPort());
				logger.debug("Made a new connection to APNS {}", conn);
			} catch (IOException e) {
				logger.error("Couldn't connect to APNS server " + conn, e);
				// indicate to clients whether this is a resend or initial send
				throw e;
			}
		}
	}

	public synchronized void brainBuffer() {
		while (!notificationsBuffer.isEmpty()) {
			final ApnsNotification notification = notificationsBuffer.poll();
			try {
				sendMessage(notification, true);
			} catch (NetworkIOException ex) {
				// at this point we are retrying the submission of messages but failing to connect to APNS,
				// therefore notify the client of this
				delegate.messageSendFailed(notification, ex);
			}
		}
	}

	public synchronized void sendMessage(ApnsNotification m) throws NetworkIOException {
		sendMessage(m, false);
	}

	private static final int DELAY_IN_MS = 1000;
	private static final int RETRIES = 3;

	private synchronized void sendMessage(ApnsNotification m, boolean fromBuffer) throws NetworkIOException {
		delegate.startSending(m, fromBuffer);

		int attempts = 0;
		while (true) {
			try {
				attempts++;
				connect(fromBuffer);
				conn.send(m);
				logger.info("fromBuffer: {}, Message sended {} ", fromBuffer, m);
				conn.cacheNotification(m);

				delegate.messageSent(m, fromBuffer);

				attempts = 0;
				break;
			} catch (IOException e) {
				returnAddress();
				if (attempts >= RETRIES) {
					logger.error("Couldn't send message after " + RETRIES + " retries." + m, e);
					delegate.messageSendFailed(m, e);
					throw new NetworkIOException(e);
				}
				// The first failure might be due to closed connection (which in turn might be caused by
				// a message containing a bad token), so don't delay for the first retry.
				//
				// Additionally we don't want to spam the log file in this case, only after the second retry
				// which uses the delay.

				if (attempts != 1) {
					logger.info("Failed to send message " + m + "... trying again after delay", e);
					Utilities.sleep(DELAY_IN_MS);
				}
			}
		}
	}

	public ApnsConnectionImpl copy() {
		return new ApnsConnectionImpl(host, port, connectionHolder, delegate, //
				cacheLength, autoAdjustCacheLength, readTimeout);
	}

	public void testConnection() throws NetworkIOException {
		ApnsConnectionImpl testConnection = null;
		try {
			testConnection = new ApnsConnectionImpl(host, port, connectionHolder, delegate);
			final ApnsNotification notification = new EnhancedApnsNotification(0, 0, new byte[] { 0 }, new byte[] { 0 });
			testConnection.sendMessage(notification);
		} finally {
			if (testConnection != null) {
				testConnection.close();
			}
		}
	}

	public void setCacheLength(int cacheLength) {
		this.cacheLength = cacheLength;
	}

	public int getCacheLength() {
		return cacheLength;
	}

}
