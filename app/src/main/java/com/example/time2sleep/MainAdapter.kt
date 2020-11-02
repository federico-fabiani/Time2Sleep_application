package com.example.time2sleep

import android.app.Activity
import android.app.TimePickerDialog
import android.content.Context
import android.text.InputType
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.recyclerview.widget.RecyclerView
import com.example.time2sleep.model.ChartData
import com.example.time2sleep.model.Device
import com.example.time2sleep.model.SmartAlarm
import com.google.gson.GsonBuilder
import com.google.gson.JsonArray
import com.google.gson.JsonObject
import com.jjoe64.graphview.helper.StaticLabelsFormatter
import com.jjoe64.graphview.series.DataPoint
import com.jjoe64.graphview.series.LineGraphSeries
import kotlinx.android.synthetic.main.charts_dialog.*
import kotlinx.android.synthetic.main.device.view.*
import kotlinx.android.synthetic.main.settings_dialog.*
import okhttp3.*
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.logging.HttpLoggingInterceptor
import org.jetbrains.anko.toast
import java.io.IOException
import java.text.SimpleDateFormat
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.*

private lateinit var alarmFormatted: LocalDateTime
private lateinit var nightFormatted: LocalDateTime
private lateinit var network_name: String

class MainAdapter(val devices: List<Device>, val parentContext: Context, val network_name: String): RecyclerView.Adapter<CustomViewHolder>() {
    val parentActivity = parentContext as Activity


    override fun getItemCount(): Int {
        return devices.count()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CustomViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val recycledDevice = layoutInflater.inflate(R.layout.device, parent, false)
        return  CustomViewHolder(recycledDevice)
    }

    override fun onBindViewHolder(holder: CustomViewHolder, position: Int) {
        val dev = devices[position]
        val smartAlarm = SmartAlarm()
        val deviceURL = "http://${dev.ip}:${dev.port}"
        fetchDevice(holder.view, deviceURL, smartAlarm)

        holder.view.setting_button.setOnClickListener {
            val settingsDialogView= LayoutInflater.from(parentContext).inflate(
                R.layout.settings_dialog,
                null
            )
            val settingsDialogBuilder = AlertDialog.Builder(parentContext).setView(
                settingsDialogView
            )
                .setTitle(holder.view.room_name.text.toString() + " settings:")
            settingsDialogBuilder.setCancelable(false)
            val settingsAlertDialog = settingsDialogBuilder.show()

            // Initial settings
            setInitialSetting(smartAlarm, settingsAlertDialog)

            // Setting alarm time
            settingsAlertDialog.alarmSwitch.setOnCheckedChangeListener { _, isChecked ->
//                if (isChecked) {
//                    settingsAlertDialog.alarmInput.setBackgroundColor(Color.TRANSPARENT)
//                }
//                else {settingsAlertDialog.alarmInput.setBackgroundColor(Color.LTGRAY) }

                if (isChecked && settingsAlertDialog.nightSwitch.isChecked) {
                    settingsAlertDialog.adaptableSwitch.isClickable = true
                } else {
                    settingsAlertDialog.adaptableSwitch.isClickable = false
                    settingsAlertDialog.adaptableSwitch.isChecked = false
                }
            }

            settingsAlertDialog.nightSwitch.setOnCheckedChangeListener { _, isChecked ->
                if (isChecked) {
                    settingsAlertDialog.lightSwitch.isClickable = true
                } else {
                    settingsAlertDialog.lightSwitch.isClickable = false
                    settingsAlertDialog.lightSwitch.isChecked = false
                }
                if (isChecked && settingsAlertDialog.alarmSwitch.isChecked) {
                    settingsAlertDialog.adaptableSwitch.isClickable = true
                } else {
                    settingsAlertDialog.adaptableSwitch.isClickable = false
                    settingsAlertDialog.adaptableSwitch.isChecked = false
                }
            }

            settingsAlertDialog.alarmInput.setOnClickListener{ setTimeFromPicker(settingsAlertDialog.alarmInput) }
            settingsAlertDialog.nightInput.setOnClickListener{ setTimeFromPicker(settingsAlertDialog.nightInput) }

            settingsAlertDialog.closeButton.setOnClickListener { settingsAlertDialog.dismiss() }
            settingsAlertDialog.saveButton.setOnClickListener {
                if (settingsAlertDialog.nameInput.text.toString().isNullOrEmpty()) {
                    parentActivity.toast("Don't leave room name empty")
                } else {
                    saveNewSettings(settingsAlertDialog, smartAlarm, deviceURL, holder.view)
                    settingsAlertDialog.dismiss()
                }
            }
        }

        holder.view.plots_button.setOnClickListener {
            val chartDialogView= LayoutInflater.from(parentContext).inflate(
                R.layout.charts_dialog,
                null
            )
            val chartDialogBuilder = AlertDialog.Builder(parentContext).setView(chartDialogView)
                .setTitle(holder.view.room_name.text.toString() + " data:")
            chartDialogBuilder.setCancelable(true)
            val chartAlertDialog = chartDialogBuilder.show()
            fetchChart(chartAlertDialog)
        }

        holder.view.light_button.setOnClickListener {
            val request = Request.Builder()
                .url("$deviceURL/toggle_light/")
                .build()
            val client = OkHttpClient.Builder().build()

            client.newCall(request).enqueue(object : Callback {
                override fun onResponse(call: Call, response: Response) {
                    parentActivity.runOnUiThread {
                        parentActivity.toast("Light toggled")
                    }
                }

                override fun onFailure(call: Call, e: IOException) {
                    parentActivity.runOnUiThread {
                        parentActivity.toast("Nope")
                    }
                }
            })
        }
    }

    private fun fetchChart(chartAlertDialog: AlertDialog) {
        chartAlertDialog.chart_container.visibility = View.GONE
        val request = Request.Builder().url("https://api.thingspeak.com/channels/1127842/feeds.json?days=1").build()
        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val gson = GsonBuilder().create()
                    val dataJson = gson.fromJson(body, ChartData::class.java)
                    parentActivity.runOnUiThread {
                        chartAlertDialog.chart_container.visibility = View.VISIBLE
                        chartAlertDialog.chart_loading.visibility = View.GONE
                        val last_update_formatted = dataJson.channel.updated_at.split("T")[0]
                        chartAlertDialog.last_seen_text.text =
                            "Last update: ${last_update_formatted}"
                        populateChart(dataJson, chartAlertDialog)
                    }
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                parentActivity.toast("Impossible to retrieve chart")
                chartAlertDialog.dismiss()
            }
        })
    }

    private fun populateChart(dataJson: ChartData, chartAlertDialog: AlertDialog) {
//        parentActivity.longToast(dataJson.toString())
        var tempData = emptyArray<DataPoint>()
        var humData = emptyArray<DataPoint>()
        var noiseData = emptyArray<DataPoint>()
        var sleepData = emptyArray<DataPoint>()
        var x = 0.0
        var labels = emptyArray<String>()

        for (f in dataJson.feeds) {
//            2020-10-03T13:59:41Z
            tempData += DataPoint(x, f.field4)
            humData += DataPoint(x, f.field5)
            noiseData += DataPoint(x, f.field2)
            sleepData += DataPoint(x, f.field6)
            if (x%40 == 0.0) {
                val temp = LocalDateTime.parse(
                    f.created_at, DateTimeFormatter.ofPattern(
                        "yyyy-MM-dd'T'HH:mm:ss'Z'"))
                labels += temp.format(DateTimeFormatter.ofPattern("HH:mm"))
            }
            x += 1
        }
        val tempSerie = LineGraphSeries(tempData)
        val humSerie = LineGraphSeries(humData)
        val noiseSerie = LineGraphSeries(noiseData)
        val sleepSerie = LineGraphSeries(sleepData)
        var staticLabelsFormatter = StaticLabelsFormatter(chartAlertDialog.temp_chart)
        staticLabelsFormatter.setHorizontalLabels(labels)
        chartAlertDialog.temp_chart.gridLabelRenderer.labelFormatter = staticLabelsFormatter
//        chartAlertDialog.temp_chart.viewport.isYAxisBoundsManual = true
//        chartAlertDialog.temp_chart.viewport.setMinY(tempSerie.lowestValueY)
//        chartAlertDialog.temp_chart.viewport.setMaxY(tempSerie.highestValueY)
//        chartAlertDialog.temp_chart.viewport.isXAxisBoundsManual = true
//        chartAlertDialog.temp_chart.viewport.setMinX(tempSerie.highestValueX-40*3)
//        chartAlertDialog.temp_chart.viewport.setMaxX(tempSerie.highestValueX)
//        chartAlertDialog.temp_chart.viewport.isScrollable = true
        chartAlertDialog.temp_chart.addSeries(tempSerie)

        staticLabelsFormatter = StaticLabelsFormatter(chartAlertDialog.hum_chart)
        staticLabelsFormatter.setHorizontalLabels(labels)
        chartAlertDialog.hum_chart.gridLabelRenderer.labelFormatter = staticLabelsFormatter
//        chartAlertDialog.hum_chart.viewport.isYAxisBoundsManual = true
//        chartAlertDialog.hum_chart.viewport.setMinY(humSerie.lowestValueY)
//        chartAlertDialog.hum_chart.viewport.setMaxY(humSerie.highestValueY)
//        chartAlertDialog.hum_chart.viewport.isXAxisBoundsManual = true
//        chartAlertDialog.hum_chart.viewport.setMinX(humSerie.lowestValueX)
//        chartAlertDialog.hum_chart.viewport.setMaxX(humSerie.highestValueX)
//        chartAlertDialog.hum_chart.viewport.isScalable = true
        chartAlertDialog.hum_chart.addSeries(humSerie)

        staticLabelsFormatter = StaticLabelsFormatter(chartAlertDialog.noise_chart)
        staticLabelsFormatter.setHorizontalLabels(labels)
        chartAlertDialog.noise_chart.gridLabelRenderer.labelFormatter = staticLabelsFormatter
//        chartAlertDialog.noise_chart.viewport.isYAxisBoundsManual = true
//        chartAlertDialog.noise_chart.viewport.setMinY(noiseSerie.lowestValueY)
//        chartAlertDialog.noise_chart.viewport.setMaxY(noiseSerie.highestValueY)
//        chartAlertDialog.noise_chart.viewport.isXAxisBoundsManual = true
//        chartAlertDialog.noise_chart.viewport.setMinX(noiseSerie.lowestValueX)
//        chartAlertDialog.noise_chart.viewport.setMaxX(noiseSerie.highestValueX)
//        chartAlertDialog.noise_chart.viewport.isScalable = true
        chartAlertDialog.noise_chart.addSeries(noiseSerie)

        staticLabelsFormatter = StaticLabelsFormatter(chartAlertDialog.sleep_state_chart)
        staticLabelsFormatter.setHorizontalLabels(labels)
        staticLabelsFormatter.setVerticalLabels(arrayOf("deep", "light"))
        chartAlertDialog.sleep_state_chart.gridLabelRenderer.labelFormatter = staticLabelsFormatter
//        chartAlertDialog.sleep_state_chart.viewport.isYAxisBoundsManual = true
//        chartAlertDialog.sleep_state_chart.viewport.setMinY(sleepSerie.lowestValueY)
//        chartAlertDialog.sleep_state_chart.viewport.setMaxY(sleepSerie.highestValueY)
//        chartAlertDialog.sleep_state_chart.viewport.isXAxisBoundsManual = true
//        chartAlertDialog.sleep_state_chart.viewport.setMinX(sleepSerie.lowestValueX)
//        chartAlertDialog.sleep_state_chart.viewport.setMaxX(sleepSerie.highestValueX)
//        chartAlertDialog.sleep_state_chart.viewport.isScalable = true
//        chartAlertDialog.sleep_state_chart.gridLabelRenderer.labelFormatter = staticLabelsFormatter
        chartAlertDialog.sleep_state_chart.addSeries(sleepSerie)
    }

    private fun saveNewSettings(
        settingsAlertDialog: AlertDialog,
        smartAlarm: SmartAlarm,
        deviceURL: String,
        itemView: View
    ) {
        smartAlarm.room_name = settingsAlertDialog.nameInput.text.toString()
        var sensors = emptyArray<String>()
        if (settingsAlertDialog.temperatureCheck.isChecked) {sensors += "temperature"}
        if (settingsAlertDialog.humidityCheck.isChecked) {sensors += "humidity"}
        if (settingsAlertDialog.vibrationCheck.isChecked) {sensors += "vibration"}
        if (settingsAlertDialog.motionCheck.isChecked) {sensors += "motion"}
        if (settingsAlertDialog.noiseCheck.isChecked) {sensors += "noise"}
        smartAlarm.sensors = sensors.toList()
        var actuators = emptyArray<String>()
        if (settingsAlertDialog.alarmCheck.isChecked) {actuators += "alarm"}
        if (settingsAlertDialog.lightCheck.isChecked) {actuators += "light"}
        smartAlarm.actuators = actuators.toList()
        smartAlarm.alarm_set = settingsAlertDialog.alarmSwitch.isChecked
        smartAlarm.alarm_time = alarmFormatted.format(DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm"))
        smartAlarm.night_monitoring = settingsAlertDialog.nightSwitch.isChecked
        smartAlarm.night_start = nightFormatted.format(DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm"))
        smartAlarm.light_set = settingsAlertDialog.lightSwitch.isChecked
        smartAlarm.adaptive_alarm = settingsAlertDialog.adaptableSwitch.isChecked

        val logging = HttpLoggingInterceptor()
        logging.level = (HttpLoggingInterceptor.Level.BODY)
        val json = JsonObject()
        json.addProperty("network_name", network_name)
        json.addProperty("room_name", smartAlarm.room_name)
        json.addProperty("alarm_set", smartAlarm.alarm_set)
        json.addProperty("alarm_time", smartAlarm.alarm_time)
        json.addProperty("night_monitoring", smartAlarm.night_monitoring)
        json.addProperty("night_start", smartAlarm.night_start)
        json.addProperty("light_set", smartAlarm.light_set)
        json.addProperty("adaptive_alarm", smartAlarm.adaptive_alarm)
        val jsonSensors = JsonArray()
        val jsonActuators = JsonArray()
        for (s in smartAlarm.sensors) { jsonSensors.add(s) }
        for (a in smartAlarm.actuators) { jsonActuators.add(a) }
        json.add("sensors", jsonSensors)
        json.add("actuators", jsonActuators)

        val postBody = json.toString().toRequestBody("application/json: charset=utf-8".toMediaTypeOrNull())
        val request = Request.Builder()
            .url("$deviceURL/changeConfig/")
            .post(postBody)
            .build()
        val client = OkHttpClient.Builder().addInterceptor(logging).build()

        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                parentActivity.runOnUiThread {
                    Toast.makeText(parentContext, "Configuration saved", Toast.LENGTH_SHORT).show()
                    itemView.room_name.text = smartAlarm.room_name
                    if (smartAlarm.alarm_set) {
                        itemView.room_alarm.text = "Alarm: ${
                            alarmFormatted.format(
                                DateTimeFormatter.ofPattern(
                                    "HH:mm"
                                )
                            )
                        }"
                    } else {
                        itemView.room_alarm.text = "No alarm set yet"
                    }
                    if (smartAlarm.night_monitoring) {
                        itemView.room_night.text = "Night start: ${
                            nightFormatted.format(
                                DateTimeFormatter.ofPattern(
                                    "HH:mm"
                                )
                            )
                        }"
                    } else
                        itemView.room_night.text = "No night monitoring"
                    itemView.itemLoadingIcon.visibility = View.GONE
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                parentActivity.runOnUiThread {
                    Toast.makeText(
                        parentContext,
                        "Failed to update configuration",
                        Toast.LENGTH_SHORT
                    ).show()
                }
            }
        })
    }

    private fun setInitialSetting(smartAlarm: SmartAlarm, settingsAlertDialog: AlertDialog) {
        settingsAlertDialog.nightInput.inputType = InputType.TYPE_NULL
        settingsAlertDialog.alarmInput.inputType = InputType.TYPE_NULL
        settingsAlertDialog.nameInput.setText(smartAlarm.room_name)
        for (s in smartAlarm.sensors) {
            when(s) {
                "temperature" -> settingsAlertDialog.temperatureCheck.isChecked = true
                "humidity" -> settingsAlertDialog.humidityCheck.isChecked = true
                "vibration" -> settingsAlertDialog.vibrationCheck.isChecked = true
                "motion" -> settingsAlertDialog.motionCheck.isChecked = true
                "noise" -> settingsAlertDialog.noiseCheck.isChecked = true
            }
        }
        for (a in smartAlarm.actuators) {
            when(a) {
                "light" -> settingsAlertDialog.lightCheck.isChecked = true
                "alarm" -> settingsAlertDialog.alarmCheck.isChecked = true
            }
        }
        settingsAlertDialog.alarmSwitch.isChecked = smartAlarm.alarm_set
        settingsAlertDialog.alarmInput.setText(alarmFormatted.format(DateTimeFormatter.ofPattern("HH:mm")))
        settingsAlertDialog.alarmInput.hint = smartAlarm.alarm_time
        settingsAlertDialog.nightSwitch.isChecked = smartAlarm.night_monitoring
        settingsAlertDialog.nightInput.setText(nightFormatted.format(DateTimeFormatter.ofPattern("HH:mm")))
        settingsAlertDialog.nightInput.hint = smartAlarm.night_start
        settingsAlertDialog.lightSwitch.isChecked = smartAlarm.light_set
        settingsAlertDialog.adaptableSwitch.isChecked = smartAlarm.adaptive_alarm

        if (settingsAlertDialog.nightSwitch.isChecked) {
            settingsAlertDialog.lightSwitch.isClickable = true
            if (settingsAlertDialog.alarmSwitch.isChecked) {
                settingsAlertDialog.adaptableSwitch.isClickable = true
            } else {
                settingsAlertDialog.adaptableSwitch.isClickable = false
                settingsAlertDialog.adaptableSwitch.isChecked = false
            }
        } else {
            settingsAlertDialog.lightSwitch.isClickable = false
            settingsAlertDialog.lightSwitch.isChecked = false
            settingsAlertDialog.adaptableSwitch.isClickable = false
            settingsAlertDialog.adaptableSwitch.isChecked = false
        }

    }

    private fun setTimeFromPicker(textInput: EditText?){
        val cal = Calendar.getInstance()
        val h = cal.get(Calendar.HOUR_OF_DAY)
        val m = cal.get(Calendar.MINUTE)
        val timeSetListener = TimePickerDialog.OnTimeSetListener { timePicker, hour, minute ->
            cal.set(Calendar.HOUR_OF_DAY, hour)
            cal.set(Calendar.MINUTE, minute)
            textInput?.setText(SimpleDateFormat("HH:mm").format(cal.time))
            if (hour < h || (hour == h && minute < m)) {
                cal.add(Calendar.DAY_OF_MONTH, 1)
            }
            textInput?.hint =  SimpleDateFormat("yyyy,MM,dd,HH,mm").format(cal.time)
            if (textInput?.id == R.id.alarmInput ) {
                alarmFormatted = LocalDateTime.parse(
                    textInput.hint,
                    DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm")
                )
            }
            if (textInput?.id == R.id.nightInput) {
                nightFormatted = LocalDateTime.parse(
                    textInput.hint,
                    DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm")
                )
            }
        }
        TimePickerDialog(parentContext, timeSetListener, h, m, true).show()
    }

    private fun fetchDevice(itemView: View, deviceURL: String, smartAlarm: SmartAlarm) {
        itemView.itemLoadingIcon.visibility = View.VISIBLE
        val request = Request.Builder().url(deviceURL).build()
        val client = OkHttpClient()
        client.newCall(request).enqueue(object : Callback {
            override fun onResponse(call: Call, response: Response) {
                val body = response.body?.string()
                if (!body.isNullOrEmpty()) {
                    val gson = GsonBuilder().create()
                    val deviceJson = gson.fromJson(body, SmartAlarm::class.java)
                    smartAlarm.copyFieldFrom(deviceJson)
                }
                parentActivity.runOnUiThread {
                    itemView.room_name.text = smartAlarm.room_name
                    if (!body.isNullOrEmpty()) {
                        alarmFormatted = LocalDateTime.parse(
                            smartAlarm.alarm_time, DateTimeFormatter.ofPattern(
                                "yyyy,MM,dd,HH,mm"
                            )
                        )
                        nightFormatted = LocalDateTime.parse(
                            smartAlarm.night_start, DateTimeFormatter.ofPattern(
                                "yyyy,MM,dd,HH,mm"
                            )
                        )
                    } else {
                        val nowFormatted = LocalDateTime.now()
                            .format(DateTimeFormatter.ofPattern("yyyy,MM,dd,HH,mm"))
                        alarmFormatted = LocalDateTime.parse(
                            nowFormatted, DateTimeFormatter.ofPattern(
                                "yyyy,MM,dd,HH,mm"
                            )
                        )
                        nightFormatted = LocalDateTime.parse(
                            nowFormatted, DateTimeFormatter.ofPattern(
                                "yyyy,MM,dd,HH,mm"
                            )
                        )
                    }

                    if (smartAlarm.alarm_set) {
                        itemView.room_alarm.text = "Alarm: ${
                            alarmFormatted.format(
                                DateTimeFormatter.ofPattern(
                                    "HH:mm"
                                )
                            )
                        }"
                    } else {
                        itemView.room_alarm.text = "No alarm set yet"
                    }
                    if (smartAlarm.night_monitoring) {
                        itemView.room_night.text = "Night start: ${
                            nightFormatted.format(
                                DateTimeFormatter.ofPattern(
                                    "HH:mm"
                                )
                            )
                        }"
                    } else {
                        itemView.room_night.text = "No night monitoring"
                    }
                    itemView.itemLoadingIcon.visibility = View.GONE
                }
            }

            override fun onFailure(call: Call, e: IOException) {
                parentActivity.runOnUiThread {
                    Toast.makeText(
                        parentContext,
                        "Impossible to connect to device: ${itemView.room_name.text}",
                        Toast.LENGTH_SHORT
                    ).show()
                    itemView.itemLoadingIcon.visibility = View.GONE
                }
            }
        })
    }
}


class CustomViewHolder(val view: View): RecyclerView.ViewHolder(view)


