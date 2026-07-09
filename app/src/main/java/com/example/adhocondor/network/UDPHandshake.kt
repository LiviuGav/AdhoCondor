package com.example.adhocondor.network

import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import kotlin.concurrent.thread

object UDPHandshake {
    private const val PORT = 8888

    fun sendPing(ip: String, message: String) {
        thread {
            try {
                val socket = DatagramSocket()
                val buf = message.toByteArray()
                val packet = DatagramPacket(buf, buf.size, InetAddress.getByName(ip), PORT)
                socket.send(packet)
                socket.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun startListener(onMessageReceived: (String, String) -> Unit) {
        thread {
            try {
                val socket = DatagramSocket(PORT)
                val buf = ByteArray(256)
                while (true) {
                    val packet = DatagramPacket(buf, buf.size)
                    socket.receive(packet)
                    val msg = String(packet.data, 0, packet.length)
                    val senderIp = packet.address.hostAddress
                    onMessageReceived(senderIp, msg)
                }
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }
}
