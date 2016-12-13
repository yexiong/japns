package com.notnoop.apns;

import java.io.FileInputStream;
import java.io.FileNotFoundException;

import com.notnoop.exceptions.InvalidSSLConfig;
import com.notnoop.exceptions.NetworkIOException;

public class MainClass {

	/**
	 * @param args
	 *            Program arguments
	 * @throws FileNotFoundException
	 * @throws InvalidSSLConfig
	 * @throws NetworkIOException 
	 */
	public static void main(final String[] args) throws InvalidSSLConfig, FileNotFoundException, NetworkIOException {
		if (args.length != 4) {
			System.err.println("Usage: test <p|s> <cert> <cert-password>\ntest p ./cert abc123 token");
			System.exit(777);
		}

		final ApnsDelegate delegate = new ApnsDelegateAdapter() {
			public void messageSent(final ApnsNotification message, final boolean resent) {
				System.out.println("Sent message " + message + " Resent: " + resent);
			}

			public void messageSendFailed(final ApnsNotification message, final Throwable e) {
				System.out.println("Failed message " + message);

			}

			public void connectionClosed(final DeliveryError e, final int messageIdentifier) {
				System.out.println("Closed connection: " + messageIdentifier + "\n   deliveryError " + e.toString());
			}

			public void cacheLengthExceeded(final int newCacheLength) {
				System.out.println("cacheLengthExceeded " + newCacheLength);

			}

			public void notificationsResent(final int resendCount) {
				System.out.println("notificationResent " + resendCount);
			}
		};

		final ApnsService svc = APNS.newService().withAppleDestination(args[0].equals("p")).withCert(new FileInputStream(args[1]), args[2]).withDelegate(delegate).build();

		final String goodToken = args[3];

		final String payload = APNS.newPayload().alertBody("Wrzlmbrmpf dummy alert").build();

		svc.start();
		System.out.println("Sending message");
		final ApnsNotification goodMsg = svc.push(goodToken, payload);
		System.out.println("Message id: " + goodMsg.getIdentifier());

		svc.stop();
	}
}
