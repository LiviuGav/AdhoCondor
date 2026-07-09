package com.example.adhocondor.network

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.net.wifi.p2p.WifiP2pManager
import android.widget.Toast
import androidx.annotation.RequiresPermission
import com.example.adhocondor.ui.main.MainActivity

class WiFiDirectBroadcastReceiver(
    private val manager: WifiP2pManager,
    private val channel: WifiP2pManager.Channel,
    private val activity: MainActivity
) : BroadcastReceiver() {

    @RequiresPermission(allOf = [Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.NEARBY_WIFI_DEVICES])
    override fun onReceive(context: Context?, intent: Intent?) {
        when (intent?.action) {

            WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION -> {
                val state = intent.getIntExtra(WifiP2pManager.EXTRA_WIFI_STATE, -1)
                val message = if (state == WifiP2pManager.WIFI_P2P_STATE_ENABLED) {
                    "Wi-Fi Direct activ"
                } else {
                    "Wi-Fi Direct dezactivat"
                }
                Toast.makeText(activity, message, Toast.LENGTH_SHORT).show()
            }

            WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION -> {
                manager.requestPeers(channel) { peers ->
                    activity.updatePeerList(peers.deviceList)
                }
            }

            WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION -> {
                manager.requestConnectionInfo(channel) { info ->
                    if (info.groupFormed) {
                        activity.updateConnectionInfo(info)
                    } else {
                    }
                }
            }


            WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION -> {
                // Optional: poți actualiza UI dacă vrei să arăți statusul dispozitivului local
            }
        }
    }
}

