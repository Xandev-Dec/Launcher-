package com.example.simplelauncher

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.text.Editable
import android.text.TextWatcher
import android.widget.EditText
import android.widget.FrameLayout
import com.google.android.gms.ads.AdRequest
import com.google.android.gms.ads.AdSize
import com.google.android.gms.ads.AdView
import com.google.android.gms.ads.MobileAds
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var allApps: List<AppInfo> = emptyList()
    private lateinit var adapter: AppAdapter
    private lateinit var search: EditText
    private lateinit var list: RecyclerView
    private var bannerView: AdView? = null

    private val packageReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) = loadApps()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        search = findViewById(R.id.search)
        list = findViewById(R.id.app_list)

        adapter = AppAdapter(
            onClick = { app -> launch(app) },
            onLongClick = { app -> showMenu(app) }
        )
        list.layoutManager = GridLayoutManager(this, 4)
        list.adapter = adapter

        search.addTextChangedListener(object : TextWatcher {
            override fun afterTextChanged(s: Editable?) = filter(s?.toString().orEmpty())
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        val filter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_CHANGED)
            addDataScheme("package")
        }
        ContextCompat.registerReceiver(this, packageReceiver, filter, ContextCompat.RECEIVER_EXPORTED)

        // Launcher tidak boleh tertutup oleh tombol Back (kompatibel predictive back)
        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                if (search.text.isNotEmpty()) search.setText("")
            }
        })

        loadApps()
        setupAds()
    }

    private fun setupAds() {
        MobileAds.initialize(this) {}
        val container = findViewById<FrameLayout>(R.id.ad_container)
        container.post {
            val density = resources.displayMetrics.density
            val widthDp = (container.width / density).toInt().coerceAtLeast(320)
            bannerView = AdView(this).apply {
                adUnitId = getString(R.string.admob_banner_id)
                setAdSize(AdSize.getCurrentOrientationAnchoredAdaptiveBannerAdSize(this@MainActivity, widthDp))
            }
            container.addView(bannerView)
            bannerView?.loadAd(AdRequest.Builder().build())
        }
    }

    override fun onResume() {
        super.onResume()
        bannerView?.resume()
    }

    override fun onPause() {
        bannerView?.pause()
        super.onPause()
    }

    override fun onDestroy() {
        bannerView?.destroy()
        unregisterReceiver(packageReceiver)
        super.onDestroy()
    }

    // Tekan Home saat sudah di launcher: reset pencarian & scroll ke atas
    override fun onNewIntent(intent: Intent?) {
        super.onNewIntent(intent)
        if (intent?.action == Intent.ACTION_MAIN) {
            search.setText("")
            search.clearFocus()
            list.scrollToPosition(0)
        }
    }

    private fun loadApps() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val pm = packageManager
        allApps = pm.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .map {
                AppInfo(
                    label = it.loadLabel(pm).toString(),
                    packageName = it.activityInfo.packageName,
                    icon = it.loadIcon(pm)
                )
            }
            .sortedBy { it.label.lowercase() }
        filter(search.text?.toString().orEmpty())
    }

    private fun filter(query: String) {
        val q = query.trim().lowercase()
        adapter.submit(if (q.isEmpty()) allApps else allApps.filter { it.label.lowercase().contains(q) })
    }

    private fun launch(app: AppInfo) {
        packageManager.getLaunchIntentForPackage(app.packageName)?.let { startActivity(it) }
    }

    private fun showMenu(app: AppInfo) {
        AlertDialog.Builder(this)
            .setTitle(app.label)
            .setItems(arrayOf(getString(R.string.app_info), getString(R.string.uninstall))) { _, which ->
                val uri = Uri.parse("package:${app.packageName}")
                if (which == 0) {
                    startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, uri))
                } else {
                    startActivity(Intent(Intent.ACTION_DELETE, uri))
                }
            }
            .show()
    }
}
