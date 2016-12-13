package com.notnoop.apns.integration;

import static com.notnoop.apns.internal.ApnsFeedbackParsingUtils.checkParsedSimple;
import static com.notnoop.apns.internal.ApnsFeedbackParsingUtils.checkParsedThree;
import static com.notnoop.apns.internal.ApnsFeedbackParsingUtils.simple;
import static com.notnoop.apns.internal.ApnsFeedbackParsingUtils.three;
import static com.notnoop.apns.utils.FixedCertificates.LOCALHOST;
import static com.notnoop.apns.utils.FixedCertificates.clientContext;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.fail;

import java.io.IOException;
import java.net.SocketTimeoutException;

import javax.net.ssl.SSLContext;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.notnoop.apns.APNS;
import com.notnoop.apns.internal.ApnsFeedbackConnection;
import com.notnoop.apns.utils.ApnsServerStub;
import com.notnoop.exceptions.NetworkIOException;

public class FeedbackTest {

    ApnsServerStub server;
    SSLContext clientContext = clientContext();


    @Before
    public void startup() {
        server = ApnsServerStub.prepareAndStartServer();
    }

    @After
    public void tearDown() {
        server.stop();
        server = null;
    }

    @Test
    public void simpleFeedback() throws NetworkIOException, IOException {
        server.getToSend().write(simple);

        ApnsFeedbackConnection service =
            APNS.newService().withSSLContext(clientContext)
            .withGatewayDestination(LOCALHOST, server.getEffectiveGatewayPort())
            .withFeedbackDestination(LOCALHOST, server.getEffectiveFeedbackPort())
            .buildFeedback();

        checkParsedSimple(service.getInactiveDevices());
    }
    
    @Test
    public void simpleFeedbackWithoutTimeout() throws IOException, NetworkIOException {
        server.getToSend().write(simple);
        server.getToWaitBeforeSend().set(2000);
        ApnsFeedbackConnection service =
            APNS.newService().withSSLContext(clientContext)
            .withGatewayDestination(LOCALHOST, server.getEffectiveGatewayPort())
            .withFeedbackDestination(LOCALHOST, server.getEffectiveFeedbackPort())
            .withReadTimeout(3000)
            .buildFeedback();

        checkParsedSimple(service.getInactiveDevices());
    }

    @Test()
    public void simpleFeedbackWithTimeout() throws IOException, NetworkIOException {
        server.getToSend().write(simple);
        server.getToWaitBeforeSend().set(5000);
        ApnsFeedbackConnection service =
            APNS.newService().withSSLContext(clientContext)
            .withGatewayDestination(LOCALHOST, server.getEffectiveGatewayPort())
            .withFeedbackDestination(LOCALHOST, server.getEffectiveFeedbackPort())
            .withReadTimeout(1000)
            .buildFeedback();
        try {
            service.getInactiveDevices();
            fail("RuntimeException expected");
        }
        catch(RuntimeException e) {
            assertEquals("Socket timeout exception expected", 
                    SocketTimeoutException.class, e.getCause().getClass() );
        }
    }

    @Test
    public void threeFeedback() throws IOException, NetworkIOException {
        server.getToSend().write(three);

        ApnsFeedbackConnection service =
            APNS.newService().withSSLContext(clientContext)
            .withGatewayDestination(LOCALHOST, server.getEffectiveGatewayPort())
            .withFeedbackDestination(LOCALHOST, server.getEffectiveFeedbackPort())
            .buildFeedback();

        checkParsedThree(service.getInactiveDevices());
    }


}
