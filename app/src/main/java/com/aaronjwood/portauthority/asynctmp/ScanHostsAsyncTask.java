package com.aaronjwood.portauthority.async;

import android.os.AsyncTask;
import android.util.Log;

import com.aaronjwood.portauthority.response.MainAsyncResponse;
import com.aaronjwood.portauthority.runnable.ScanHostsRunnable;

import java.io.BufferedReader;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class ScanHostsAsyncTask extends AsyncTask<String, Void, ArrayList<Map<String, String>>> {

    private static final String TAG = "ScanHostsAsyncTask";
    private MainAsyncResponse delegate;

    public ScanHostsAsyncTask(MainAsyncResponse delegate) {
        this.delegate = delegate;
    }

    @Override
    protected ArrayList<Map<String, String>> doInBackground(String... params) {
        String ip = params[0];
        String parts[] = ip.split("\\.");

        ExecutorService executor = Executors.newFixedThreadPool(8);
        executor.execute(new ScanHostsRunnable(parts, 1, 31, delegate));
        executor.execute(new ScanHostsRunnable(parts, 32, 63, delegate));
        executor.execute(new ScanHostsRunnable(parts, 64, 95, delegate));
        executor.execute(new ScanHostsRunnable(parts, 96, 127, delegate));
        executor.execute(new ScanHostsRunnable(parts, 128, 159, delegate));
        executor.execute(new ScanHostsRunnable(parts, 160, 191, delegate));
        executor.execute(new ScanHostsRunnable(parts, 192, 223, delegate));
        executor.execute(new ScanHostsRunnable(parts, 224, 255, delegate));
        executor.shutdown();

        try {
            executor.awaitTermination(5, TimeUnit.SECONDS);
        }
        catch(InterruptedException e) {
            Log.e(TAG, e.getMessage());
        }

        return new ArrayList<>();
    }

    protected void onPostExecute(final ArrayList<Map<String, String>> result) {
        try {
            BufferedReader reader = new BufferedReader(new FileReader("/proc/net/arp"));
            reader.readLine();
            String line;

            while((line = reader.readLine()) != null) {
                String[] arpLine = line.split("\\s+");

                String ip = arpLine[0];
                String flag = arpLine[2];
                String macAddress = arpLine[3];

                if(!flag.equals("0x0") && !macAddress.equals("00:00:00:00:00:00")) {
                    Map<String, String> entry = new HashMap<>();
                    entry.put("First Line", ip);
                    entry.put("Second Line", macAddress);
                    result.add(entry);
                }
            }

            Collections.sort(result, new Comparator<Map<String, String>>() {

                @Override
                public int compare(Map<String, String> lhs, Map<String, String> rhs) {
                    return lhs.get("First Line").compareTo(rhs.get("First Line"));
                }
            });

            delegate.processFinish(result);
        }
        catch(FileNotFoundException e) {
            Log.e(TAG, e.getMessage());
        }
        catch(IOException e) {
            Log.e(TAG, e.getMessage());
        }
    }
}
