package com.example.time2sleep

import android.app.Activity
import android.content.Context
import android.content.SharedPreferences
import android.content.SharedPreferences.Editor
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuItem
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import com.example.time2sleep.model.Catalog
import com.google.gson.GsonBuilder
import kotlinx.android.synthetic.main.catalog_dialog.*
import kotlinx.android.synthetic.main.catalog_dialog.view.*
import kotlinx.android.synthetic.main.content_main.*
import kotlinx.android.synthetic.main.network_name_dialog.*
import kotlinx.android.synthetic.main.toolbar_main.*
import okhttp3.*
import org.jetbrains.anko.longToast
import org.jetbrains.anko.toast
import java.io.IOException

private var catalogIP: String? = null
private var catalogPort: String? = null
private var catalogURL: String? = null
private lateinit var sharedPref: SharedPreferences
private lateinit var prefEditor: Editor
private var network_name: String? = null

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        setSupportActionBar(my_toolbar)
        recyclerView_main.layoutManager = LinearLayoutManager(this)

        sharedPref = getSharedPreferences("main_activity_pref", 0)
        prefEditor = sharedPref.edit()
        catalogIP = sharedPref.getString("catalog_ip", "")
        catalogPort = sharedPref.getString("catalog_port", "")
        network_name = sharedPref.getString("net_name", "")
        supportActionBar!!.title = network_name
        my_toolbar.setTitleTextAppearance(this, R.style.SevenSegmentTextAppearance)

        if (network_name.isNullOrEmpty()) {
            setNetworkName()
        }

        if (catalogIP != "" && catalogPort != "") {
            catalogURL = "http://$catalogIP:$catalogPort"
            fetchCatalog(this)
        }

        swiperefresh_main.setOnRefreshListener {
            if (catalogURL != "") {
                fetchCatalog(this)
                swiperefresh_main.isRefreshing = false
            }
            swiperefresh_main.isRefreshing = false
        }
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menuInflater.inflate(R.menu.menu_main, menu)
        return true
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        return when (item.itemId) {
            R.id.action_set_catalog -> {
                setCatalog()
                true
            }
            R.id.action_reload -> {
                fetchCatalog(this)
                true
            }
            R.id.action_edit_network -> {
                setNetworkName()
                true
            }
            else -> super.onOptionsItemSelected(item)
        }
    }

    private fun setNetworkName() {
        val netNameDialogView = LayoutInflater.from(this)
            .inflate(R.layout.network_name_dialog, null)
        val netNameDialogBuilder = AlertDialog.Builder(this).setView(netNameDialogView)
            .setTitle("Set your Network name:")
        netNameDialogBuilder.setCancelable(false)
        val netNameAlertDialog = netNameDialogBuilder.show()
        netNameAlertDialog.editNetworkName.setText(network_name)
        netNameAlertDialog.netNegativeButton.setOnClickListener { netNameAlertDialog.dismiss() }
        netNameAlertDialog.netPositiveButton.setOnClickListener {
            if (netNameAlertDialog.editNetworkName.text.toString().isNullOrEmpty()) {
                toast("Don't leave this field empty")
            } else {
                network_name = netNameAlertDialog.editNetworkName.text.toString()
                supportActionBar!!.title = network_name
                prefEditor.putString("net_name", network_name)
                prefEditor.apply()
                netNameAlertDialog.dismiss()

            }
        }
    }

    private fun setCatalog() {
        val catalogDialogView = LayoutInflater.from(this)
            .inflate(R.layout.catalog_dialog, null)
        if (catalogIP != "") {
            catalogDialogView.editCatalogIP.setText(catalogIP)
        }
        if (catalogPort != "") {
            catalogDialogView.editCatalogPort.setText(catalogPort)
        }
        val catalogDialogBuilder = AlertDialog.Builder(this).setView(catalogDialogView)
            .setTitle(resources.getString(R.string.dialog_set_catalog))
        catalogDialogBuilder.setCancelable(false)
        val catalogAlertDialog = catalogDialogBuilder.show()
        catalogAlertDialog.negativeButton.setOnClickListener { catalogAlertDialog.dismiss() }
        catalogAlertDialog.positiveButton.setOnClickListener {
            catalogIP = catalogAlertDialog.editCatalogIP.text.toString()
            catalogPort = catalogAlertDialog.editCatalogPort.text.toString()
            prefEditor.putString("catalog_ip", catalogIP).putString("catalog_port", catalogPort)
            prefEditor.apply()
            if (catalogPort != "" && catalogIP != "") {
                catalogURL = "http://$catalogIP:$catalogPort"
                fetchCatalog(this)
            }else {
                catalogURL = ""
                recyclerView_main.adapter = MainAdapter(emptyList(), this, network_name!!)
                longToast("Failed to reach the catalog")
            }
            catalogAlertDialog.dismiss()
        }
     }

    private fun fetchCatalog(parentContext: Context) {
        loadingIcon.visibility = View.VISIBLE
        val request = Request.Builder().url(catalogURL!!).build()
        val client = OkHttpClient()
        client.newCall(request).enqueue(object: Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                val gson = GsonBuilder().create()
                val newCatalog = gson.fromJson(body, Catalog::class.java)

                runOnUiThread {
                    longToast("Connected to the catalog!")
                    recyclerView_main.adapter = MainAdapter(newCatalog.devices, parentContext, network_name!!)
                    loadingIcon.visibility = View.GONE
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                runOnUiThread {
                    longToast("Failed to reach the catalog")
                    recyclerView_main.adapter = MainAdapter(emptyList(), parentContext, network_name!!)
                    loadingIcon.visibility = View.GONE
                }
            }
        })
    }
}



