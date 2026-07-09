package com.example.adhocondor.ui.main

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.net.wifi.p2p.WifiP2pConfig
import android.net.wifi.p2p.WifiP2pDevice
import android.net.wifi.p2p.WifiP2pInfo
import android.net.wifi.p2p.WifiP2pManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.constraintlayout.widget.ConstraintLayout
import com.example.adhocondor.R
import com.example.adhocondor.network.UDPHandshake
import com.example.adhocondor.network.WiFiDirectBroadcastReceiver
import com.example.adhocondor.ui.chat.ChatActivity
import com.google.android.material.bottomnavigation.BottomNavigationView

class MainActivity : ComponentActivity() {

    companion object {
        private const val DISCOVERY_TIMEOUT = 15_000L
        private const val PING_REPEAT = 3
        private const val PING_INTERVAL_MS = 150L
    }

    private lateinit var manager: WifiP2pManager
    private lateinit var channel: WifiP2pManager.Channel
    private lateinit var receiver: BroadcastReceiver
    private lateinit var intentFilter: IntentFilter

    // UI
    private lateinit var connectButton: Button
    private lateinit var connectProgress: ProgressBar
    private lateinit var peersListView: ListView
    private lateinit var discoverContainer: ConstraintLayout
    private lateinit var discoverProgress: ProgressBar
    private lateinit var refreshIcon: ImageView
    private lateinit var devicesFoundText: TextView

    // Peers
    private val peers = mutableListOf<WifiP2pDevice>()
    private lateinit var selectedDevice: WifiP2pDevice

    // Identificare utilizator
    private lateinit var userId: String
    private lateinit var nickname: String

    // Descoperire
    private var isDiscovering = false
    private val discoverHandler = Handler(Looper.getMainLooper())
    private val stopDiscoverRunnable = Runnable { showDiscoverIdle() }

    // Confirmare peer
    private val confirmedPeers = mutableSetOf<String>() // deviceAddress

    // Permisiuni
    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { perms ->
        val granted = perms[Manifest.permission.ACCESS_FINE_LOCATION] == true &&
                perms[Manifest.permission.NEARBY_WIFI_DEVICES] == true
        if (!granted) {
            Toast.makeText(this, "Trebuie acordate permisiile Wi-Fi și locație!", Toast.LENGTH_LONG).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Preluare UUID și nickname
        userId = intent.getStringExtra("USER_ID") ?: "unknown_id"
        nickname = intent.getStringExtra("NICKNAME") ?: "Anonim"

        // UI
        connectButton = findViewById(R.id.connectButton)
        connectProgress = findViewById(R.id.connectProgress)
        peersListView = findViewById(R.id.peersListView)
        discoverContainer = findViewById(R.id.discoverContainer)
        discoverProgress = findViewById(R.id.discoverProgress)
        devicesFoundText = findViewById(R.id.devicesFoundText)
        refreshIcon = findViewById(R.id.refreshIcon)

        // Wi-Fi Direct
        manager = getSystemService(WIFI_P2P_SERVICE) as WifiP2pManager
        channel = manager.initialize(this, mainLooper, null)

        // Permisiuni
        permissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.NEARBY_WIFI_DEVICES
            )
        )

        // Intent filter
        intentFilter = IntentFilter().apply {
            addAction(WifiP2pManager.WIFI_P2P_STATE_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_PEERS_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_CONNECTION_CHANGED_ACTION)
            addAction(WifiP2pManager.WIFI_P2P_THIS_DEVICE_CHANGED_ACTION)
        }

        receiver = WiFiDirectBroadcastReceiver(manager, channel, this)

        // UDP listener handshake
        UDPHandshake.startListener { _, msg ->
            val peer = peers.find { it.deviceAddress == msg }
            if (peer != null) {
                confirmedPeers.add(peer.deviceAddress)
                runOnUiThread { updateConfirmedPeerList() }
            }
        }

        // Connect button
        connectButton.setOnClickListener {
            if (::selectedDevice.isInitialized) {
                connectToPeer(selectedDevice)
            } else {
                Toast.makeText(this, "Selectează un dispozitiv!", Toast.LENGTH_SHORT).show()
            }
        }

        // Descoperire manuală
        refreshIcon.setOnClickListener { if (!isDiscovering) discoverPeers() }

        // Auto-discover
        if (intent.getBooleanExtra("autoDiscover", false)) discoverPeers()

        val bottomNav = findViewById<BottomNavigationView>(R.id.bottomNav)
        bottomNav.selectedItemId = R.id.nav_devices
    }

    private fun setConnectButtonEnabled(enabled: Boolean) {
        connectButton.isEnabled = enabled
        connectProgress.visibility = if (enabled) View.GONE else View.VISIBLE
    }

    private fun showDiscoverLoading() {
        isDiscovering = true
        refreshIcon.visibility = View.GONE
        discoverProgress.visibility = View.VISIBLE
    }

    private fun showDiscoverIdle() {
        isDiscovering = false
        discoverProgress.visibility = View.GONE
        refreshIcon.visibility = View.VISIBLE
    }

    private fun stopDiscoverIfRunning() {
        if (isDiscovering) {
            discoverHandler.removeCallbacks(stopDiscoverRunnable)
            showDiscoverIdle()
        }
    }

    private fun discoverPeers() {
        if (checkSelfPermission(Manifest.permission.NEARBY_WIFI_DEVICES) == PackageManager.PERMISSION_GRANTED &&
            checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
        ) {
            showDiscoverLoading()
            manager.discoverPeers(channel, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(this@MainActivity, "Căutare începută...", Toast.LENGTH_SHORT).show()
                    discoverHandler.removeCallbacks(stopDiscoverRunnable)
                    discoverHandler.postDelayed(stopDiscoverRunnable, DISCOVERY_TIMEOUT)
                }

                override fun onFailure(reason: Int) {
                    stopDiscoverIfRunning()
                    Toast.makeText(this@MainActivity, "Eroare la căutare: $reason", Toast.LENGTH_SHORT).show()
                }
            })
        }
    }

    fun updatePeerList(newPeers: Collection<WifiP2pDevice>) {
        peers.clear()
        peers.addAll(newPeers)

        repeat(PING_REPEAT) { attempt ->
            Handler(Looper.getMainLooper()).postDelayed({
                peers.forEach { peer ->
                    UDPHandshake.sendPing("255.255.255.255", peer.deviceAddress)
                }
            }, attempt * PING_INTERVAL_MS)
        }

        updateConfirmedPeerList()
    }

    private fun updateConfirmedPeerList() {
        val names = peers.map { peer ->
            if (confirmedPeers.contains(peer.deviceAddress)) "${peer.deviceName} ✅"
            else peer.deviceName
        }

        peersListView.adapter = ArrayAdapter(this, R.layout.item_peers_list, names)
        devicesFoundText.text = "${peers.size} devices found"

        peersListView.setOnItemClickListener { _, _, position, _ ->
            selectedDevice = peers[position]
            Toast.makeText(this, "Selectat: ${selectedDevice.deviceName}", Toast.LENGTH_SHORT).show()
            setConnectButtonEnabled(false)
            Handler(mainLooper).postDelayed({ setConnectButtonEnabled(true) }, 500)
        }
    }

    private fun connectToPeer(device: WifiP2pDevice, attempt: Int = 1) {
        stopDiscoverIfRunning()
        setConnectButtonEnabled(false)

        // În loc să blocăm la removeGroup, apelăm conectarea oricum
        manager.removeGroup(channel, object : WifiP2pManager.ActionListener {
            override fun onSuccess() {
                // chiar dacă s-a rupt grupul, conectăm
                actuallyConnect(device, attempt)
            }

            override fun onFailure(reason: Int) {
                // ignorăm eroarea și conectăm oricum
                actuallyConnect(device, attempt)
            }
        })
    }


    private fun actuallyConnect(device: WifiP2pDevice, attempt: Int) {
        val config = WifiP2pConfig().apply {
            deviceAddress = device.deviceAddress
            groupOwnerIntent = 0
        }

        try {
            manager.connect(channel, config, object : WifiP2pManager.ActionListener {
                override fun onSuccess() {
                    Toast.makeText(this@MainActivity, "Conectare reușită!", Toast.LENGTH_SHORT).show()
                    setConnectButtonEnabled(true)
                }

                override fun onFailure(reason: Int) {
                    if ((reason == WifiP2pManager.BUSY || reason == WifiP2pManager.ERROR) && attempt < 3) {
                        Handler(Looper.getMainLooper()).postDelayed({ actuallyConnect(device, attempt + 1) }, 500)
                    } else {
                        Toast.makeText(this@MainActivity, "Conectare eșuată: $reason", Toast.LENGTH_SHORT).show()
                        setConnectButtonEnabled(true)
                    }
                }
            })
        } catch (e: SecurityException) {
            Toast.makeText(this, "Nu există permisiunea necesară!", Toast.LENGTH_SHORT).show()
            setConnectButtonEnabled(true)
        }
    }

    fun updateConnectionInfo(info: WifiP2pInfo) {
        if (info.groupFormed && info.groupOwnerAddress != null) {
            val ip = info.groupOwnerAddress.hostAddress
            peers.forEach { peer ->
                if (!confirmedPeers.contains(peer.deviceAddress)) {
                    UDPHandshake.sendPing(ip, peer.deviceAddress)
                }
            }
        }

        if (!info.groupFormed) return

        val intent = Intent(this, ChatActivity::class.java).apply {
            putExtra("USER_ID", userId)
            putExtra("NICKNAME", nickname)
            putExtra("IS_GROUP_OWNER", info.isGroupOwner)
            info.groupOwnerAddress?.hostAddress?.let { putExtra("GROUP_OWNER_IP", it) }
        }
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        registerReceiver(receiver, intentFilter)
    }

    override fun onPause() {
        super.onPause()
        stopDiscoverIfRunning()
        unregisterReceiver(receiver)
    }
}
