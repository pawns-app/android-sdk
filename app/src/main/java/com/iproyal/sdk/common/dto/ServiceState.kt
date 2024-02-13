package com.iproyal.sdk.common.dto


/**
 * Exhaustive list of all available Internet sharing states
 */
public sealed class ServiceState {
    /**
     * Indicates that service was started and is currently initialising
     */
    public object On : ServiceState()

    /**
     * Indicates that service is not running and is stopped
     */
    public object Off : ServiceState()

    /**
     * Indicates that service responsible for sharing internet has finished initialisation
     * and is launching internet sharing flow
     */
    public sealed class Launched : ServiceState() {

        /**
         * Internet sharing flow is running and working as expected
         */
        public object Running: Launched()

        /**
         * Service is running, but hosting device has low battery
         * Conditions: Battery level <20% and device is not charging
         */
        public object LowBattery: Launched()

        /**
         * Indicates that service was launched, but something unexpected happened
         * @param error provides more information on available errors and their reason
         * @see ServiceError
         */
        public data class Error(val error: ServiceError) : Launched()
    }



}