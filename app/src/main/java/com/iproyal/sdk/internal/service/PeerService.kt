package com.iproyal.sdk.internal.service

import android.annotation.SuppressLint
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import com.iproyal.sdk.internal.dto.SdkErrorType
import com.iproyal.sdk.internal.dto.SdkEvent
import com.iproyal.sdk.internal.dto.SdkLifeCycleName
import com.iproyal.sdk.internal.dto.ServiceAction
import com.iproyal.sdk.internal.logger.PawnsLogger
import com.iproyal.sdk.internal.notification.NotificationManager
import com.iproyal.sdk.public.dto.ServiceError
import com.iproyal.sdk.public.dto.ServiceState
import com.iproyal.sdk.public.sdk.Pawns
import kotlinx.coroutines.*
import mobile_sdk.Mobile_sdk


@SuppressLint("MissingPermission")
internal class PeerService : Service() {

    companion object {
        const val TAG = "PawnsSdkService"
        const val CHECK_INTERVAL: Long = 5 * 60 * 1000 // 5min

        fun performAction(context: Context, action: ServiceAction) {
            val intent = Intent(context, PeerService::class.java)
            intent.action = action.name

            try {
                context.startService(intent)
            } catch (e: Exception) {
                PawnsLogger.e(TAG, "Failed to start/stop foreground service $e")
            }
        }

    }

    private val serviceScope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private var wakeLock: PowerManager.WakeLock? = null
    private var isServiceStarted = false
    private var isSdkStarted = false

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        intent?.let {
            PawnsLogger.d(TAG, "Action received ${it.action}")
            when (it.action) {
                ServiceAction.START_PAWNS_SERVICE.name -> {
                    try {
                        startForeground(
                            NotificationManager.CHANNEL_SERVICE_MESSAGE_ID,
                            Pawns.instance.dependencyProvider.notificationManager.createServiceNotification()
                        )
                        startService()
                    } catch (e: Exception) {
                        PawnsLogger.e(TAG, ("Failed to start foreground service $e"))
                    }
                }
                ServiceAction.STOP_PAWNS_SERVICE.name -> {
                    try {
                        stopService()
                    } catch (e: Exception) {
                        PawnsLogger.e(TAG, ("Failed to stop foreground service $e"))
                    }
                }
                else -> PawnsLogger.e(TAG, "Unknown action received, please use ServiceAction")
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()
        try {
            startForeground(
                NotificationManager.CHANNEL_SERVICE_MESSAGE_ID,
                Pawns.instance.dependencyProvider.notificationManager.createServiceNotification()
            )
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
        if (isServiceStarted) return
        isServiceStarted = true
        PawnsLogger.d(TAG, ("Started service"))
        serviceScope.coroutineContext.cancelChildren()
        emitState(ServiceState.On)
        wakeLock = (getSystemService(Context.POWER_SERVICE) as PowerManager).run {
            newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "$TAG::lock").apply {
                acquire()
            }
        }

        serviceScope.launch {
            while (isServiceStarted && isActive) {
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
    }

    // Responsible for starting Internet sharing SDK
    private fun startSharing() {
        if (isSdkStarted) return
        isSdkStarted = true
        PawnsLogger.d(TAG, ("Started sharing"))
        Mobile_sdk.startMainRoutine(Pawns.instance.apiKey) {
            val event = Pawns.instance.dependencyProvider.jsonInstance.decodeFromString(SdkEvent.serializer(), it)
            val sdkError = when {
                event.parameters?.error == SdkErrorType.UNAUTHORISED.sdkValue -> ServiceError.Unauthorised
                !event.parameters?.error.isNullOrBlank() -> ServiceError.Unknown(event.parameters?.error)
                else -> null
            }
            val serviceState = when {
                event.name == SdkLifeCycleName.STARTING.sdkValue -> ServiceState.On
                event.name == SdkLifeCycleName.NOT_RUNNING.sdkValue && sdkError != null -> ServiceState.Launched.Error(sdkError)
                else -> ServiceState.Launched.Running
            }
            emitState(serviceState)
            PawnsLogger.d(TAG, it)
        }
    }

    // Responsible for stopping PeerService
    private fun stopService() {
        stopSharing(ServiceState.Off)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                stopForeground(true)
            }
            stopSelf()
        } catch (e: Exception) {
            PawnsLogger.e(TAG, e.message.orEmpty())
        }
        isServiceStarted = false
        isSdkStarted = false
        PawnsLogger.d(TAG, ("Stopped service"))
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
        Pawns.instance._serviceState.value = state
        Pawns.instance.serviceListener?.onStateChange(state)
    }

}