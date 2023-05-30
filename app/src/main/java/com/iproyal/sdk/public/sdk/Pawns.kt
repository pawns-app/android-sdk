package com.iproyal.sdk.public.sdk

import android.content.Context
import android.util.Log
import com.iproyal.sdk.public.dto.ServiceConfig
import com.iproyal.sdk.internal.dto.ServiceAction
import com.iproyal.sdk.internal.logger.PawnsLogger
import com.iproyal.sdk.public.dto.ServiceState
import com.iproyal.sdk.internal.provider.DependencyProvider
import com.iproyal.sdk.public.listener.PawnsServiceListener
import com.iproyal.sdk.internal.service.PeerService
import com.iproyal.sdk.internal.util.DeviceIdHelper
import com.iproyal.sdk.internal.util.SystemUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import mobile_sdk.Mobile_sdk

public class Pawns private constructor(
    context: Context,
    internal val apiKey: String,
    internal val serviceConfig: ServiceConfig,
) {

    public companion object {
        internal const val TAG = "PawnsSdk"

        /**
         * PawnsSdk entry point to be used after initialization with Builder class and build method.
         */
        @Volatile
        public lateinit var instance: Pawns
            private set
    }

    private constructor(builder: Builder) : this(builder.context, builder.apiKey, builder.serviceConfig)

    /**
     * PawnsSdk initialisation method. Recommended to be used while Application is starting.
     * within onCreate() method of Application.
     * @param context from Application extending class.
     * @see android.app.Application
     */
    public class Builder(internal val context: Context) {
        internal var apiKey: String = ""
        internal var serviceConfig: ServiceConfig = ServiceConfig()
        internal var isLoggingEnabled: Boolean = false

        /**
         * Mandatory method. It allows SDK to recognise and authorize the use of our service.
         * @param key Api key received from SDK representatives.
         */
        public fun apiKey(key: String): Builder = apply { this.apiKey = key }

        /**
         * Optional method. It provides notification manager with parameters to tailor service.
         * notification displayed to users, when service is running.
         * @param config Notification configuration parameters.
         */
        public fun serviceConfig(config: ServiceConfig): Builder = apply { this.serviceConfig = config }

        /**
         * Optional method. Allows to configure PawnsSdk internal logger.
         * Default value is false.
         * @param isEnabled turns on or off PawnsSdk logger.
         */
        public fun loggerEnabled(isEnabled: Boolean): Builder = apply { this.isLoggingEnabled = isEnabled }

        /**
         * Mandatory method. Final method to build PawnsSdk instance for later usage.
         */
        public fun build() {
            if (apiKey.isBlank()) {
                Log.e(TAG, "Api key has not been provided")
            }
            PawnsLogger.isEnabled = isLoggingEnabled
            val pawns = Pawns(this)
            instance = pawns
        }
    }

    init {
        val deviceId = DeviceIdHelper.id(context)
        val deviceName = SystemUtils.getDeviceNameAndOsVersion()
        Mobile_sdk.initialize(deviceId, deviceName)
    }

    internal val dependencyProvider: DependencyProvider = DependencyProvider(context)
    internal var serviceListener: PawnsServiceListener? = null
    internal val _serviceState: MutableStateFlow<ServiceState> = MutableStateFlow(ServiceState.Off)

    /**
     * Option 1.
     * Coroutine approach.
     * It provides with latest updates of PawnsSdk service state.
     */
    public val serviceState: StateFlow<ServiceState> = _serviceState

    /**
     * Option 2.
     * Listener approach.
     * Registers a listener for PawnsSdk service to trigger when updating its state.
     */
    public fun registerListener(listener: PawnsServiceListener) {
        serviceListener = listener
        listener.onStateChange(serviceState.value)
    }

    /**
     * Option 2.
     * Listener approach.
     * Removes a listener for PawnsSdk service.
     */
    public fun unregisterListener() {
        serviceListener = null
    }

    /**
     * Runs PawnsSdk Internet sharing service and starts updating its state.
     */
    public fun startSharing(context: Context) {
        if (!SystemUtils.isServiceRunning(context)) {
            PeerService.performAction(context, ServiceAction.START_PAWNS_SERVICE)
        }
    }

    /**
     * Stops PawnsSdk Internet sharing service.
     */
    public fun stopSharing(context: Context) {
        if (SystemUtils.isServiceRunning(context)) {
            PeerService.performAction(context, ServiceAction.STOP_PAWNS_SERVICE)
        } else {
            instance._serviceState.value = ServiceState.Off
            instance.serviceListener?.onStateChange(ServiceState.Off)
        }
    }

}