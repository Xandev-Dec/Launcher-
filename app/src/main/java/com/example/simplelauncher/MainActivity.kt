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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView

class MainActivity : AppCompatActivity() {

    private var allApps: List<AppInfo> = emptyList()
    private lateinit var adapter: AppAdapter
    private lateinit var search: EditText
    private lateinit var list: RecyclerView

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
        registerReceiver(packageReceiver, filter)

        loadApps()
    }

    override fun onDestroy() {
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

    // Launcher tidak boleh tertutup oleh tombol Back
    @Deprecated("Deprecated in Java")
    override fun onBackPressed() {
        if (search.text.isNotEmpty()) search.setText("")
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
