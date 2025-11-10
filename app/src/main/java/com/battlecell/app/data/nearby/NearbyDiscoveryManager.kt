package com.battlecell.app.data.nearby

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.wifi.WifiManager
import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeout
import kotlin.coroutines.resume

class NearbyDiscoveryManager(
    context: Context
) {
    private val appContext = context.applicationContext
    private val wifiManager: WifiManager? =
        appContext.getSystemService(Context.WIFI_SERVICE) as? WifiManager
    private val bluetoothAdapter: BluetoothAdapter? =
        (appContext.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager)?.adapter

    suspend fun discover(timeoutMillis: Long = DEFAULT_TIMEOUT_MS): NearbyDiscoverySnapshot {
        val wifiDevices = scanWifi(timeoutMillis)
        val bluetoothDevices = scanBluetooth(timeoutMillis)
        return NearbyDiscoverySnapshot(
            wifiDevices = wifiDevices,
            bluetoothDevices = bluetoothDevices
        )
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanWifi(timeoutMillis: Long): List<WifiDeviceInfo> {
        val manager = wifiManager ?: return emptyList()
        return try {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            val results = manager.scanResults.orEmpty()
                                .filter { !it.BSSID.isNullOrBlank() }
                            unregisterReceiverSafe(this)
                            if (continuation.isActive) {
                                continuation.resume(results.map {
                                    WifiDeviceInfo(
                                        ssid = it.SSID.takeIf { name -> name?.isNotBlank() == true },
                                        bssid = it.BSSID!!,
                                        signalLevel = it.level
                                    )
                                })
                            }
                        }
                    }
                    registerReceiverSafe(receiver, filter)
                    val started = try {
                        manager.startScan()
                    } catch (security: SecurityException) {
                        false
                    }
                    if (!started) {
                        unregisterReceiverSafe(receiver)
                        continuation.resume(manager.scanResults.orEmpty()
                            .filter { !it.BSSID.isNullOrBlank() }
                            .map {
                            WifiDeviceInfo(
                                ssid = it.SSID.takeIf { name -> name?.isNotBlank() == true },
                                bssid = it.BSSID!!,
                                signalLevel = it.level
                            )
                        })
                    }
                    continuation.invokeOnCancellation {
                        unregisterReceiverSafe(receiver)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            manager.scanResults.orEmpty()
                .filter { !it.BSSID.isNullOrBlank() }
                .map {
                WifiDeviceInfo(
                    ssid = it.SSID.takeIf { name -> name?.isNotBlank() == true },
                    bssid = it.BSSID!!,
                    signalLevel = it.level
                )
            }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanBluetooth(timeoutMillis: Long): List<BluetoothDeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return emptyList()

        return try {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine { continuation ->
                    val discovered = mutableMapOf<String, BluetoothDeviceInfo>()
                    val filter = IntentFilter().apply {
                        addAction(BluetoothDevice.ACTION_FOUND)
                        addAction(BluetoothAdapter.ACTION_DISCOVERY_FINISHED)
                    }
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            when (intent?.action) {
                                BluetoothDevice.ACTION_FOUND -> {
                                    val device =
                                        intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                                    val rssi = intent.getShortExtra(
                                        BluetoothDevice.EXTRA_RSSI,
                                        Short.MIN_VALUE
                                    ).toInt()
                                    if (device != null && device.address != null) {
                                        discovered[device.address] =
                                            BluetoothDeviceInfo(
                                                name = device.name,
                                                address = device.address,
                                                rssi = rssi
                                            )
                                    }
                                }

                                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                                    unregisterReceiverSafe(this)
                                    if (continuation.isActive) {
                                        continuation.resume(discovered.values.toList())
                                    }
                                }
                            }
                        }
                    }
                    registerReceiverSafe(receiver, filter)

                    val started = try {
                        adapter.startDiscovery()
                    } catch (security: SecurityException) {
                        false
                    }

                    if (!started) {
                        unregisterReceiverSafe(receiver)
                        continuation.resume(emptyList())
                    }

                    continuation.invokeOnCancellation {
                        adapter.cancelDiscovery()
                        unregisterReceiverSafe(receiver)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            adapter.cancelDiscovery()
            emptyList()
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    private fun registerReceiverSafe(receiver: BroadcastReceiver, filter: IntentFilter) {
        try {
            appContext.registerReceiver(receiver, filter)
        } catch (_: Exception) {
        }
    }

    private fun unregisterReceiverSafe(receiver: BroadcastReceiver) {
        try {
            appContext.unregisterReceiver(receiver)
        } catch (_: IllegalArgumentException) {
            // already unregistered
        }
    }

    companion object {
        private const val DEFAULT_TIMEOUT_MS = 8_000L
    }
}
