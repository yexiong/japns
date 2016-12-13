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
package com.notnoop.apns;

import static com.notnoop.apns.internal.Utilities.PRODUCTION_FEEDBACK_HOST;
import static com.notnoop.apns.internal.Utilities.PRODUCTION_FEEDBACK_PORT;
import static com.notnoop.apns.internal.Utilities.PRODUCTION_GATEWAY_HOST;
import static com.notnoop.apns.internal.Utilities.PRODUCTION_GATEWAY_PORT;
import static com.notnoop.apns.internal.Utilities.SANDBOX_FEEDBACK_HOST;
import static com.notnoop.apns.internal.Utilities.SANDBOX_FEEDBACK_PORT;
import static com.notnoop.apns.internal.Utilities.SANDBOX_GATEWAY_HOST;
import static com.notnoop.apns.internal.Utilities.SANDBOX_GATEWAY_PORT;
import static com.notnoop.apns.internal.Utilities.newSSLContext;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;
import java.security.KeyStore;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;

import com.notnoop.apns.internal.ApnsConnection;
import com.notnoop.apns.internal.ApnsConnectionImpl;
import com.notnoop.apns.internal.ApnsFeedbackConnection;
import com.notnoop.apns.internal.ApnsPooledConnection;
import com.notnoop.apns.internal.ApnsServiceImpl;
import com.notnoop.apns.internal.Utilities;
import com.notnoop.exceptions.InvalidSSLConfig;
import com.notnoop.exceptions.RuntimeIOException;

/**
 * The class is used to create instances of {@link ApnsService}.
 *
 * Note that this class is not synchronized.  If multiple threads access a
 * {@code ApnsServiceBuilder} instance concurrently, and at least on of the
 * threads modifies one of the attributes structurally, it must be
 * synchronized externally.
 *
 * Starting a new {@code ApnsService} is easy:
 *
 * <pre>
 *   ApnsService = APNS.newService()
 *    .withCert("/path/to/certificate.p12", "MyCertPassword")
 *    .withSandboxDestination()
 *    .build()
 * </pre>
 */
public class ApnsServiceBuilder {
    private static final String KEYSTORE_TYPE = "PKCS12";
    private static final String KEY_ALGORITHM = ((java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm") == null)? "sunx509" : java.security.Security.getProperty("ssl.KeyManagerFactory.algorithm"));

    private SSLContext sslContext;

    private int readTimeout = 0;

    private String gatewayHost;
    private int gatewayPort = -1;

    private String feedbackHost;
    private int feedbackPort;
    private int pooledMax = 1;
    private int cacheLength = ApnsConnection.DEFAULT_CACHE_LENGTH;
    private boolean autoAdjustCacheLength = true;
    private ExecutorService executor = null;

    private ConnectionHolder localAddressSwitcher = ConnectionHolder.EMPTY;
    
    private ApnsDelegate delegate = ApnsDelegate.EMPTY;

    /**
     * Constructs a new instance of {@code ApnsServiceBuilder}
     */
    public ApnsServiceBuilder() { sslContext = null; }

    /**
     * Specify the certificate used to connect to Apple APNS
     * servers.  This relies on the path (absolute or relative to
     * working path) to the keystore (*.p12) containing the
     * certificate, along with the given password.
     *
     * The keystore needs to be of PKCS12 and the keystore
     * needs to be encrypted using the SunX509 algorithm.  Both
     * of these settings are the default.
     *
     * This library does not support password-less p12 certificates, due to a
     * Oracle Java library <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6415637">
     * Bug 6415637</a>.  There are three workarounds: use a password-protected
     * certificate, use a different boot Java SDK implementation, or construct
     * the `SSLContext` yourself!  Needless to say, the password-protected
     * certificate is most recommended option.
     *
     * @param fileName  the path to the certificate
     * @param password  the password of the keystore
     * @return  this
     * @throws RuntimeIOException if it {@code fileName} cannot be
     *          found or read
     * @throws InvalidSSLConfig if fileName is invalid Keystore
     *  or the password is invalid
     */
    public ApnsServiceBuilder withCert(String fileName, String password)
    throws RuntimeIOException, InvalidSSLConfig {
        FileInputStream stream = null;
        try {
            stream = new FileInputStream(fileName);
            return withCert(stream, password);
        } catch (FileNotFoundException e) {
            throw new RuntimeIOException(e);
        } finally {
            Utilities.close(stream);
        }
    }

    /**
     * Specify the certificate used to connect to Apple APNS
     * servers.  This relies on the stream of keystore (*.p12)
     * containing the certificate, along with the given password.
     *
     * The keystore needs to be of PKCS12 and the keystore
     * needs to be encrypted using the SunX509 algorithm.  Both
     * of these settings are the default.
     *
     * This library does not support password-less p12 certificates, due to a
     * Oracle Java library <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6415637">
     * Bug 6415637</a>.  There are three workarounds: use a password-protected
     * certificate, use a different boot Java SDK implementation, or constract
     * the `SSLContext` yourself!  Needless to say, the password-protected
     * certificate is most recommended option.
     *
     * @param stream    the keystore represented as input stream
     * @param password  the password of the keystore
     * @return  this
     * @throws InvalidSSLConfig if stream is invalid Keystore
     *  or the password is invalid
     */
    public ApnsServiceBuilder withCert(InputStream stream, String password)
    throws InvalidSSLConfig {
        assertPasswordNotEmpty(password);
        return withSSLContext(
                newSSLContext(stream, password,
                        KEYSTORE_TYPE, KEY_ALGORITHM));
    }

    /**
     * Specify the certificate used to connect to Apple APNS
     * servers.  This relies on a keystore (*.p12)
     * containing the certificate, along with the given password.
     *
     * This library does not support password-less p12 certificates, due to a
     * Oracle Java library <a href="http://bugs.sun.com/bugdatabase/view_bug.do?bug_id=6415637">
     * Bug 6415637</a>.  There are three workarounds: use a password-protected
     * certificate, use a different boot Java SDK implementation, or construct
     * the `SSLContext` yourself!  Needless to say, the password-protected
     * certificate is most recommended option.
     *
     * @param keyStore  the keystore
     * @param password  the password of the keystore
     * @return  this
     * @throws InvalidSSLConfig if stream is invalid Keystore
     *  or the password is invalid
     */
    public ApnsServiceBuilder withCert(KeyStore keyStore, String password)
    throws InvalidSSLConfig {
        assertPasswordNotEmpty(password);
        return withSSLContext(
                newSSLContext(keyStore, password, KEY_ALGORITHM));
    }
    
	private void assertPasswordNotEmpty(String password) {
		if (password == null || password.length() == 0) {
            throw new IllegalArgumentException("Passwords must be specified." +
                    "Oracle Java SDK does not support passwordless p12 certificates");
        }
	}
    
    /**
     * Specify the SSLContext that should be used to initiate the
     * connection to Apple Server.
     *
     * Most clients would use {@link #withCert(InputStream, String)}
     * or {@link #withCert(String, String)} instead.  But some
     * clients may need to represent the Keystore in a different
     * format than supported.
     *
     * @param sslContext    Context to be used to create secure connections
     * @return  this
     */
    public ApnsServiceBuilder withSSLContext(SSLContext sslContext) {
        this.sslContext = sslContext;
        return this;
    }
    
    /**
     * Specify the timeout value to be set in new setSoTimeout in created
     * sockets, for both feedback and push connections, in milliseconds.
     * @param readTimeout timeout value to be set in new setSoTimeout
     * @return this
     */
    public ApnsServiceBuilder withReadTimeout(int readTimeout) {
    	this.readTimeout = readTimeout;
    	return this;
    }

    /**
     * Specify the gateway server for sending Apple iPhone
     * notifications.
     *
     * Most clients should use {@link #withSandboxDestination()}
     * or {@link #withProductionDestination()}.  Clients may use
     * this method to connect to mocking tests and such.
     *
     * @param host  hostname the notification gateway of Apple
     * @param port  port of the notification gateway of Apple
     * @return  this
     */
    public ApnsServiceBuilder withGatewayDestination(String host, int port) {
        this.gatewayHost = host;
        this.gatewayPort = port;
        return this;
    }

    /**
     * Specify the Feedback for getting failed devices from
     * Apple iPhone Push servers.
     *
     * Most clients should use {@link #withSandboxDestination()}
     * or {@link #withProductionDestination()}.  Clients may use
     * this method to connect to mocking tests and such.
     *
     * @param host  hostname of the feedback server of Apple
     * @param port  port of the feedback server of Apple
     * @return this
     */
    public ApnsServiceBuilder withFeedbackDestination(String host, int port) {
        this.feedbackHost = host;
        this.feedbackPort = port;
        return this;
    }

    /**
     * Specify to use Apple servers as iPhone gateway and feedback servers.
     *
     * If the passed {@code isProduction} is true, then it connects to the
     * production servers, otherwise, it connects to the sandbox servers
     *
     * @param isProduction  determines which Apple servers should be used:
     *               production or sandbox
     * @return this
     */
    public ApnsServiceBuilder withAppleDestination(boolean isProduction) {
        if (isProduction) {
            return withProductionDestination();
        } else {
            return withSandboxDestination();
        }
    }

    /**
     * Specify to use the Apple sandbox servers as iPhone gateway
     * and feedback servers.
     *
     * This is desired when in testing and pushing notifications
     * with a development provision.
     *
     * @return  this
     */
    public ApnsServiceBuilder withSandboxDestination() {
        return withGatewayDestination(SANDBOX_GATEWAY_HOST, SANDBOX_GATEWAY_PORT)
        .withFeedbackDestination(SANDBOX_FEEDBACK_HOST, SANDBOX_FEEDBACK_PORT);
    }

    /**
     * Specify to use the Apple Production servers as iPhone gateway
     * and feedback servers.
     *
     * This is desired when sending notifications to devices with
     * a production provision (whether through App Store or Ad hoc
     * distribution).
     *
     * @return  this
     */
    public ApnsServiceBuilder withProductionDestination() {
        return withGatewayDestination(PRODUCTION_GATEWAY_HOST, PRODUCTION_GATEWAY_PORT)
        .withFeedbackDestination(PRODUCTION_FEEDBACK_HOST, PRODUCTION_FEEDBACK_PORT);
    }

    /**
     * Specify the local address switcher for the socket connection
     * 
     * @param laSwitcher
     * @return
     */
    public ApnsServiceBuilder withLocalAddressSwitcher(ConnectionHolder laSwitcher) {
    	this.localAddressSwitcher = laSwitcher;
    	return this;
    }
    
    /**
     * Specify if the notification cache should auto adjust.
     * Default is true
     * 
     * @param autoAdjustCacheLength the notification cache should auto adjust.
     * @return this
     */
    public ApnsServiceBuilder withAutoAdjustCacheLength(boolean autoAdjustCacheLength) {
        this.autoAdjustCacheLength = autoAdjustCacheLength;
        return this;
    }

    /**
     * Specify the number of notifications to cache for error purposes.
     * Default is 100
     * 
     * @param cacheLength  Number of notifications to cache for error purposes
     * @return  this
     */
    public ApnsServiceBuilder withCacheLength(int cacheLength) {
        this.cacheLength = cacheLength;
        return this;
    }

    /**
     * Constructs a pool of connections to the notification servers.
     *
     * Apple servers recommend using a pooled connection up to
     * 15 concurrent persistent connections to the gateways.
     *
     * Note: This option has no effect when using non-blocking
     * connections.
     */
    public ApnsServiceBuilder asPool(int maxConnections) {
        return asPool(Executors.newFixedThreadPool(maxConnections), maxConnections);
    }

    /**
     * Constructs a pool of connections to the notification servers.
     *
     * Apple servers recommend using a pooled connection up to
     * 15 concurrent persistent connections to the gateways.
     *
     * Note: This option has no effect when using non-blocking
     * connections.
     *
     * Note: The maxConnections here is used as a hint to how many connections
     * get created.
     */
    public ApnsServiceBuilder asPool(ExecutorService executor, int maxConnections) {
        this.pooledMax = maxConnections;
        this.executor = executor;
        return this;
    }

    /**
     * Sets the delegate of the service, that gets notified of the
     * status of message delivery.
     *
     * Note: This option has no effect when using non-blocking
     * connections.
     */
    public ApnsServiceBuilder withDelegate(ApnsDelegate delegate) {
        this.delegate = delegate == null ? ApnsDelegate.EMPTY : delegate;
        return this;
    }

    /**
     * Returns a fully initialized instance of {@link ApnsService},
     * according to the requested settings.
     *
     * @return  a new instance of ApnsService
     */
    public ApnsService build() {
        checkInitialization();
        ApnsService service;

        SSLSocketFactory sslFactory = sslContext.getSocketFactory();
       
        
        localAddressSwitcher.setSocketFactory(sslFactory);

        ApnsConnection conn = new ApnsConnectionImpl(gatewayHost,
            gatewayPort, localAddressSwitcher,
                delegate, cacheLength,
                autoAdjustCacheLength, readTimeout);
        if (pooledMax != 1) {
            conn = new ApnsPooledConnection(conn, pooledMax, executor);
        }

        service = new ApnsServiceImpl(conn);

        service.start();

        return service;
    }
    
	public ApnsFeedbackConnection buildFeedback() {
		SSLSocketFactory sslFactory = sslContext.getSocketFactory();
		ApnsFeedbackConnection feedback = new ApnsFeedbackConnection(sslFactory, feedbackHost, feedbackPort, readTimeout);
		return feedback;
	}

    private void checkInitialization() {
        if (sslContext == null)
            throw new IllegalStateException(
                    "SSL Certificates and attribute are not initialized\n"
                    + "Use .withCert() methods.");
        if (gatewayHost == null || gatewayPort == -1)
            throw new IllegalStateException(
                    "The Destination APNS server is not stated\n"
                    + "Use .withDestination(), withSandboxDestination(), "
                    + "or withProductionDestination().");
    }
}
