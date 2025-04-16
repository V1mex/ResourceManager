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
import androidx.lifecycle.lifecycleScope
import com.example.resourcemanager.databinding.ActivityProcessDetailBinding
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class ProcessDetailActivity : ComponentActivity() {
    private lateinit var binding: ActivityProcessDetailBinding
    private var packageName: String? = null // Зберігаємо packageName
    // Додайте цю константу до класу
    companion object {
        private const val TAG = "BatteryUsage" // Тег для логів
    }
    data class SourceData(
        val time: String,
        val batteryPercentage: String,
        val batteryMah: String // Нове поле для mAh (наприклад, "0.12 mAh" або "N/A")
    )

    private fun calculatePercentage(mah: String, totalMah: Double): Pair<String, String> {
        val mahValue = mah.toDoubleOrNull() ?: 0.0
        return if (mahValue > 0 && totalMah > 0) {
            val percentage = String.format("%.4f%%", (mahValue / totalMah) * 100)
            val mahFormatted = String.format("%.4f mAh", mahValue)
            Pair(percentage, mahFormatted)
        } else {
            Pair("N/A", "N/A")
        }
    }
    // Ваша існуюча функція getUidForPackage
    private fun getUidForPackage(packageName: String): String? {
        // ... (код залишається без змін, але переконайтеся, що логування помилок там є)
        return try {
            val process = Runtime.getRuntime().exec("su -c pm list packages -U") // Краще додати --user 0, якщо можливо?
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream)) // Додано читання помилок
            val output = reader.readText()
            val errorOutput = errorReader.readText() // Додано читання помилок
            reader.close()
            errorReader.close()
            val exitCode = process.waitFor()

            if (exitCode != 0 || errorOutput.isNotBlank()) {
                Log.e(TAG, "pm list packages command failed. Exit code: $exitCode, Error: $errorOutput")
                // Можна спробувати іншу команду, якщо ця не працює, наприклад "dumpsys package $packageName | grep uid:"
            }

            // Шукаємо рядок, що містить ім'я пакету
            val packageLine = output.lines().find { it.contains(packageName) && it.contains("uid:") } // Шукаємо uid: замість uid:

            if (packageLine != null) {
                // Витягуємо UID з uid:xxxx
                val uidMatch = Regex("uid:(\\d+)").find(packageLine)
                val foundUid = uidMatch?.groupValues?.getOrNull(1)
                if (foundUid != null) {
                    Log.d(TAG,"Extracted UID $foundUid from line: $packageLine")
                    return foundUid
                } else {
                    Log.w(TAG,"Found line for $packageName but failed to extract userId: $packageLine")
                    // Спробуємо старий метод як запасний?
                    return packageLine.substringAfter("uid:")?.trim()?.takeIf { it.isNotEmpty() }
                }
            } else {
                Log.w(TAG, "Could not find line containing '$packageName' and 'uid:' in pm list output.")
                // Можна спробувати запасний метод пошуку UID тут
                return null
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error getting UID for $packageName: ${e.message}", e)
            null
        }
    }

    private suspend fun getBatteryUsageAndTime(packageName: String?): Pair<String, Map<String, SourceData>> = withContext(Dispatchers.IO) {
        if (packageName == null) {
            Log.w(TAG, "PackageName is null, cannot get battery usage.")
            return@withContext Pair("N/A", emptyMap())
        }
        Log.d(TAG, "Attempting to get battery usage for package: $packageName")

        val uid = getUidForPackage(packageName)
        if (uid == null) {
            Log.w(TAG, "Could not retrieve UID for package: $packageName")
            return@withContext Pair("N/A", emptyMap())
        }
        Log.i(TAG, "Found UID for $packageName: $uid")

        val formattedUid = if (uid.startsWith("10") && uid.length >= 5) {
            "u0a${uid.substring(2)}"
        } else {
            uid
        }
        Log.d(TAG, "Formatted UID for dumpsys: $formattedUid")

        var output: String
        try {
            val process = Runtime.getRuntime().exec("su -c dumpsys batterystats --charged")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val errorReader = BufferedReader(InputStreamReader(process.errorStream))
            val outputBuilder = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                outputBuilder.append(line).append("\n")
            }
            output = outputBuilder.toString()
            val errorOutput = errorReader.readText()
            reader.close()
            errorReader.close()
            val exitCode = process.waitFor()
            if (exitCode != 0) {
                Log.e(TAG, "dumpsys command failed with exit code $exitCode. Error output: $errorOutput")
                return@withContext Pair("N/A", emptyMap())
            }
            if (output.isBlank()) {
                Log.e(TAG, "dumpsys command returned empty output. Error output: $errorOutput")
                return@withContext Pair("N/A", emptyMap())
            }
            Log.d(TAG, "dumpsys batterystats output length: ${output.length}")
        } catch (e: Exception) {
            Log.e(TAG, "Error executing or reading dumpsys command for $packageName: ${e.message}", e)
            return@withContext Pair("N/A", emptyMap())
        }

        try {
            var totalMah = 0.0
            var appMah = "N/A"
            val sourceData = mutableMapOf<String, SourceData>().apply {
                put("CPU Foreground Services", SourceData("N/A", "N/A", "N/A"))
                put("CPU Foreground", SourceData("N/A", "N/A", "N/A"))
                put("CPU Background", SourceData("N/A", "N/A", "N/A"))
                put("Foreground Services", SourceData("N/A", "N/A", "N/A"))
                put("Foreground Activities", SourceData("N/A", "N/A", "N/A"))
                put("Wakelock", SourceData("N/A", "N/A", "N/A"))
                put("Sensors", SourceData("N/A", "N/A", "N/A"))
                put("Network Usage", SourceData("N/A", "N/A", "N/A"))
                put("JobScheduler", SourceData("N/A", "N/A", "N/A"))
                put("Sync", SourceData("N/A", "N/A", "N/A"))
                put("Alarms", SourceData("N/A", "N/A", "N/A"))
                put("Top", SourceData("N/A", "N/A", "N/A"))
            }
            var foundUidSection = false
            var processedLinesInUidSection = 0
            var inEstimatedPowerSection = false
            var processingUid = false

            // Знаходимо загальне споживання
            val totalDrainMatch = Regex("Computed drain: (\\d+\\.?\\d*),").find(output)
            if (totalDrainMatch != null && totalDrainMatch.groupValues.size > 1) {
                totalMah = totalDrainMatch.groupValues[1].toDoubleOrNull() ?: 0.0
                Log.i(TAG, "Total computed drain: $totalMah mAh")
            } else {
                Log.w(TAG, "Could not find 'Computed drain:' in dumpsys output.")
            }

            // Регулярні вирази
            val uidPatterns = listOf(
                Regex("^\\s*UID ${Regex.escape(formattedUid)}:", RegexOption.IGNORE_CASE),
                Regex("^\\s*${Regex.escape(formattedUid)}:", RegexOption.IGNORE_CASE)
            )
            val mahPattern = Regex("(\\d*\\.\\d+)|(\\d+)\\s*(?:mah|\\(calculated\\)|drain)", RegexOption.IGNORE_CASE)
            val timePatternHmsMs = Regex("(\\d+d)?\\s*(\\d+h)?\\s*(\\d+m)?\\s*(\\d+s)?\\s*(\\d+ms)?", RegexOption.IGNORE_CASE)

            Log.d(TAG, "Starting line-by-line parsing of dumpsys output for UID $formattedUid...")
            val lines = output.lines()

            for (i in lines.indices) {
                val currentLine = lines[i]
                var isUidStartLine = false

                if (currentLine.contains("Estimated power use (mAh):", ignoreCase = true)) {
                    inEstimatedPowerSection = true
                    Log.d(TAG, "Entered Estimated power use section at line $i")
                } else if (inEstimatedPowerSection && currentLine.trim().isEmpty()) {
                    inEstimatedPowerSection = false
                    Log.d(TAG, "Exited Estimated power use section at line $i")
                }

                if (!foundUidSection) {
                    for (pattern in uidPatterns) {
                        if (pattern.containsMatchIn(currentLine)) {
                            foundUidSection = true
                            isUidStartLine = true
                            processingUid = true
                            processedLinesInUidSection = 0
                            Log.i(TAG, ">>> Found START of UID $formattedUid section at line $i: '$currentLine'")
                            break
                        }
                    }
                }

                if (foundUidSection) {
                    if (processedLinesInUidSection < 50 || currentLine.contains("Foreground", ignoreCase = true) || currentLine.contains("mah", ignoreCase = true)) {
                        Log.v(TAG, "    [UID $formattedUid Section Line $processedLinesInUidSection]: '$currentLine'")
                    }
                    processedLinesInUidSection++

                    if (inEstimatedPowerSection) {
                        val isDifferentUidSectionStart = !isUidStartLine && uidPatterns.any { it.containsMatchIn(currentLine.replace(formattedUid, "different")) }
                        if (!isUidStartLine && (!currentLine.startsWith(" ") || isDifferentUidSectionStart)) {
                            Log.i(TAG, "<<< Heuristic END of UID $formattedUid section detected at line $i: '$currentLine'")
                            foundUidSection = false
                            processingUid = false
                            continue
                        }
                    } else {
                        val isNewUidSection = !isUidStartLine && uidPatterns.any { it.containsMatchIn(currentLine.replace(formattedUid, "different")) }
                        if (!isUidStartLine && (isNewUidSection || currentLine.trim().startsWith(formattedUid.plus(":").replace("u0a196:", "u0a197:")))) {
                            Log.i(TAG, "<<< Detailed UID $formattedUid section ended at line $i: '$currentLine'")
                            foundUidSection = false
                            processingUid = false
                            continue
                        }
                    }

                    // Пошук mAh (загальне)
                    if (appMah == "N/A" && inEstimatedPowerSection) {
                        val mahMatch = mahPattern.find(currentLine)
                        if (mahMatch != null) {
                            val matchedValue = mahMatch.groupValues[1].takeIf { it.isNotEmpty() } ?: mahMatch.groupValues[2]
                            if (matchedValue.isNotEmpty()) {
                                appMah = matchedValue
                                Log.i(TAG, "    +++ Found appMah: $appMah in line: '$currentLine'")
                            }
                        }
                    }

                    // Пошук cpu:fgs
                    if (currentLine.contains("cpu:fgs=", ignoreCase = true)) {
                        val timeString = currentLine.substringAfter("cpu:fgs=").substringAfter("(").substringBefore(")")
                        val batteryString = currentLine.substringAfter("fgs:").substringBefore("(").trim()
                        Log.d(TAG, "    ??? Extracted time string for cpu:fgs: '$timeString'")
                        val timeMatch = timePatternHmsMs.find(timeString)
                        if (timeMatch != null) {
                            val parsedMs = parseTimeFromHmsMs(timeMatch)
                            if (parsedMs > 0) {
                                val (percentage, mahFormatted) = calculatePercentage(batteryString, totalMah)
                                sourceData["CPU Foreground Services"] = SourceData(
                                    time = formatBatteryTimeMs(parsedMs),
                                    batteryPercentage = percentage,
                                    batteryMah = mahFormatted
                                )
                                Log.i(TAG, "    +++ Parsed cpu:fgs: time=${sourceData["CPU Foreground Services"]!!.time}, batteryPercentage=${percentage}, batteryMah=${mahFormatted}")
                            }
                        }
                    }

                    // Пошук cpu:fg
                    if (currentLine.contains("cpu:fg=", ignoreCase = true)) {
                        val timeString = currentLine.substringAfter("cpu:fg=").substringAfter("(").substringBefore(")")
                        val batteryString = currentLine.substringAfter("fg:").substringBefore("(").trim()
                        Log.d(TAG, "    ??? Extracted time string for cpu:fg: '$timeString'")
                        val timeMatch = timePatternHmsMs.find(timeString)
                        if (timeMatch != null) {
                            val parsedMs = parseTimeFromHmsMs(timeMatch)
                            if (parsedMs > 0) {
                                val (percentage, mahFormatted) = calculatePercentage(batteryString, totalMah)
                                sourceData["CPU Foreground"] = SourceData(
                                    time = formatBatteryTimeMs(parsedMs),
                                    batteryPercentage = percentage,
                                    batteryMah = mahFormatted
                                )
                                Log.i(TAG, "    +++ Parsed cpu:fg: time=${sourceData["CPU Foreground"]!!.time}, batteryPercentage=${percentage}, batteryMah=${mahFormatted}")
                            }
                        }
                    }

                    // Пошук cpu:bg
                    if (currentLine.contains("cpu:bg=", ignoreCase = true)) {
                        val timeString = currentLine.substringAfter("cpu:bg=").substringAfter("(").substringBefore(")")
                        val batteryString = currentLine.substringAfter("bg:").substringBefore("(").trim()
                        Log.d(TAG, "    ??? Extracted time string for cpu:bg: '$timeString'")
                        val timeMatch = timePatternHmsMs.find(timeString)
                        if (timeMatch != null) {
                            val parsedMs = parseTimeFromHmsMs(timeMatch)
                            if (parsedMs > 0) {
                                val (percentage, mahFormatted) = calculatePercentage(batteryString, totalMah)
                                sourceData["CPU Background"] = SourceData(
                                    time = formatBatteryTimeMs(parsedMs),
                                    batteryPercentage = percentage,
                                    batteryMah = mahFormatted
                                )
                                Log.i(TAG, "    +++ Parsed cpu:bg: time=${sourceData["CPU Background"]!!.time}, batteryPercentage=${percentage}, batteryMah=${mahFormatted}")
                            }
                        }
                    }
                }

                // Пошук Foreground services
                if (processingUid && currentLine.contains("Foreground services:", ignoreCase = true)) {
                    Log.d(TAG, "    ??? Searching for time in Foreground services line: '$currentLine'")
                    val timeString = currentLine.substringAfter("Foreground services:").substringBefore("realtime").trim()
                    Log.d(TAG, "    ??? Extracted time string for Foreground services: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            sourceData["Foreground Services"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = "N/A",
                                batteryMah = "N/A"
                            )
                            Log.i(TAG, "    +++ Parsed Foreground services: time=${sourceData["Foreground Services"]!!.time}")
                        }
                    }
                }

                // Пошук Foreground activities
                if (processingUid && currentLine.contains("Foreground activities:", ignoreCase = true)) {
                    Log.d(TAG, "    ??? Searching for time in Foreground activities line: '$currentLine'")
                    val timeString = currentLine.substringAfter("Foreground activities:").substringBefore("realtime").trim()
                    Log.d(TAG, "    ??? Extracted time string for Foreground activities: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            sourceData["Foreground Activities"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = "N/A",
                                batteryMah = "N/A"
                            )
                            Log.i(TAG, "    +++ Parsed Foreground activities: time=${sourceData["Foreground Activities"]!!.time}")
                        }
                    }
                }

                // Пошук wakelock
                if (processingUid && currentLine.contains("wakelock=", ignoreCase = true)) {
                    val timeString = currentLine.substringAfter("wakelock=").substringAfter("(").substringBefore(")")
                    val batteryString = currentLine.substringAfter("wakelock=").substringBefore("(").trim()
                    Log.d(TAG, "    ??? Extracted time string for wakelock: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            val (percentage, mahFormatted) = calculatePercentage(batteryString, totalMah)
                            sourceData["Wakelock"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = percentage,
                                batteryMah = mahFormatted
                            )
                            Log.i(TAG, "    +++ Parsed wakelock: time=${sourceData["Wakelock"]!!.time}, batteryPercentage=${percentage}, batteryMah=${mahFormatted}")
                        }
                    }
                }

                // Пошук sensors
                if (processingUid && currentLine.contains("sensors=", ignoreCase = true)) {
                    val timeString = currentLine.substringAfter("sensors=").substringAfter("(").substringBefore(")")
                    val batteryString = currentLine.substringAfter("sensors=").substringBefore("(").trim()
                    Log.d(TAG, "    ??? Extracted time string for sensors: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            val (percentage, mahFormatted) = calculatePercentage(batteryString, totalMah)
                            sourceData["Sensors"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = percentage,
                                batteryMah = mahFormatted
                            )
                            Log.i(TAG, "    +++ Parsed sensors: time=${sourceData["Sensors"]!!.time}, batteryPercentage=${percentage}, batteryMah=${mahFormatted}")
                        }
                    }
                }

                // Пошук Network usage (wifi або mobile)
                if (processingUid && currentLine.contains("wifi=", ignoreCase = true)) {
                    val timeString = currentLine.substringAfter("wifi=").substringAfter("(").substringBefore(")")
                    val batteryString = currentLine.substringAfter("wifi=").substringBefore("(").trim()
                    Log.d(TAG, "    ??? Extracted time string for wifi: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            val (percentage, mahFormatted) = calculatePercentage(batteryString, totalMah)
                            sourceData["Network Usage"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = percentage,
                                batteryMah = mahFormatted
                            )
                            Log.i(TAG, "    +++ Parsed wifi: time=${sourceData["Network Usage"]!!.time}, batteryPercentage=${percentage}, batteryMah=${mahFormatted}")
                        }
                    }
                }

                // Пошук JobScheduler
                if (processingUid && currentLine.contains("Job completions:", ignoreCase = true)) {
                    val timeString = currentLine.substringAfter("Job completions:").substringBefore("(").trim()
                    Log.d(TAG, "    ??? Extracted time string for JobScheduler: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            sourceData["JobScheduler"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = "N/A",
                                batteryMah = "N/A"
                            )
                            Log.i(TAG, "    +++ Parsed JobScheduler: time=${sourceData["JobScheduler"]!!.time}")
                        }
                    }
                }

                // Пошук Sync
                if (processingUid && currentLine.contains("Sync:", ignoreCase = true)) {
                    val timeString = currentLine.substringAfter("Sync:").substringBefore("(").trim()
                    Log.d(TAG, "    ??? Extracted time string for Sync: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            sourceData["Sync"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = "N/A",
                                batteryMah = "N/A"
                            )
                            Log.i(TAG, "    +++ Parsed Sync: time=${sourceData["Sync"]!!.time}")
                        }
                    }
                }

                // Пошук Alarms
                if (processingUid && currentLine.contains("Alarm:", ignoreCase = true)) {
                    val timeString = currentLine.substringAfter("Alarm:").substringBefore("(").trim()
                    Log.d(TAG, "    ??? Extracted time string for Alarms: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            sourceData["Alarms"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = "N/A",
                                batteryMah = "N/A"
                            )
                            Log.i(TAG, "    +++ Parsed Alarms: time=${sourceData["Alarms"]!!.time}")
                        }
                    }
                }

                // Пошук Top
                if (processingUid && currentLine.contains("top:", ignoreCase = true)) {
                    val timeString = currentLine.substringAfter("top:").substringBefore("(").trim()
                    Log.d(TAG, "    ??? Extracted time string for Top: '$timeString'")
                    val timeMatch = timePatternHmsMs.find(timeString)
                    if (timeMatch != null) {
                        val parsedMs = parseTimeFromHmsMs(timeMatch)
                        if (parsedMs > 0) {
                            sourceData["Top"] = SourceData(
                                time = formatBatteryTimeMs(parsedMs),
                                batteryPercentage = "N/A",
                                batteryMah = "N/A"
                            )
                            Log.i(TAG, "    +++ Parsed Top: time=${sourceData["Top"]!!.time}")
                        }
                    }
                }
            }
            Log.d(TAG, "Finished line-by-line parsing.")

            // Форматування загального відсотка
            val percentage: String
            if (appMah != "N/A" && totalMah > 0) {
                val mahValue = appMah.toDoubleOrNull() ?: 0.0
                percentage = if (mahValue > 0) String.format("%.1f%%", (mahValue / totalMah) * 100) else "0.0%"
                Log.d(TAG, "Calculating percentage: $mahValue / $totalMah * 100")
            } else {
                percentage = "N/A"
                Log.w(TAG, "Percentage is N/A. appMah: $appMah, totalMah: $totalMah")
            }

            Log.i(TAG, "Final result for $packageName (UID $formattedUid): Percentage=$percentage, Sources=$sourceData")
            return@withContext Pair(percentage, sourceData)
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing dumpsys output for $packageName: ${e.message}", e)
            return@withContext Pair("N/A", emptyMap())
        }
    }

    private fun parseTimeFromHmsMs(matchResult: MatchResult): Long {
        var totalMs = 0L
        try {
            for (i in 1 until matchResult.groups.size) {
                val group = matchResult.groups[i]
                if (group != null) {
                    val valueString = group.value.filter { it.isDigit() }
                    val value = valueString.toLongOrNull() ?: 0L
                    when {
                        group.value.contains("d", ignoreCase = true) -> totalMs += value * 24 * 3600 * 1000
                        group.value.contains("h", ignoreCase = true) -> totalMs += value * 3600 * 1000
                        group.value.contains("m", ignoreCase = true) && !group.value.contains("ms", ignoreCase = true) -> totalMs += value * 60 * 1000
                        group.value.contains("s", ignoreCase = true) && !group.value.contains("ms", ignoreCase = true) -> totalMs += value * 1000
                        group.value.contains("ms", ignoreCase = true) -> totalMs += value
                    }
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing time from HMSMS format: ${matchResult.value}", e)
            return -1L
        }
        Log.d(TAG, "Parsed HMSMS time string '${matchResult.value}' to $totalMs ms")
        return totalMs
    }

    // Переконайтеся, що formatBatteryTimeMs коректно обробляє 0 та від'ємні значення
    private fun formatBatteryTimeMs(totalMs: Long): String {
        if (totalMs < 0) return "N/A" // Якщо час не знайдено або помилка
        if (totalMs < 1000) return "0s" // Якщо менше секунди, показуємо 0s

        // ... (решта логіки форматування без змін)
        val totalSeconds = totalMs / 1000
        val hours = totalSeconds / 3600
        val minutes = (totalSeconds % 3600) / 60
        val seconds = totalSeconds % 60

        val parts = mutableListOf<String>()
        if (hours > 0) parts.add("${hours}h")
        if (minutes > 0) parts.add("${minutes}m")
        // Завжди показуємо секунди, якщо години та хвилини нульові, або якщо секунди > 0
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
        lifecycleScope.launch {
            val (pkgName, appIcon) = getAppInfo(pid, processInfo!!.cmd)
            packageName = pkgName // Зберігаємо packageName
            withContext(Dispatchers.Main) {
                binding.packageName.text = "Package Name: ${packageName ?: "N/A"}"
                binding.appIcon.setImageDrawable(appIcon)
            }

            val (batteryPercentage, sourceData) = getBatteryUsageAndTime(packageName)
            withContext(Dispatchers.Main) {
                // Загальні дані
                binding.batteryUsage.text = "Battery Usage: $batteryPercentage"

                if(batteryPercentage!="N/A"){
                    processInfo.batteryUsageAvailable=true
                    binding.settingsButton.visibility=View.VISIBLE
                    binding.tableLabel.text="Battery and Time Usage by Source:"
                    binding.sourceTable.visibility=View.VISIBLE
                }
                else{
                    processInfo.batteryUsageAvailable=false
                    binding.tableLabel.text="Uh oh :(\nSeems like this is a system process\nNo battery stats for this one \uD83D\uDE14"
                }

                // Заповнення таблиці
                binding.timeCpuFgs.text = sourceData["CPU Foreground Services"]?.time ?: "N/A"
                binding.batteryCpuFgs.text = sourceData["CPU Foreground Services"]?.batteryPercentage ?: "N/A"
                binding.mahCpuFgs.text = sourceData["CPU Foreground Services"]?.batteryMah ?: "N/A"

                binding.timeCpuFg.text = sourceData["CPU Foreground"]?.time ?: "N/A"
                binding.batteryCpuFg.text = sourceData["CPU Foreground"]?.batteryPercentage ?: "N/A"
                binding.mahCpuFg.text = sourceData["CPU Foreground"]?.batteryMah ?: "N/A"

                binding.timeCpuBg.text = sourceData["CPU Background"]?.time ?: "N/A"
                binding.batteryCpuBg.text = sourceData["CPU Background"]?.batteryPercentage ?: "N/A"
                binding.mahCpuBg.text = sourceData["CPU Background"]?.batteryMah ?: "N/A"

                binding.timeForegroundServices.text = sourceData["Foreground Services"]?.time ?: "N/A"
                binding.batteryForegroundServices.text = sourceData["Foreground Services"]?.batteryPercentage ?: "N/A"
                binding.mahForegroundServices.text = sourceData["Foreground Services"]?.batteryMah ?: "N/A"

                binding.timeForegroundActivities.text = sourceData["Foreground Activities"]?.time ?: "N/A"
                binding.batteryForegroundActivities.text = sourceData["Foreground Activities"]?.batteryPercentage ?: "N/A"
                binding.mahForegroundActivities.text = sourceData["Foreground Activities"]?.batteryMah ?: "N/A"

                binding.timeWakelock.text = sourceData["Wakelock"]?.time ?: "N/A"
                binding.batteryWakelock.text = sourceData["Wakelock"]?.batteryPercentage ?: "N/A"
                binding.mahWakelock.text = sourceData["Wakelock"]?.batteryMah ?: "N/A"

                binding.timeSensors.text = sourceData["Sensors"]?.time ?: "N/A"
                binding.batterySensors.text = sourceData["Sensors"]?.batteryPercentage ?: "N/A"
                binding.mahSensors.text = sourceData["Sensors"]?.batteryMah ?: "N/A"

                binding.timeNetwork.text = sourceData["Network Usage"]?.time ?: "N/A"
                binding.batteryNetwork.text = sourceData["Network Usage"]?.batteryPercentage ?: "N/A"
                binding.mahNetwork.text = sourceData["Network Usage"]?.batteryMah ?: "N/A"

                binding.timeJobscheduler.text = sourceData["JobScheduler"]?.time ?: "N/A"
                binding.batteryJobscheduler.text = sourceData["JobScheduler"]?.batteryPercentage ?: "N/A"
                binding.mahJobscheduler.text = sourceData["JobScheduler"]?.batteryMah ?: "N/A"

                binding.timeSync.text = sourceData["Sync"]?.time ?: "N/A"
                binding.batterySync.text = sourceData["Sync"]?.batteryPercentage ?: "N/A"
                binding.mahSync.text = sourceData["Sync"]?.batteryMah ?: "N/A"

                binding.timeAlarms.text = sourceData["Alarms"]?.time ?: "N/A"
                binding.batteryAlarms.text = sourceData["Alarms"]?.batteryPercentage ?: "N/A"
                binding.mahAlarms.text = sourceData["Alarms"]?.batteryMah ?: "N/A"

                binding.timeTop.text = sourceData["Top"]?.time ?: "N/A"
                binding.batteryTop.text = sourceData["Top"]?.batteryPercentage ?: "N/A"
                binding.mahTop.text = sourceData["Top"]?.batteryMah ?: "N/A"
            }
        }

        // Динамічне оновлення CPU, MEM та Uptime
        lifecycleScope.launch {
            while (isActive) {
                val updatedProcess = getProcessByPid(pid)
                withContext(Dispatchers.Main) {
                    if (updatedProcess != null) {
                        binding.cpuUsage.text = "CPU Usage: ${updatedProcess.cpu}%"
                        binding.memUsage.text = "Memory Usage: ${updatedProcess.mem}%"
                        binding.uptime.text = "Uptime: ${formatUptime(updatedProcess.uptime)}"
                        binding.user.text = "User: ${updatedProcess.user}"
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
            if (processInfo == null) {
                Toast.makeText(this, "Помилка: інформація про процес відсутня", Toast.LENGTH_SHORT).show()
                finish()
                return@setOnClickListener
            }

            val isSystemProcess = processInfo!!.user in listOf(
                "system", "root", "shell", "audioserver", "media", "credstore",
                "drm", "wifi", "keystore", "gps", "mediaex", "radio", "gpu_service",
                "webview_zygote", "secure_element", "vendor_qrtr", "cameraserver",
                "mdnsr", "vendor_rfs", "lmkd", "mediacodec", "bluetooth", "camera",
                "incidentd", "logd", "network_stack", "nobody", "prng_seeder",
                "statsd", "tombstoned"
            )

            if (isSystemProcess) {
                val message = when (processInfo!!.user) {
                    "audioserver" -> "Це процес аудіосистеми. Завершення може порушити звук на пристрої та викликати нестабільність. Продовжити?"
                    "credstore" -> "Це процес системи безпеки. Завершення може порушити автентифікацію та доступ до захищених даних. Продовжити?"
                    "media" -> "Це процес медіасистеми. Завершення може порушити відтворення медіа та вплинути на галерею чи камеру. Продовжити?"
                    "drm" -> "Це процес керування захищеним контентом. Завершення може порушити відтворення захищених медіа (наприклад, Netflix). Продовжити?"
                    "wifi" -> "Це процес Wi-Fi. Завершення призведе до втрати Wi-Fi-з’єднання. Продовжити?"
                    "keystore" -> "Це процес криптографічної безпеки. Завершення порушить автентифікацію та функції безпеки. Продовжити?"
                    "gps" -> "Це процес геолокації. Завершення може порушити GPS і навігаційні функції. Продовжити?"
                    "mediaex" -> "Це процес обробки медіаданих. Завершення може вплинути на відтворення медіафайлів. Продовжити?"
                    "radio" -> "Це процес телефонії. Завершення може порушити дзвінки, SMS і мобільний інтернет. Продовжити?"
                    "gpu_service" -> "Це процес графічного рендерингу. Завершення викличе графічні збої. Продовжити?"
                    "webview_zygote" -> "Це процес вебконтенту. Завершення може порушити відображення вебсторінок у додатках. Продовжити?"
                    "secure_element" -> "Це процес безпеки (наприклад, NFC-платежі). Завершення порушить безконтактні платежі та інші функції. Продовжити?"
                    "vendor_qrtr" -> "Це процес апаратної комунікації. Завершення може порушити модем, звук чи інші функції. Продовжити?"
                    "cameraserver" -> "Це процес камери. Завершення може порушити роботу камери в усіх додатках. Продовжити?"
                    "mdnsr" -> "Це процес мережевого виявлення. Завершення може порушити функції, як-от Chromecast. Продовжити?"
                    "vendor_rfs" -> "Це процес доступу до апаратних файлів. Завершення може порушити апаратні функції. Продовжити?"
                    "lmkd" -> "Це процес керування пам’яттю. Завершення може викликати збої додатків і нестабільність системи. Продовжити?"
                    "mediacodec" -> "Це процес кодування/декодування медіа. Завершення може порушити відтворення відео чи аудіо в додатках. Продовжити?"
                    "bluetooth" -> "Це процес Bluetooth. Завершення може порушити Bluetooth-з’єднання та функції, як-от гарнітури чи передача файлів. Продовжити?"
                    "camera" -> "Це процес камери. Завершення може порушити роботу камери в усіх додатках. Продовжити?"
                    "incidentd" -> "Це процес збору звітів про збої. Завершення може порушити діагностику системи. Продовжити?"
                    "logd" -> "Це процес системного логування. Завершення може порушити запис логів і діагностику. Продовжити?"
                    "network_stack" -> "Це процес мережевих функцій. Завершення може порушити Wi-Fi, мобільний інтернет чи VPN. Продовжити?"
                    "nobody" -> "Це процес із обмеженими правами. Завершення може вплинути на фонові системні задачі. Продовжити?"
                    "prng_seeder" -> "Це процес генерації випадкових чисел. Завершення може порушити криптографічні операції. Продовжити?"
                    "statsd" -> "Це процес збору статистики. Завершення може порушити моніторинг продуктивності системи. Продовжити?"
                    "tombstoned" -> "Це процес збереження звітів про збої. Завершення може порушити збереження даних про помилки. Продовжити?"
                    else -> "Це системний процес. Його завершення може спричинити нестабільність системи. Продовжити?"
                }
                AlertDialog.Builder(this)
                    .setTitle("Попередження")
                    .setMessage(message)
                    .setPositiveButton("Так") { _, _ ->
                        val success = killProcess(processInfo!!.pid)
                        if (success) {
                            Toast.makeText(this, "Процес ${processInfo!!.pid} завершено", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK, Intent().apply {
                                putExtra("process_killed", true)
                                putExtra("pid", processInfo!!.pid)
                            })
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle("Помилка")
                                .setMessage("Не вдалося завершити процес ${processInfo!!.pid}. Можливо, потрібні root-дозволи.")
                                .setPositiveButton("OK", null)
                                .show()
                            setResult(RESULT_CANCELED)
                        }
                        finish()
                    }
                    .setNegativeButton("Ні", null)
                    .show()
            } else {
                val packageName = getPackageNameFromUser(processInfo!!.user, processInfo!!.pid.toInt(), this)
                val appName = getAppNameFromPackage(packageName, this)
                val message = if (packageName != null) {
                    val available = processInfo.batteryUsageAvailable
                    if(available==null)
                        "Зачекаааай"
                    else
                        if (available)
                            "Це головний процес додатка '$appName'. Завершення зупинить цей додаток. Продовжити?"
                        else
                            "Це під-процес додатка '$appName'. Завершення може привести до збоїв або завершення цього додатку. Продовжити?"
                } else {
                    "Це невідомий процес. Завершення може вплинути на стабільність системи. Продовжити?"
                }
                AlertDialog.Builder(this)
                    .setTitle("Підтвердження")
                    .setMessage(message)
                    .setPositiveButton("Так") { _, _ ->
                        val success = killProcess(processInfo!!.pid)
                        if (success) {
                            Toast.makeText(this, "Процес ${processInfo!!.pid} завершено", Toast.LENGTH_SHORT).show()
                            setResult(RESULT_OK, Intent().apply {
                                putExtra("process_killed", true)
                                putExtra("pid", processInfo!!.pid)
                            })
                        } else {
                            AlertDialog.Builder(this)
                                .setTitle("Помилка")
                                .setMessage("Не вдалося завершити процес ${processInfo!!.pid}. Можливо, потрібні root-дозволи.")
                                .setPositiveButton("OK", null)
                                .show()
                            setResult(RESULT_CANCELED)
                        }
                        finish()
                    }
                    .setNegativeButton("Ні", null)
                    .show()
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
                        uptime = uptime,
                        batteryUsageAvailable = null
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

    private fun killProcess(pid: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "kill $pid"))
            process.waitFor()
            process.exitValue() == 0
        } catch (e: Exception) {
            Log.e("ProcessDetails", "Error killing process $pid: ${e.message}", e)
            false
        }
    }

    private fun getPackageNameFromUser(user: String, pid: Int, context: Context): String? {
        if (!user.startsWith("u0_a")) return null
        return try {
            // Спроба отримати UID із /proc/[pid]/status
            val uid = getUidFromPid(pid) ?: run {
                // Резервна формула
                val appId = user.removePrefix("u0_a").toInt()
                10000 + appId
            }

            Log.d("ProcessDetails", "Trying UID $uid for user $user, pid $pid")

            // Спроба через PackageManager
            val pm = context.packageManager
            var packages = pm.getPackagesForUid(uid)
            if (packages != null) {
                return packages.firstOrNull().also {
                    Log.d("ProcessDetails", "Found package via PackageManager: $it")
                }
            }

            // Резервний варіант: pm list packages -U
            Log.w("ProcessDetails", "PackageManager returned null, trying pm list packages")
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "pm list packages -U"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var packageName: String? = null
            reader.lineSequence().forEach { line ->
                if (line.contains("uid:$uid")) {
                    packageName = line.substringAfter("package:").substringBefore(" uid:")
                }
            }
            process.waitFor()

            packageName.also {
                if (it == null) {
                    Log.w("ProcessDetails", "No package found for UID $uid (user: $user, pid: $pid)")
                } else {
                    Log.d("ProcessDetails", "Found package via pm list packages: $it")
                }
            }
        } catch (e: Exception) {
            Log.e("ProcessDetails", "Error getting package for user $user, pid $pid: ${e.message}", e)
            null
        }
    }

    private fun getAppNameFromPackage(packageName: String?, context: Context): String {
        if (packageName == null) return "Невідомий додаток"
        return try {
            val pm = context.packageManager
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            Log.e("ProcessDetails", "Error getting app name for $packageName: ${e.message}", e)
            packageName // Повертаємо packageName як резервний варіант
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

    private fun getUidFromPid(pid: Int): Int? {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "cat /proc/$pid/status"))
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var uid: Int? = null
            reader.lineSequence().forEach { line ->
                if (line.startsWith("Uid:")) {
                    val parts = line.split("\\s+".toRegex())
                    if (parts.size > 1) {
                        uid = parts[1].toIntOrNull()
                    }
                }
            }
            process.waitFor()
            uid
        } catch (e: Exception) {
            Log.e("ProcessDetails", "Error getting UID for pid $pid: ${e.message}", e)
            null
        }
    }



}