package com.notnoop.apns.sample;

import com.notnoop.apns.APNS;
import com.notnoop.apns.ApnsDelegateAdapter;
import com.notnoop.apns.ApnsService;
import com.notnoop.apns.internal.LoopSwithConnectionHolder;
import com.notnoop.exceptions.InvalidSSLConfig;
import com.notnoop.exceptions.RuntimeIOException;

import java.text.SimpleDateFormat;
import java.util.Date;

public class ApnsPoolSwitchSample {

    /**
     * @param args
     * @throws InterruptedException
     * @throws InvalidSSLConfig
     * @throws RuntimeIOException
     */
    public static void main(String[] args) throws InterruptedException, RuntimeIOException, InvalidSSLConfig {
        String certFile = "/Users/sean/workspace/ios/jy/000.p12";
        int[] ports = new int[]{9589, 9599, 9560};
        ApnsService apns = APNS.newService().withCert(certFile, "000").withProductionDestination() //
                .withLocalAddressSwitcher(new LoopSwithConnectionHolder("192.168", ports))//
                .withDelegate(new ApnsDelegateAdapter() {

                    @Override
                    public void connectionCreate(String localHost, int localPort) {
                        System.out.println(localHost + ":" + localPort);
                    }

                }).asPool(ports.length).build();

        Thread.sleep(1000);

        String token = "E19E90FCE8AF84196F0885B84739B1985441040326FB6073491BB04509B23BAF"; // test iphone token

        for (int i = 1; i < 10; i++) {
            try {
                long start = System.currentTimeMillis();
                apns.push(token, APNS.newPayload()
                        .sound("default") // 有声or有震动
                        .alertBody("SUCC " + i + ":" + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date())).build());
                System.out.println(i + " duration" + (System.currentTimeMillis() - start));
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        Thread.sleep(5000000);

        apns.stop();
    }

}
