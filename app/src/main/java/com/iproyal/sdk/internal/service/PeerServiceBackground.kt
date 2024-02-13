package com.iproyal.sdk.internal.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.IBinder
import com.iproyal.sdk.internal.dto.SdkErrorType
import com.iproyal.sdk.internal.dto.SdkEvent
import com.iproyal.sdk.internal.dto.SdkLifeCycleName
import com.iproyal.sdk.internal.dto.ServiceAction
import com.iproyal.sdk.internal.logger.PawnsLogger
import com.iproyal.sdk.common.dto.ServiceError
import com.iproyal.sdk.common.dto.ServiceState
import com.iproyal.sdk.common.sdk.Pawns
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mobile_sdk.Mobile_sdk


@SuppressLint("MissingPermission")
internal class PeerServiceBackground : Service() {

    companion object {
        const val TAG = "PawnsSdkServiceBackground"
        const val CHECK_INTERVAL: Long = 2 * 60 * 1000 // 2min

        fun performAction(context: Context, action: ServiceAction) {
            val intent = Intent(context, PeerServiceBackground::class.java)
            intent.action = action.name

            try {
                context.startService(intent)
            } catch (e: Exception) {
                PawnsLogger.e(TAG, "Failed to start/stop background service $e")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var isServiceStarted = false
    private var isSdkStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PawnsLogger.d(TAG, "Action received ${intent?.action}")
        when (intent?.action) {
            null -> startService()
            ServiceAction.START_PAWNS_SERVICE.name -> startService()
            ServiceAction.STOP_PAWNS_SERVICE.name -> stopService(startId)
            else -> PawnsLogger.e(TAG, "Unknown action received, please use ServiceAction")
        }
        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        startService()
    }

    override fun onDestroy() {
        emitState(ServiceState.Off)
        super.onDestroy()
    }

    // Responsible for starting PeerService
    private fun startService() {
        try {
            if (isServiceStarted) return
            isServiceStarted = true
            PawnsLogger.d(TAG, ("Started service"))
            serviceScope.coroutineContext.cancelChildren()
            emitState(ServiceState.On)

            serviceScope.launch {
                while (isServiceStarted && isActive) {
                    val batteryManager: BatteryManager? =
                        getSystemService(BATTERY_SERVICE) as? BatteryManager
                    val batteryLevel =
                        batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY)
                            ?: 100

                    val intent = registerReceiver(null, IntentFilter(Intent.ACTION_BATTERY_CHANGED))
                    val plugged = intent?.getIntExtra(BatteryManager.EXTRA_PLUGGED, -1)
                    val isCharging = plugged == BatteryManager.BATTERY_PLUGGED_AC ||
                            plugged == BatteryManager.BATTERY_PLUGGED_USB ||
                            plugged == BatteryManager.BATTERY_PLUGGED_WIRELESS

                    if (batteryLevel < 20 && !isCharging) {
                        stopSharing(ServiceState.Launched.LowBattery)
                    } else {
                        startSharing()
                    }
                    delay(CHECK_INTERVAL)
                }
            }
        } catch (e: Exception) {
            PawnsLogger.e(TAG, ("Failed to start background service $e"))
        }
    }

    // Responsible for starting Internet sharing SDK
    private fun startSharing() {
        if (!Pawns.isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, make sure to initialise before using startSharing")
            return
        }
        if (isSdkStarted) return
        isSdkStarted = true
        PawnsLogger.d(TAG, ("Started sharing"))
        Mobile_sdk.startMainRoutine(Pawns.getInstance().apiKey) {
            val dependencyProvider = Pawns.getInstance().dependencyProvider ?: return@startMainRoutine
            val event = dependencyProvider.jsonInstance.decodeFromString(
                SdkEvent.serializer(),
                it
            )
            val sdkError: ServiceError? = when (event.parameters?.error) {
                SdkErrorType.NO_FREE_PORT.sdkValue -> ServiceError.Critical("Unable to open port")
                SdkErrorType.NON_RESIDENTIAL.sdkValue -> ServiceError.Critical("IP address is not suitable for internet sharing")
                SdkErrorType.UNSUPPORTED.sdkValue -> ServiceError.Critical("Library version is too old and is no longer supported")
                SdkErrorType.UNAUTHORISED.sdkValue -> ServiceError.Critical("ApiKey is incorrect or expired")
                SdkErrorType.LOST_CONNECTION.sdkValue -> ServiceError.General("Lost connection")
                SdkErrorType.IP_USED.sdkValue -> ServiceError.General("This IP is already in use")
                SdkErrorType.PEER_ALIVE_FAILED.sdkValue -> ServiceError.General("Internal error")
                null -> null
                else -> ServiceError.Unknown(event.parameters.error)
            }
            val serviceState = when {
                event.name == SdkLifeCycleName.STARTING.sdkValue -> ServiceState.On
                event.name == SdkLifeCycleName.NOT_RUNNING.sdkValue && sdkError != null -> ServiceState.Launched.Error(
                    sdkError
                )

                else -> ServiceState.Launched.Running
            }
            emitState(serviceState)
            PawnsLogger.d(TAG, "state: $serviceState error: $sdkError")
        }
    }

    // Responsible for stopping PeerService
    private fun stopService(startId: Int) {
        try {
            stopSharing(ServiceState.Off)
            try {
                stopSelf(startId)
            } catch (e: Exception) {
                PawnsLogger.e(TAG, e.message.orEmpty())
            }
            isServiceStarted = false
            isSdkStarted = false
            PawnsLogger.d(TAG, ("Stopped service"))
        } catch (e: Exception) {
            PawnsLogger.e(TAG, ("Failed to stop background service $e"))
        }
    }

    // Responsible for stopping SDK
    private fun stopSharing(state: ServiceState) {
        PawnsLogger.d(TAG, ("Stopped sharing"))
        Mobile_sdk.stopMainRoutine()
        emitState(state)
        isSdkStarted = false
    }

    // Triggers state change for coroutines flow and for listener
    private fun emitState(state: ServiceState) {
        if (!Pawns.isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, cannot emitState")
            return
        }
        Pawns.getInstance()._serviceState.value = state
        Pawns.getInstance().serviceListener?.onStateChange(state)
    }

}