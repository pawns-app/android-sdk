package com.pawns.sdk.internal.service

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
import com.pawns.ndk.PawnsCore
import com.pawns.sdk.common.dto.ServiceError
import com.pawns.sdk.common.dto.ServiceState
import com.pawns.sdk.common.sdk.Pawns
import com.pawns.sdk.internal.dto.SdkErrorType
import com.pawns.sdk.internal.dto.SdkEvent
import com.pawns.sdk.internal.dto.SdkLifeCycleName
import com.pawns.sdk.internal.dto.ServiceAction
import com.pawns.sdk.internal.logger.PawnsLogger
import com.pawns.sdk.internal.network.NetworkChecker
import com.pawns.sdk.internal.util.PermissionUtil
import com.pawns.sdk.internal.util.runCatchingCoroutine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.delay
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.random.Random


internal class PeerServiceForeground : Service() {

    companion object {
        const val TAG = "PawnsSdkServiceForeground"
        private const val OPTIMISATION_CHECK_INTERVAL: Long = 5 * 60 * 1000 // 5min
        private const val ROUTINE_INTERVAL: Long = 30 * 1000 // 30sec
        private const val ROUTINE_INTERVAL_MAX: Long = 300 * 1000 // 5min

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

    private val networkChecker: NetworkChecker = NetworkChecker()
    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val coreScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var wakeLockTag: String = "com.pawns.sdk:LOCK"
    private var isServiceStarted = false
    private var isSdkStarted = false
    private var isSdkStartAllowedFromRoutine = true
    private var connectionFailedTimes = 0

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
                        ) &&
                        !PermissionUtil.hasPermissionInManifest(
                            this, Manifest.permission.FOREGROUND_SERVICE_SPECIAL_USE
                        ) -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC

                Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                else -> 0
            }

            val notificationManager = dependencyProvider.notificationManager
            ServiceCompat.startForeground(
                this,
                notificationManager.getNotificationId(),
                notificationManager.createServiceNotification(),
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
        try {
            start(null)
        } catch (e: Exception) {
            PawnsLogger.e(TAG, "Failed to create foreground service $e")
        }
        if (!Pawns.isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, cannot create service")
            stopService(-1)
            return
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
        coreScope.launch { PawnsCore.StopMainRoutine() }
        super.onDestroy()
    }

    // Responsible for starting PeerService
    private fun startService() {
        if (!Pawns.isInitialised) {
            PawnsLogger.e(TAG, "Instance is not initialised, cannot startService")
            return
        }
        try {
            if (isServiceStarted) return
            isServiceStarted = true
            PawnsLogger.d(TAG, ("Started service"))
            connectionFailedTimes = 0
            serviceScope.coroutineContext.cancelChildren()
            coreScope.coroutineContext.cancelChildren()
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

                    when {
                        batteryLevel < 20 && !isCharging -> {
                            stopSharing(ServiceState.Launched.LowBattery)
                        }

                        networkChecker.isVPNDetected(this@PeerServiceForeground) -> {
                            PawnsLogger.d(TAG, "Optimisation routine detected VPN service")
                            stopSharing(ServiceState.Launched.Error(ServiceError.Critical("VPN is not allowed, waiting on VPN to be disabled")))
                        }

                        else -> {
                            if (isSdkStartAllowedFromRoutine) {
                                startSharing()
                            }
                        }
                    }
                    delay(OPTIMISATION_CHECK_INTERVAL)
                }
            }
        } catch (e: Exception) {
            PawnsLogger.e(TAG, ("Failed to start foreground service $e"))
        }
    }

    // Responsible for starting Internet sharing SDK
    private suspend fun startSharing() {
        if (isSdkStarted) return
        isSdkStarted = true

        PawnsLogger.d(TAG, ("Started sharing"))
        emitState(ServiceState.On)

        PawnsCore.StartMainRoutine(Pawns.getInstance().apiKey, object : PawnsCore.Callback {
            override fun onCallback(callback: String) {
                if (!isSdkStarted) return
                val isVpnDetected = networkChecker.isVPNDetected(this@PeerServiceForeground)
                val dependencyProvider = Pawns.getInstance().dependencyProvider ?: return
                val event = dependencyProvider.jsonInstance.decodeFromString(SdkEvent.serializer(), callback)
                val sdkError: ServiceError? = when {
                    isVpnDetected -> ServiceError.Critical("VPN is not allowed, waiting on VPN to be disabled")
                    event.parameters?.error == SdkErrorType.NO_FREE_PORT.sdkValue -> ServiceError.Critical("Unable to open port")
                    event.parameters?.error == SdkErrorType.NON_RESIDENTIAL.sdkValue -> ServiceError.Critical("IP address is not suitable for internet sharing")
                    event.parameters?.error == SdkErrorType.UNSUPPORTED.sdkValue -> ServiceError.Critical("Library version is too old and is no longer supported")
                    event.parameters?.error == SdkErrorType.UNAUTHORISED.sdkValue -> ServiceError.Critical("ApiKey is incorrect or expired")
                    event.parameters?.error == SdkErrorType.LOST_CONNECTION.sdkValue -> ServiceError.General("Lost connection")
                    event.parameters?.error == SdkErrorType.IP_USED.sdkValue -> ServiceError.General("This IP is already in use")
                    event.parameters?.error == SdkErrorType.PEER_ALIVE_FAILED.sdkValue -> ServiceError.General("Internal error")
                    event.parameters?.error == SdkErrorType.CANT_OPEN_PORT.sdkValue -> ServiceError.General("Unable to open port")
                    event.parameters?.error == null -> null
                    else -> ServiceError.Unknown(event.parameters.error)
                }

                PawnsLogger.d(
                    TAG,
                    "event: ${event.name}" +
                            if (event.parameters?.traffic != null) " ${event.parameters.traffic} " else " " +
                                    "error: $sdkError"
                )
                when {
                    event.name == SdkLifeCycleName.NOT_RUNNING.sdkValue && sdkError is ServiceError.Critical && !isVpnDetected -> {
                        PawnsLogger.d(TAG, "Launching critical error fallback routine")
                        serviceScope.launch {
                            runCatchingCoroutine {
                                isSdkStartAllowedFromRoutine = false
                                stopSharing(ServiceState.Launched.Error(sdkError))
                                connectionFailedTimes += 1
                                val exponentialDelay = (ROUTINE_INTERVAL * connectionFailedTimes.coerceAtLeast(1))
                                    .coerceAtMost(ROUTINE_INTERVAL_MAX) + Random.nextInt(1000, 10001)
                                PawnsLogger.d(TAG, "Critical error fallback routine started $exponentialDelay")
                                delay(exponentialDelay)
                                ensureActive()
                                isSdkStartAllowedFromRoutine = true
                                startSharing()
                            }
                        }
                    }
                    // Stop Sharing and allow optimisation flow to start if VPN is not detected after a while
                    isVpnDetected -> {
                        connectionFailedTimes = 0
                        PawnsLogger.d(TAG, "Core routine detected VPN service")
                        stopSharing(ServiceState.Launched.Error(ServiceError.Critical("VPN is not allowed, waiting on VPN to be disabled")))
                    }

                    else -> {
                        connectionFailedTimes = 0
                        emitState(event, sdkError)
                    }
                }
            }
        })
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
            connectionFailedTimes = 0
            runCatching { serviceScope.cancel() }
            PawnsLogger.d(TAG, ("Stopped service"))
        } catch (e: Exception) {
            PawnsLogger.e(TAG, ("Failed to stop foreground service $e"))
        }
    }

    // Responsible for stopping SDK
    private fun stopSharing(state: ServiceState) {
        PawnsLogger.d(TAG, ("Stopped sharing"))
        emitState(state)
        isSdkStarted = false
        coreScope.launch { PawnsCore.StopMainRoutine() }
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

    private fun emitState(event: SdkEvent, sdkError: ServiceError?) {
        if (SdkLifeCycleName.values().map { it.sdkValue }.none { it == event.name }) return

        val serviceState = when {
            event.name == SdkLifeCycleName.RUNNING.sdkValue ||
                    event.name == SdkLifeCycleName.TRAFFIC.sdkValue -> ServiceState.Launched.Running(event.parameters?.traffic?.toIntOrNull())

            event.name == SdkLifeCycleName.STARTING.sdkValue -> ServiceState.On
            event.name == SdkLifeCycleName.NOT_RUNNING.sdkValue && sdkError != null -> ServiceState.Launched.Error(sdkError)
            else -> ServiceState.On
        }

        emitState(serviceState)
    }

}