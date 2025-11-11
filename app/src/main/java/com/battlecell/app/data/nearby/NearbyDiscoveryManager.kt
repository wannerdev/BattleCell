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
import java.io.File
import java.util.Locale
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

    @SuppressLint("MissingPermission", "DeprecatedWifiInfo")
    private suspend fun scanWifi(timeoutMillis: Long): List<WifiDeviceInfo> {
        val manager = wifiManager ?: return discoverArpClients()
        val scanned = try {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine<List<WifiDeviceInfo>> { continuation ->
                    val filter = IntentFilter(WifiManager.SCAN_RESULTS_AVAILABLE_ACTION)
                    val receiver = object : BroadcastReceiver() {
                        override fun onReceive(context: Context?, intent: Intent?) {
                            unregisterReceiverSafe(this)
                            if (continuation.isActive) {
                                continuation.resume(manager.mapScanResults())
                            }
                        }
                    }
                    registerReceiverSafe(receiver, filter)
                    val started = try {
                        manager.startScan()
                    } catch (_: SecurityException) {
                        false
                    }
                    if (!started && continuation.isActive) {
                        unregisterReceiverSafe(receiver)
                        continuation.resume(manager.mapScanResults())
                    }
                    continuation.invokeOnCancellation {
                        unregisterReceiverSafe(receiver)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            manager.mapScanResults()
        } catch (_: SecurityException) {
            emptyList()
        }

        val connection = manager.connectedAccessPoint()
        val arpClients = discoverArpClients()

        return buildList {
            addAll(scanned)
            connection?.let { add(it) }
            addAll(arpClients)
        }
            .distinctBy { it.bssid.lowercase(Locale.US) }
            .sortedByDescending { it.signalLevel }
    }

    @SuppressLint("MissingPermission")
    private suspend fun scanBluetooth(timeoutMillis: Long): List<BluetoothDeviceInfo> {
        val adapter = bluetoothAdapter ?: return emptyList()
        if (!adapter.isEnabled) return bondedDevices(adapter)

        val discovered = try {
            withTimeout(timeoutMillis) {
                suspendCancellableCoroutine<List<BluetoothDeviceInfo>> { continuation ->
                    val found = mutableMapOf<String, BluetoothDeviceInfo>()
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
                                    val address = device?.address
                                    if (!address.isNullOrBlank()) {
                                        found[address] = BluetoothDeviceInfo(
                                            name = device.name,
                                            address = address,
                                            rssi = rssi
                                        )
                                    }
                                }

                                BluetoothAdapter.ACTION_DISCOVERY_FINISHED -> {
                                    unregisterReceiverSafe(this)
                                    if (continuation.isActive) {
                                        continuation.resume(found.values.toList())
                                    }
                                }
                            }
                        }
                    }
                    registerReceiverSafe(receiver, filter)

                    val started = try {
                        adapter.startDiscovery()
                    } catch (_: SecurityException) {
                        false
                    }

                    if (!started && continuation.isActive) {
                        unregisterReceiverSafe(receiver)
                        continuation.resume(emptyList())
                        return@suspendCancellableCoroutine
                    }

                    continuation.invokeOnCancellation {
                        adapter.cancelDiscovery()
                        unregisterReceiverSafe(receiver)
                    }
                }
            }
        } catch (_: TimeoutCancellationException) {
            emptyList()
        } catch (_: SecurityException) {
            emptyList()
        } finally {
            runCatching { adapter.cancelDiscovery() }
        }

        val bonded = bondedDevices(adapter)
        return (discovered + bonded)
            .distinctBy { it.address.lowercase(Locale.US) }
            .sortedByDescending { it.rssi }
    }

    @SuppressLint("MissingPermission", "DeprecatedWifiInfo")
    private fun WifiManager.connectedAccessPoint(): WifiDeviceInfo? {
        val info = runCatching { connectionInfo }.getOrNull() ?: return null
        val bssid = info.bssid?.takeIf {
            it.isNotBlank() && it != "00:00:00:00:00:00"
        } ?: return null
        val ssid = info.ssid?.takeIf {
            it.isNotBlank() && it != "<unknown ssid>"
        }?.trim('"')
        val rssi = info.rssi.takeUnless { it == Int.MIN_VALUE || it <= -200 } ?: -50
        return WifiDeviceInfo(
            ssid = ssid,
            bssid = bssid.lowercase(Locale.US),
            signalLevel = rssi
        )
    }

    private fun WifiManager.mapScanResults(): List<WifiDeviceInfo> =
        scanResults.orEmpty()
            .mapNotNull { result ->
                val bssid = result.BSSID?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                val ssid = result.SSID?.takeIf {
                    it.isNotBlank() && it != "<unknown ssid>"
                }
                WifiDeviceInfo(
                    ssid = ssid,
                    bssid = bssid.lowercase(Locale.US),
                    signalLevel = result.level
                )
            }

    private fun discoverArpClients(): List<WifiDeviceInfo> =
        runCatching {
            val arpFile = File("/proc/net/arp")
            if (!arpFile.exists() || !arpFile.canRead()) {
                emptyList()
            } else {
                arpFile.useLines { sequence ->
                    sequence
                        .drop(1)
                        .mapNotNull { line ->
                            val parts = line.split(Regex("\\s+"))
                            if (parts.size < 4) return@mapNotNull null
                            val flags = parts[2]
                            val mac = parts[3]
                            if (flags != "0x2") return@mapNotNull null
                            if (mac.equals("00:00:00:00:00:00", ignoreCase = true)) return@mapNotNull null
                            WifiDeviceInfo(
                                ssid = null,
                                bssid = mac.lowercase(Locale.US),
                                signalLevel = -45
                            )
                        }
                        .distinctBy { it.bssid }
                        .toList()
                }
            }
        }.getOrElse { emptyList() }

    @SuppressLint("MissingPermission")
    private fun bondedDevices(adapter: BluetoothAdapter): List<BluetoothDeviceInfo> =
        runCatching {
            adapter.bondedDevices.orEmpty()
                .mapNotNull { device ->
                    val address = device.address?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
                    BluetoothDeviceInfo(
                        name = device.name,
                        address = address,
                        rssi = 0
                    )
                }
        }.getOrElse { emptyList() }

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
