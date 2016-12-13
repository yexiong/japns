package com.notnoop.apns.internal;

import org.junit.Test;

import static org.mockito.Mockito.*;

import com.notnoop.apns.ApnsService;
import com.notnoop.apns.EnhancedApnsNotification;
import com.notnoop.exceptions.NetworkIOException;

public class ApnsServiceImplTest {

    EnhancedApnsNotification notification = new EnhancedApnsNotification(1,
            EnhancedApnsNotification.MAXIMUM_EXPIRY, "2342", "{}");

    @Test
    public void pushEventually() throws NetworkIOException {
        ApnsConnection connection = mock(ApnsConnection.class);
        ApnsService service = newService(connection);

        service.push(notification);

        verify(connection, times(1)).sendMessage(notification);
    }

    @Test
    public void pushEventuallySample() throws NetworkIOException {
        ApnsConnection connection = mock(ApnsConnection.class);
        ApnsService service = newService(connection);

        service.push("2342", "{}");

        verify(connection, times(1)).sendMessage(notification);
    }

    protected ApnsService newService(ApnsConnection connection) {
        return new ApnsServiceImpl(connection);
    }
}
