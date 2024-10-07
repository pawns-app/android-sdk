package com.pawns.sdk.common.listener

import com.pawns.sdk.common.dto.ServiceState

/**
 * Optional interface.
 * Internet sharing callback interface
 */
public interface PawnsServiceListener {
    /**
     * Service state change method. Provides updates with the latest state of service
     * @param state represent all available states
     * @see ServiceState for all available service states
     */
    public fun onStateChange(state: ServiceState)
}