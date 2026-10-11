package net.redngreen.easypos.print

import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.util.UUID
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Classic Bluetooth (SPP) ESC/POS printer transport (design D7, task 10.2).
 * Runs on IO and is always called off the sale path; failures surface as a
 * retry affordance rather than blocking anything.
 */
@Singleton
class BluetoothPrinter @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private val manager: BluetoothManager?
        get() = context.getSystemService(Context.BLUETOOTH_SERVICE) as? BluetoothManager

    val adapter: BluetoothAdapter? get() = manager?.adapter

    val isAvailable: Boolean get() = adapter != null

    /** Already-paired printers to pick from (spec: discover + select). */
    @SuppressLint("MissingPermission")
    fun bondedDevices(): List<BluetoothDevice> {
        val a = adapter ?: return emptyList()
        return runCatching {
            a.bondedDevices.orEmpty().sortedBy { it.name ?: it.address }
        }.getOrDefault(emptyList())
    }

    /** Opens the SPP socket, writes the byte stream, closes. Throws on failure. */
    @SuppressLint("MissingPermission")
    suspend fun print(macAddress: String, bytes: ByteArray): Unit = withContext(Dispatchers.IO) {
        val a = adapter ?: error("Bluetooth is unavailable")
        val device = a.getRemoteDevice(macAddress)
        a.cancelDiscovery()
        val socket = device.createRfcommSocketToServiceRecord(SPP_UUID)
        try {
            socket.connect()
            socket.outputStream.use { stream ->
                stream.write(bytes)
                stream.flush()
            }
        } finally {
            runCatching { socket.close() }
        }
    }

    companion object {
        val SPP_UUID: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
    }
}
