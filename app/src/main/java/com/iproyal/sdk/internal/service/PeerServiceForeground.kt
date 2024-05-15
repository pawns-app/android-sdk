package com.iproyal.sdk.internal.service

import android.Manifest
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ServiceInfo
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.ServiceCompat
import com.iproyal.sdk.common.dto.ServiceError
import com.iproyal.sdk.common.dto.ServiceState
import com.iproyal.sdk.common.sdk.Pawns
import com.iproyal.sdk.internal.dto.SdkErrorType
import com.iproyal.sdk.internal.dto.SdkEvent
import com.iproyal.sdk.internal.dto.SdkLifeCycleName
import com.iproyal.sdk.internal.dto.ServiceAction
import com.iproyal.sdk.internal.logger.PawnsLogger
import com.iproyal.sdk.internal.notification.NotificationManager
import com.iproyal.sdk.internal.util.PermissionUtil
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import mobile_sdk.Mobile_sdk


internal class PeerServiceForeground : Service() {

    companion object {
        const val TAG = "PawnsSdkServiceForeground"
        const val CHECK_INTERVAL: Long = 5 * 60 * 1000 // 5min

        fun performAction(context: Context, action: ServiceAction) {
            val intent = Intent(context, PeerServiceForeground::class.java)
            intent.action = action.name

            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(intent)
                } else {
                    context.startService(intent)
                }
            } catch (e: Exception) {
                PawnsLogger.e(TAG, "Failed to start/stop foreground service $e")
            }
        }
    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockTag: String = "com.iproyal.sdk:LOCK"
    private var isServiceStarted = false
    private var isSdkStarted = false

    private fun start(startId: Int?) {
        val dependencyProvider = Pawns.getInstance().dependencyProvider
        if (dependencyProvider == null) {
            PawnsLogger.e(TAG, "start failed due to dependencyProvider being null")
            return
        }

        runCatching {
            val foregroundServiceType = when {
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
                        PermissionUtil.hasPermissionInManifest(
                            this, Manifest.permission.FOREGROUND_SERVICE_DATA_SYNC
                        ) -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else -> 0
            }

            ServiceCompat.startForeground(
                this,
                NotificationManager.CHANNEL_SERVICE_MESSAGE_ID,
                dependencyProvider.notificationManager.createServiceNotification(),
                foregroundServiceType
            )
        }.onFailure { error ->
            PawnsLogger.e(TAG, "Unable to start PeerServiceForeground $error")
            stopService(startId)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        PawnsLogger.d(TAG, "Action received ${intent?.action}")
        start(startId)
        when (intent?.action) {
            null -> startService()
            ServiceAction.START_PAWNS_SERVICE.name -> startService()
            ServiceAction.STOP_PAWNS_SERVICE.name -> stopService(startId)
            else -> PawnsLogger.e(TAG, "Unknown action received, please use ServiceAction")
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT == Build.VERSION_CODES.M && Build.MANUFACTURER.equals("Huawei")) {
            wakeLockTag = "LocationManagerService"
        }
        if (!Pawns.isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, cannot create service")
            stopService(-1)
            return
        }
        try {
            start(null)
        } catch (e: Exception) {
            PawnsLogger.e(TAG, "Failed to create foreground service $e")
        }
    }

    override fun onDestroy() {
        emitState(ServiceState.Off)
        runCatching {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        }
            .onFailure { PawnsLogger.e(TAG, it.message.orEmpty()) }
        super.onDestroy()
    }

    // Responsible for starting PeerService
    private fun startService() {
        if (!Pawns.isInitialised) {
            PawnsLogger.e(PeerServiceBackground.TAG, "Instance is not initialised, cannot startService")
            return
        }
        try {
            if (isServiceStarted) return
            isServiceStarted = true
            PawnsLogger.d(TAG, ("Started service"))
            serviceScope.coroutineContext.cancelChildren()
            emitState(ServiceState.On)
            wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
                newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, wakeLockTag).apply {
                    acquire()
                }
            }

            serviceScope.launch {
                while (isServiceStarted && isActive && Pawns.isInitialised) {
                    val batteryManager: BatteryManager? = getSystemService(BATTERY_SERVICE) as? BatteryManager
                    val batteryLevel = batteryManager?.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY) ?: 100

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
            PawnsLogger.e(TAG, ("Failed to start foreground service $e"))
        }
    }

    // Responsible for starting Internet sharing SDK
    private fun startSharing() {
        if (isSdkStarted) return
        isSdkStarted = true
        PawnsLogger.d(TAG, ("Started sharing"))
        Mobile_sdk.startMainRoutine(Pawns.getInstance().apiKey) {
            val dependencyProvider = Pawns.getInstance().dependencyProvider ?: return@startMainRoutine
            val event = dependencyProvider.jsonInstance.decodeFromString(SdkEvent.serializer(), it)
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
                event.name == SdkLifeCycleName.NOT_RUNNING.sdkValue && sdkError != null -> ServiceState.Launched.Error(sdkError)
                else -> ServiceState.Launched.Running
            }
            emitState(serviceState)
            PawnsLogger.d(TAG, "state: $serviceState error: $sdkError")
        }
    }

    // Responsible for stopping PeerService
    private fun stopService(startId: Int?) {
        try {
            stopSharing(ServiceState.Off)
            try {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                    stopForeground(STOP_FOREGROUND_REMOVE)
                } else {
                    stopForeground(true)
                }
                if (startId != null) stopSelf(startId) else stopSelf()
            } catch (e: Exception) {
                PawnsLogger.e(TAG, e.message.orEmpty())
            }
            isServiceStarted = false
            runCatching { serviceScope.cancel() }
            PawnsLogger.d(TAG, ("Stopped service"))
        } catch (e: Exception) {
            PawnsLogger.e(TAG, ("Failed to stop foreground service $e"))
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