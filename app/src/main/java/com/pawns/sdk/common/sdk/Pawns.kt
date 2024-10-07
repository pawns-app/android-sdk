package com.pawns.sdk.common.sdk

import android.app.Notification
import android.content.Context
import android.util.Log
import com.pawns.sdk.common.dto.ServiceConfig
import com.pawns.sdk.common.dto.ServiceState
import com.pawns.sdk.common.dto.ServiceType
import com.pawns.sdk.common.listener.PawnsServiceListener
import com.pawns.sdk.internal.dto.ServiceAction
import com.pawns.sdk.internal.logger.PawnsLogger
import com.pawns.sdk.internal.provider.DependencyProvider
import com.pawns.sdk.internal.service.PeerServiceBackground
import com.pawns.sdk.internal.service.PeerServiceForeground
import com.pawns.sdk.internal.util.DeviceIdHelper
import com.pawns.sdk.internal.util.SystemUtils
import com.pawns.ndk.PawnsCore
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow

public class Pawns private constructor(
    internal val apiKey: String?,
    internal val serviceConfig: ServiceConfig,
    internal val serviceType: ServiceType
) {

    public companion object {
        internal const val TAG = "PawnsSdk"

        private val uninitialisedInstance = Pawns(null, ServiceConfig(), ServiceType.BACKGROUND)

        @Volatile
        private var _instance: Pawns? = null

        internal val isInitialised: Boolean
            get() {
                return _instance != null && _instance?.apiKey != null && PawnsCore.isNdkLoaded
            }

        /**
         * PawnsSdk entry point to be used after initialization with Builder class and build method.
         */
        public fun getInstance(): Pawns {
            return if (isInitialised) {
                _instance ?: throw UninitializedPropertyAccessException("Internal instance was not initialised")
            } else {
                Log.e(TAG, "Instance was not initialised by Pawns.Builder")
                uninitialisedInstance
            }
        }
    }

    private constructor(builder: Builder) : this(
        builder.apiKey,
        builder.serviceConfig,
        builder.serviceType
    )

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
        internal var serviceType: ServiceType = ServiceType.FOREGROUND

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
        public fun serviceConfig(config: ServiceConfig): Builder =
            apply { this.serviceConfig = config }

        /**
         * Optional method. Allows to configure PawnsSdk internal logger.
         * Default value is false.
         * @param isEnabled turns on or off PawnsSdk logger.
         */
        public fun loggerEnabled(isEnabled: Boolean): Builder =
            apply { this.isLoggingEnabled = isEnabled }

        /**
         * Optional method. Allows to configure PawnsSdk service type.
         * Default value is SdkServiceType.FOREGROUND.
         * @param serviceType configures SDK to launch service behaving either as foreground or
         * background.
         */
        public fun serviceType(serviceType: ServiceType): Builder =
            apply { this.serviceType = serviceType }

        /**
         * Mandatory method. Final method to build PawnsSdk instance for later usage.
         */
        public fun build() {
            if (apiKey.isBlank()) {
                Log.e(TAG, "Api key has not been provided")
            }
            PawnsLogger.isEnabled = isLoggingEnabled
            val pawns = Pawns(this)
            pawns.init(context)
            _instance = pawns
        }
    }

    internal var dependencyProvider: DependencyProvider? = null
    internal var serviceListener: PawnsServiceListener? = null
    internal val _serviceState: MutableStateFlow<ServiceState> = MutableStateFlow(ServiceState.Off)
    internal val serviceState: StateFlow<ServiceState> = _serviceState

    private fun init(context: Context) {
        val deviceId = DeviceIdHelper.id(context)
        val deviceName = SystemUtils.getDeviceNameAndOsVersion()
        if (PawnsCore.isNdkLoaded) {
            PawnsCore.Initialize(deviceId, deviceName)
        } else {
            PawnsLogger.e(TAG, "Failed to initialise PawnsNdk")
        }
        dependencyProvider = DependencyProvider(context, serviceConfig)
    }

    /**
     * Option 1.
     * Coroutine approach.
     * It provides with latest updates of PawnsSdk service state.
     */
    public fun getServiceState(): StateFlow<ServiceState> {
        if (!isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, getServiceState returning ServiceState.Off")
            return MutableStateFlow(ServiceState.Off)
        }
        return serviceState
    }

    /**
     * Provides the latest known state of service
     */
    public fun getServiceStateSnapshot(): ServiceState {
        if (!isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, getServiceStateSnapshot returning ServiceState.Off")
            return ServiceState.Off
        }
        return serviceState.value
    }

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
        startSharing(context, null, null)
    }

    /**
     * Runs PawnsSdk Internet sharing service and starts updating its state.
     * Optional method to use in case you want a full control of notification that is displayed or already have a running foreground service
     * and you want to reuse/combine your notification instead of SDK displaying default notification.
     * @param notification - Notification object that must match your current foreground service notification (same channel)
     * @param notificationId - Same notification id that you passed to startForeground when launching your existing service
     */
    public fun startSharing(context: Context, notification: Notification?, notificationId: Int?) {
        if (!isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, make sure to initialise before using startSharing")
            return
        }
        if (!SystemUtils.isServiceRunning(context)) {
            when (getInstance().serviceType) {
                ServiceType.FOREGROUND -> {
                    if (notification == null || notificationId == null) {
                        dependencyProvider?.notificationManager?.initNotificationChannel()
                    } else {
                        dependencyProvider?.notificationManager?.setExternalNotification(notification, notificationId)
                    }
                    PeerServiceForeground.performAction(context, ServiceAction.START_PAWNS_SERVICE)
                }

                ServiceType.BACKGROUND -> PeerServiceBackground.performAction(context, ServiceAction.START_PAWNS_SERVICE)
            }
        }
    }

    /**
     * Stops PawnsSdk Internet sharing service.
     */
    public fun stopSharing(context: Context) {
        if (!isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, make sure to initialise before using stopSharing")
            return
        }
        if (SystemUtils.isServiceRunning(context)) {
            when (getInstance().serviceType) {
                ServiceType.FOREGROUND -> PeerServiceForeground.performAction(
                    context,
                    ServiceAction.STOP_PAWNS_SERVICE
                )

                ServiceType.BACKGROUND -> PeerServiceBackground.performAction(
                    context,
                    ServiceAction.STOP_PAWNS_SERVICE
                )
            }
        } else {
            getInstance()._serviceState.value = ServiceState.Off
            getInstance().serviceListener?.onStateChange(ServiceState.Off)
        }
    }


}