package com.notnoop.apns.internal;

import static org.mockito.Matchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import com.notnoop.apns.ApnsNotification;
import com.notnoop.exceptions.NetworkIOException;

public class ApnsPooledConnectionTest {

    private ApnsConnection errorPrototype;
    
    private ApnsConnection prototype;

    private ExecutorService executorService;

    @Before
    public void setup() throws NetworkIOException {
        errorPrototype = mock(ApnsConnection.class);
        when(errorPrototype.copy()).thenReturn(errorPrototype);
        doThrow(NetworkIOException.class).when(errorPrototype).sendMessage(any(ApnsNotification.class));

        prototype = mock(ApnsConnection.class);
        when(prototype.copy()).thenReturn(prototype);
    }

    @After
    public void cleanup() {
        if (executorService != null) {
            executorService.shutdownNow();
        }
    }

    @Test(expected = NetworkIOException.class)
    public void testSendMessage() throws Exception {
        ApnsPooledConnection conn = new ApnsPooledConnection(errorPrototype, 1, getSingleThreadExecutor());
        conn.sendMessage(mock(ApnsNotification.class));
        conn.close();
    }

    @Test
    public void testCopyCalls() throws Exception {
        ApnsPooledConnection conn = new ApnsPooledConnection(prototype, 1, getSingleThreadExecutor());
        for (int i = 0; i < 10; i++) {
            conn.sendMessage(mock(ApnsNotification.class));
        }
        verify(prototype, times(1)).copy();
        conn.close();
    }

    @Test
    public void testCloseCalls() throws Exception {
        ApnsPooledConnection conn = new ApnsPooledConnection(prototype, 1, getSingleThreadExecutor());
        conn.sendMessage(mock(ApnsNotification.class));
        conn.close();
        // should be closed twice because of the thread local copy
        verify(prototype, times(2)).close();
    }

    private ExecutorService getSingleThreadExecutor() {
        executorService = Executors.newSingleThreadExecutor();
        return executorService;
    }
}