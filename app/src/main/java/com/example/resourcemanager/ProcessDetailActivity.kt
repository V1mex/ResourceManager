package com.example.resourcemanager

import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.View
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.core.content.ContextCompat
import com.example.resourcemanager.databinding.ActivityProcessDetailBinding
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

class ProcessDetailActivity : ComponentActivity() {
    private lateinit var binding: ActivityProcessDetailBinding
    private val scope = CoroutineScope(Dispatchers.IO)
    private var processInfo: ProcessInfo? = null
    private var packageName: String? = null // Зберігаємо packageName

    private fun getUidForPackage(packageName: String): String? {
        return try {
            val process = Runtime.getRuntime().exec("su -c pm list packages -U")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()
            val packageLine = output.lines().find { it.contains(packageName) }
            packageLine?.substringAfter("uid:")?.trim()
        } catch (e: Exception) {
            Log.e("BatteryUsage", "Error getting UID for $packageName: ${e.message}")
            null
        }
    }

    private fun getBatteryUsageAndTime(packageName: String?): Pair<String, String> {
        if (packageName == null) {
            Log.d("BatteryUsage", "PackageName is null")
            return Pair("N/A", "N/A")
        }

        Log.d("BatteryUsage", "Getting UID for package: $packageName")
        val uid = getUidForPackage(packageName)
        Log.d("BatteryUsage", "UID for $packageName: $uid")

        if (uid == null) {
            Log.d("BatteryUsage", "UID is null")
            return Pair("N/A", "N/A")
        }

        return try {
            val process = Runtime.getRuntime().exec("su -c dumpsys batterystats --charged")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val output = reader.readText()
            reader.close()

            Log.d("BatteryUsage", "dumpsys batterystats output length: ${output.length}")

            var totalMah = 0.0
            var appMah = "N/A"
            var appTime = "N/A"
            var timeFound = false

            output.lines().forEach { line ->
                if (line.contains("Computed drain:")) {
                    totalMah = line.substringAfter("Computed drain:").substringBefore(",").trim().toDoubleOrNull() ?: 0.0
                    Log.d("BatteryUsage", "Total computed drain: $totalMah mAh")
                }

                val uidPattern = "UID u0a${uid.substringAfter("10")}:"
                val uidPatternUpper = uidPattern.uppercase()

                if (line.uppercase().contains(uidPatternUpper)) {
                    Log.d("BatteryUsage", "Found line possibly containing info for UID $uid: $line")

                    if (appMah == "N/A") {
                        val mahMatch = Regex("\\d+(\\.\\d+)?").find(line.substringAfter(":"))?.value
                        if (mahMatch != null) {
                            appMah = mahMatch
                            Log.d("BatteryUsage", "Found appMah for UID $uid: $appMah from line: $line")
                        }
                    }

                    if (!timeFound) {
                        Log.d("BatteryUsage", "Attempting to parse time from the same line for UID $uid")
                        if (line.contains("foreground", ignoreCase = true) && line.contains("time=", ignoreCase = true)) {
                            val timeValue = line.substringAfter("time=").substringBefore(" ").trim()
                            Log.d("BatteryUsage", "Raw foreground time from line: $timeValue")
                            appTime = formatBatteryTime(timeValue)
                            Log.d("BatteryUsage", "Parsed foreground time: $appTime")
                            if (appTime != "N/A") timeFound = true
                        } else {
                            val fgKey = when {
                                line.contains(" fg:", ignoreCase = true) -> "fg:"
                                line.contains(" fgs:", ignoreCase = true) -> "fgs:"
                                else -> null
                            }

                            if (fgKey != null) {
                                Log.d("BatteryUsage", "Found '$fgKey' in line: $line")
                                val timeMatch = Regex("\\((\\d+h\\s*)?(\\d+m\\s*)?(\\d+s\\s*)?(\\d+ms)?\\)").find(line.substringAfter(fgKey))
                                if (timeMatch != null) {
                                    Log.d("BatteryUsage", "Found time match in parentheses: ${timeMatch.value}")
                                    var totalMs = 0L
                                    timeMatch.groups.filterNotNull().drop(1).forEach { group ->
                                        val valueString = group.value.filter { it.isDigit() }
                                        val value = valueString.toLongOrNull() ?: 0L
                                        when {
                                            group.value.contains("h") -> totalMs += value * 3600 * 1000
                                            group.value.contains("m") -> totalMs += value * 60 * 1000
                                            group.value.contains("s") -> totalMs += value * 1000
                                            group.value.contains("ms") -> totalMs += value
                                        }
                                    }
                                    Log.d("BatteryUsage", "Total ms from parentheses: $totalMs")
                                    if (totalMs > 0) {
                                        appTime = formatBatteryTimeMs(totalMs)
                                        Log.d("BatteryUsage", "Parsed $fgKey time from parentheses: $appTime")
                                        timeFound = true
                                    } else if (!timeFound) {
                                        appTime = "0s"
                                        Log.d("BatteryUsage", "$fgKey time from parentheses is zero")
                                    }
                                }
                            }
                        }
                    }
                }
            }

            if (!timeFound && appMah != "N/A") {
                Log.d("BatteryUsage", "Time not found after parsing, setting to 0s")
                appTime = "0s"
            }

            val percentage = if (appMah != "N/A" && appMah != "0.0" && totalMah > 0) {
                val mahValue = appMah.toDoubleOrNull() ?: 0.0
                if (mahValue > 0) String.format("%.1f%%", (mahValue / totalMah) * 100) else "0.0%"
            } else if (appMah == "0.0") {
                "0.0%"
            } else {
                "N/A"
            }

            binding.settingsButton.visibility = View.VISIBLE
            Log.d("BatteryUsage", "Final result: percentage=$percentage, time=$appTime")
            Pair(percentage, appTime)

        } catch (e: Exception) {
            Log.e("BatteryUsage", "Error in getBatteryUsageAndTime: ${e.message}", e)
            Pair("N/A", "N/A")
        }

    }

    private fun formatBatteryTime(timeStr: String): String {
        return try {
            val ms = timeStr.toLong()
            formatBatteryTimeMs(ms)
        } catch (e: NumberFormatException) {
            Log.e("BatteryUsage", "Error parsing time string '$timeStr' to long: ${e.message}")
            "N/A"
        } catch (e: Exception) {
            Log.e("BatteryUsage", "Error formatting time string '$timeStr': ${e.message}")
            "N/A"
        }
    }

    private fun formatBatteryTimeMs(totalMs: Long): String {
        if (totalMs < 0) return "N/A"
        if (totalMs < 1000) return "0s"

        val totalSeconds = totalMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        if (seconds > 0 || parts.isEmpty()) parts.add("${seconds}s")

        return parts.joinToString(" ")
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityProcessDetailBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Отримуємо дані з Intent
        val processInfo: ProcessInfo? = intent.getSerializableExtra("process_info", ProcessInfo::class.java)

        if (processInfo == null) {
            Toast.makeText(this, "Помилка: дані про процес відсутні", Toast.LENGTH_SHORT).show()
            finish()
            return
        }

        // Початкове заповнення UI
        val pid = processInfo!!.pid
        binding.pid.text = "PID: $pid"
        binding.uptime.text = "Uptime: ${formatUptime(processInfo!!.uptime)}"
        binding.cpuUsage.text = "CPU Usage: ${processInfo!!.cpu}%"
        binding.memUsage.text = "Memory Usage: ${processInfo!!.mem}%"
        binding.appName.text = processInfo!!.cmd

        // Отримання PPID та батьківського процесу
        val (ppid, parentName) = getParentProcessInfo(pid)
        binding.ppid.text = "PPID: $ppid (Parent: $parentName)"

        // Отримання packageName та іконки асинхронно
        scope.launch {
            val (pkgName, appIcon) = getAppInfo(pid, processInfo!!.cmd)
            packageName = pkgName // Зберігаємо packageName
            withContext(Dispatchers.Main) {
                binding.packageName.text = "Package Name: ${packageName ?: "N/A"}"
                binding.appIcon.setImageDrawable(appIcon)
            }
            val (batteryPercentage, batteryTime) = getBatteryUsageAndTime(packageName)
            withContext(Dispatchers.Main) {
                binding.batteryUsage.text = "Battery Usage: $batteryPercentage"
                binding.timeUsage.text = "Time Used: $batteryTime"
            }
        }

        // Динамічне оновлення CPU, MEM та Uptime
        scope.launch {
            while (isActive) {
                val updatedProcess = getProcessByPid(pid)
                withContext(Dispatchers.Main) {
                    if (updatedProcess != null) {
                        binding.cpuUsage.text = "CPU Usage: ${updatedProcess.cpu}%"
                        binding.memUsage.text = "Memory Usage: ${updatedProcess.mem}%"
                        binding.uptime.text = "Uptime: ${formatUptime(updatedProcess.uptime)}"
                    } else {
                        Toast.makeText(this@ProcessDetailActivity, "Процес $pid більше не існує", Toast.LENGTH_SHORT).show()
                        finish()
                    }
                }
                delay(1000)
            }
        }

        // Обробка кнопки Kill
        binding.killButton.setOnClickListener {
            val isSystemProcess = processInfo!!.user == "system" || processInfo!!.user == "root"
            if (isSystemProcess) {
                AlertDialog.Builder(this)
                    .setTitle("Попередження")
                    .setMessage("Це системний процес. Його завершення може спричинити нестабільність системи. Продовжити?")
                    .setPositiveButton("Так") { _, _ ->
                        killProcess(pid)
                        Toast.makeText(this, "Процес $pid завершено", Toast.LENGTH_SHORT).show()
                        setResult(RESULT_OK)
                        finish()
                    }
                    .setNegativeButton("Ні", null)
                    .show()
            } else {
                killProcess(pid)
                Toast.makeText(this, "Процес $pid завершено", Toast.LENGTH_SHORT).show()
                setResult(RESULT_OK)
                finish()
            }
        }

        // Обробка нової кнопки для відкриття налаштувань додатка
        binding.settingsButton.setOnClickListener {
            packageName?.let { pkg ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("ProcessDetail", "Error opening settings for $pkg: ${e.message}")
                    Toast.makeText(this, "Не вдалося відкрити налаштування додатка", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(this, "Назва пакета недоступна", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun getSystemContext(packageName: String): Context? {
        return try {
            createPackageContext(packageName, Context.CONTEXT_INCLUDE_CODE or Context.CONTEXT_IGNORE_SECURITY)
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error getting system context for $packageName: ${e.message}")
            null
        }
    }

    private fun getParentProcessInfo(pid: String): Pair<String, String> {
        return try {
            val process = Runtime.getRuntime().exec("su -c cat /proc/$pid/stat")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val statLine = reader.readLine()
            reader.close()

            val parts = statLine.split("\\s+".toRegex())
            if (parts.size >= 4) {
                val ppid = parts[3]
                val parentName = getProcessNameByPid(ppid)
                Pair(ppid, parentName)
            } else {
                Pair("N/A", "N/A")
            }
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error getting PPID: ${e.message}")
            Pair("N/A", "N/A")
        }
    }

    private fun getProcessNameByPid(pid: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c cat /proc/$pid/comm")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val name = reader.readLine()?.trim() ?: "N/A"
            reader.close()
            name
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error getting process name for PID $pid: ${e.message}")
            "N/A"
        }
    }

    private suspend fun getAppInfo(pid: String, cmd: String): Pair<String?, Drawable?> = withContext(Dispatchers.IO) {
        Log.d("ProcessDetail", "Attempting to get app info for PID: $pid, CMD: $cmd")
        try {
            val cmdline = readCmdline(pid)
            Log.d("ProcessDetail", "Raw cmdline: '$cmdline'")
            val cleanCmdline = cmdline.split("\u0000")[0]
            Log.d("ProcessDetail", "Clean cmdline: '$cleanCmdline'")
            val possiblePackageName = cleanCmdline.split(":")[0].trim()
            Log.d("ProcessDetail", "Possible packageName from cmdline: '$possiblePackageName'")

            if (possiblePackageName.isNotEmpty() && possiblePackageName.matches(Regex("^[a-zA-Z0-9._]+$"))) {
                val appIcon = getIconFromApk(possiblePackageName)
                    ?: ContextCompat.getDrawable(this@ProcessDetailActivity, android.R.drawable.sym_def_app_icon)
                return@withContext Pair(possiblePackageName, appIcon)
            }

            Log.d("ProcessDetail", "No package found for PID: $pid")
            val defaultIcon = ContextCompat.getDrawable(this@ProcessDetailActivity, android.R.drawable.sym_def_app_icon)
            return@withContext Pair(null, defaultIcon)
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error getting app info for PID $pid: ${e.message}")
            val defaultIcon = ContextCompat.getDrawable(this@ProcessDetailActivity, android.R.drawable.sym_def_app_icon)
            return@withContext Pair(null, defaultIcon)
        }
    }

    private fun readCmdline(pid: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c cat /proc/$pid/cmdline")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val cmdline = reader.readLine()?.trim() ?: ""
            reader.close()
            cmdline
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error reading cmdline for PID $pid: ${e.message}")
            ""
        }
    }

    private fun getIconFromApk(packageName: String): Drawable? {
        return try {
            val process = Runtime.getRuntime().exec("su -c pm path $packageName")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val apkPath = reader.readLine()?.replace("package:", "")?.trim()
            reader.close()

            Log.d("ProcessDetail", "APK path for $packageName: $apkPath")
            if (apkPath != null) {
                val packageInfo = packageManager.getPackageArchiveInfo(apkPath, PackageManager.GET_META_DATA.toInt())
                if (packageInfo != null) {
                    val appInfo = packageInfo.applicationInfo
                    appInfo?.sourceDir = apkPath
                    Log.d("ProcessDetail", "Loaded package info for $packageName from APK")
                    return appInfo?.loadIcon(packageManager)
                }
            }
            Log.d("ProcessDetail", "Failed to load package info for $packageName from APK")
            null
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error getting icon for $packageName: ${e.message}")
            null
        }
    }

    private fun getProcessByPid(pid: String): ProcessInfo? {
        return try {
            val process = Runtime.getRuntime().exec("su -c ps -p $pid -o pid,user,%cpu,%mem,cmd,etime")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines().drop(1)
            reader.close()

            lines.mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 6) {
                    val etime = parts[5]
                    val uptime = parseEtimeToSeconds(etime)
                    ProcessInfo(
                        pid = parts[0],
                        user = parts[1],
                        cpu = parts[2],
                        mem = parts[3],
                        cmd = parts.drop(4).dropLast(1).joinToString(" "),
                        uptime = uptime
                    )
                } else {
                    null
                }
            }.firstOrNull()
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error getting process $pid: ${e.message}")
            null
        }
    }

    private fun parseEtimeToSeconds(etime: String): Long {
        return try {
            val parts = etime.split("-")
            if (parts.size == 2) {
                val days = parts[0].toLongOrNull() ?: 0L
                val timeParts = parts[1].split(":")
                val hours = timeParts[0].toLongOrNull() ?: 0L
                val minutes = timeParts[1].toLongOrNull() ?: 0L
                val seconds = timeParts[2].toLongOrNull() ?: 0L
                days * 24 * 3600 + hours * 3600 + minutes * 60 + seconds
            } else {
                val timeParts = etime.split(":")
                when (timeParts.size) {
                    3 -> {
                        val hours = timeParts[0].toLongOrNull() ?: 0L
                        val minutes = timeParts[1].toLongOrNull() ?: 0L
                        val seconds = timeParts[2].toLongOrNull() ?: 0L
                        hours * 3600 + minutes * 60 + seconds
                    }
                    2 -> {
                        val minutes = timeParts[0].toLongOrNull() ?: 0L
                        val seconds = timeParts[1].toLongOrNull() ?: 0L
                        minutes * 60 + seconds
                    }
                    else -> 0L
                }
            }
        } catch (e: Exception) {
            Log.e("ParseEtime", "Error parsing etime: $etime, ${e.message}")
            0L
        }
    }

    private fun killProcess(pid: String) {
        try {
            Runtime.getRuntime().exec("su -c kill -9 $pid")
        } catch (e: Exception) {
            Log.e("ProcessDetail", "Error killing process $pid: ${e.message}")
            Toast.makeText(this, "Помилка при завершенні процесу", Toast.LENGTH_SHORT).show()
        }
    }

    private fun formatUptime(seconds: Long): String {
        val days = seconds / (24 * 3600)
        val hours = (seconds % (24 * 3600)) / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return if (days > 0) {
            "${days}d ${hours}h ${minutes}m ${secs}s"
        } else {
            "${hours}h ${minutes}m ${secs}s"
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}