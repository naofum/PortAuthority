package com.aaronjwood.portauthority.response;

import com.aaronjwood.portauthority.network.Host;

public interface MainAsyncResponse {

    /**
     * Delegate to handle Host outputs
     *
     * @param output
     */
    void processFinish(Host output);

    /**
     * Delegate to handle integer outputs
     *
     * @param output
     */
    void processFinish(int output);

    /**
     * Delegate to handle string outputs
     *
     * @param output
     */
    void processFinish(String output);
}
