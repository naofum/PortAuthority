package com.aaronjwood.portauthority.async;

import android.os.AsyncTask;

import com.aaronjwood.portauthority.network.Host;
import com.aaronjwood.portauthority.response.MainAsyncResponse;
import com.aaronjwood.portauthority.runnable.ScanHostsRunnable;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.lang.ref.WeakReference;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jcifs.netbios.NbtAddress;

public class ScanHostsAsyncTask extends AsyncTask<Integer, Void, Void> {
    private final WeakReference<MainAsyncResponse> delegate;
    private static final String ARP_INCOMPLETE = "0x0";
    private static final String ARP_INACTIVE = "00:00:00:00:00:00";
    private static final int NETBIOS_FILE_SERVER = 0x20;

    /**
     * Constructor to set the delegate
     *
     * @param delegate Called when host discovery has finished
     */
    public ScanHostsAsyncTask(MainAsyncResponse delegate) {
        this.delegate = new WeakReference<>(delegate);
    }

    /**
     * Scans for active hosts on the network
     *
     * @param params IP address
     */
    @Override
    protected Void doInBackground(Integer... params) {
        int ipv4 = params[0];
        int cidr = params[1];
        int timeout = params[2];

        ExecutorService executor = Executors.newCachedThreadPool();

        double hostBits = 32.0d - cidr; // How many bits do we have for the hosts.
        int netmask = (0xffffffff >> (32 - cidr)) << (32 - cidr); // How many bits for the netmask.
        int numberOfHosts = (int) Math.pow(2.0d, hostBits) - 2; // 2 ^ hostbits = number of hosts in integer.
        int firstAddr = (ipv4 & netmask) + 1; // AND the bits we care about, then first addr.

        int SCAN_THREADS = (int) hostBits;
        int chunk = (int) Math.ceil((double) numberOfHosts / SCAN_THREADS); // Chunk hosts by number of threads.
        int previousStart = firstAddr;
        int previousStop = firstAddr + (chunk - 2); // Ignore network + first addr

        for (int i = 0; i < SCAN_THREADS; i++) {
            executor.execute(new ScanHostsRunnable(previousStart, previousStop, timeout, delegate));
            previousStart = previousStop + 1;
            previousStop = previousStart + (chunk - 1);
        }

        executor.shutdown();

        try {
            executor.awaitTermination(5, TimeUnit.MINUTES);
            executor.shutdownNow();
        } catch (InterruptedException ignored) {
        }

        publishProgress();
        return null;
    }

    /**
     * Scans the ARP table and updates the list with hosts on the network
     * Resolves both DNS and NetBIOS
     * Don't update the UI in onPostExecute since we want to do multiple UI updates here
     * onPostExecute seems to perform all UI updates at once which would hinder what we're doing here
     * TODO: this method is gross, refactor it and break it up
     *
     * @param params
     */
    @Override
    protected final void onProgressUpdate(final Void... params) {
        BufferedReader reader = null;
        try {
            ExecutorService executor = Executors.newCachedThreadPool();
            reader = new BufferedReader(new FileReader("/proc/net/arp"));
            reader.readLine();
            String line;

            while ((line = reader.readLine()) != null) {
                String[] arpLine = line.split("\\s+");

                final String ip = arpLine[0];
                final String flag = arpLine[2];
                final String macAddress = arpLine[3];

                if (!ARP_INCOMPLETE.equals(flag) && !ARP_INACTIVE.equals(macAddress)) {
                    executor.execute(new Runnable() {
                        @Override
                        public void run() {
                            Host host = new Host(ip, macAddress);
                            try {
                                InetAddress add = InetAddress.getByName(ip);
                                String hostname = add.getCanonicalHostName();
                                host.setHostname(hostname);

                                MainAsyncResponse activity = delegate.get();
                                if (activity != null) {
                                    activity.processFinish(host);
                                }
                            } catch (UnknownHostException ignored) {
                                return;
                            }

                            try {
                                NbtAddress[] netbios = NbtAddress.getAllByAddress(ip);
                                for (NbtAddress addr : netbios) {
                                    if (addr.getNameType() == NETBIOS_FILE_SERVER) {
                                        host.setHostname(addr.getHostName());
                                        return;
                                    }
                                }
                            } catch (UnknownHostException ignored) {
                            }
                        }
                    });
                }
            }
            executor.shutdown();
        } catch (IOException ignored) {
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (IOException ignored) {
            }
        }
    }
}
