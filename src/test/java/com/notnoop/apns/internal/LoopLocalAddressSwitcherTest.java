/**
 * 
 */
package com.notnoop.apns.internal;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import org.junit.Assert;
import org.junit.Test;

import com.notnoop.apns.MonitorConnection;

/**
 * @author sean@guoqude.com
 *
 */
public class LoopLocalAddressSwitcherTest {

	@Test
	public void testTakeAddressUntilEmpty() {
		LoopSwithConnectionHolder switcher = new LoopSwithConnectionHolder("", new int[] { 9998, 9999 });
		int size = switcher.getSize();
		for (int i = 0; i < size; i++) {
			MonitorConnection address = switcher.takeAddress();
			Assert.assertNotNull(address);
		}
		Assert.assertNull(switcher.takeAddress());
	}
	
	@Test
	public void testTakeAndReturnAddress() throws IOException {
		LoopSwithConnectionHolder switcher = new LoopSwithConnectionHolder("", new int[] { 9998});
		int size = switcher.getSize();
		
		List<MonitorConnection> addresses = new ArrayList<MonitorConnection>();
		for (int i = 0; i < size; i++) {
			MonitorConnection address = switcher.takeAddress();
			Assert.assertNotNull(address);
			addresses.add(address);
		}
		Assert.assertNull(switcher.takeAddress());
		
		for (MonitorConnection address: addresses) {
			switcher.returnAddress(address);
		}
		
		for (int i = 0; i < size; i++) {
			MonitorConnection address = switcher.takeAddress();
			Assert.assertNotNull(address);
			System.out.println(address.getAddress().getHostAddress() + ":" + address.getPort());
		}
		Assert.assertNull(switcher.takeAddress());
	}

}
