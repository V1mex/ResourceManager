package com.example.resourcemanager

import android.content.Intent
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import com.example.resourcemanager.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

data class ProcessInfo(
    val pid: String,
    val user: String,
    val cpu: String,
    val mem: String,
    val cmd: String,
    val uptime: Long
) : java.io.Serializable

class MainActivity : ComponentActivity() {
    private var isFrozen = false
    private lateinit var mylayout: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var adapter: ProcessAdapter
    private val processList = mutableListOf<ProcessInfo>()
    private var sortType: String = "cpu"
    private var orderType: String = "desc"
    private var searchType: String = "pid"

    // Додаємо ActivityResultLauncher
    private val processDetailLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            scope.launch {
                val processes = sortProcesses(getProcesses())
                withContext(Dispatchers.Main) {
                    adapter.updateData(processes, mylayout.searchField.text.toString())
                }
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mylayout = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mylayout.root)

        mylayout.closeBtn.setOnClickListener {
            System.exit(0)
        }

        mylayout.freezeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isFrozen = isChecked
        }

        adapter = ProcessAdapter(this, processList)
        mylayout.processList.adapter = adapter

        // Обробка кліку на елемент ListView
        mylayout.processList.setOnItemClickListener { _, _, position, _ ->
            val process = adapter.getItem(position)
            val intent = Intent(this, ProcessDetailActivity::class.java)
            intent.putExtra("process_info", process)
            processDetailLauncher.launch(intent)
        }

        // Ініціалізація Spinner для сортування
        val sortOptions = arrayOf("Sort by CPU", "Sort by MEM", "Sort by Name", "Sort by Uptime")
        val sortAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, sortOptions)
        sortAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mylayout.sortSpinner.adapter = sortAdapter
        mylayout.sortSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                sortType = when (position) {
                    0 -> "cpu"
                    1 -> "mem"
                    2 -> "name"
                    3 -> "uptime"
                    else -> "cpu"
                }
                adapter.updateData(sortProcesses(processList), mylayout.searchField.text.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Ініціалізація Spinner для порядку сортування
        val orderOptions = arrayOf("Descending", "Ascending")
        val orderAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, orderOptions)
        orderAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mylayout.orderSpinner.adapter = orderAdapter
        mylayout.orderSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                orderType = when (position) {
                    0 -> "desc"
                    1 -> "asc"
                    else -> "desc"
                }
                adapter.updateData(sortProcesses(processList), mylayout.searchField.text.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        // Ініціалізація Spinner для пошуку
        val searchOptions = arrayOf("Search by Name", "Search by PID")
        val searchAdapter = ArrayAdapter(this, android.R.layout.simple_spinner_item, searchOptions)
        searchAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
        mylayout.searchSpinner.adapter = searchAdapter
        mylayout.searchSpinner.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: AdapterView<*>, view: View?, position: Int, id: Long) {
                searchType = when (position) {
                    0 -> "name"
                    1 -> "pid"
                    else -> "pid"
                }
                adapter.searchType = searchType
                adapter.filter(mylayout.searchField.text.toString())
            }
            override fun onNothingSelected(parent: AdapterView<*>) {}
        }

        mylayout.searchField.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                adapter.filter(s.toString())
            }
            override fun afterTextChanged(s: Editable?) {}
        })

        scope.launch {
            while (isActive) {
                if (!isFrozen) {
                    val cpuUsage = getCpuUsage()
                    val ramUsage = getRamUsage()
                    val processes = sortProcesses(getProcesses())

                    withContext(Dispatchers.Main) {
                        mylayout.infoLabel.text = "CPU: $cpuUsage% (Cores: ${getCpuCores()}) | RAM: $ramUsage%"
                        adapter.updateData(processes, mylayout.searchField.text.toString())
                    }
                }
                delay(1000)
            }
        }
    }

    private fun getCpuUsage(): String {
        return try {
            val process = Runtime.getRuntime().exec("su -c cat /proc/stat")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val line = reader.readLine()
            reader.close()

            val values = line.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle = values[3]
            val total = values.sum()

            Thread.sleep(500)

            val process2 = Runtime.getRuntime().exec("su -c cat /proc/stat")
            val reader2 = BufferedReader(InputStreamReader(process2.inputStream))
            val line2 = reader2.readLine()
            reader2.close()

            val values2 = line2.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle2 = values2[3]
            val total2 = values2.sum()

            val diffTotal = total2 - total
            val diffIdle = idle2 - idle

            ((100.0 * (diffTotal - diffIdle) / diffTotal)).toInt().toString()
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun getRamUsage(): String {
        return try {
            val process = Runtime.getRuntime().exec("cat /proc/meminfo")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val totalMem = reader.readLine().split("\\s+".toRegex())[1].toInt()
            val freeMem = reader.readLine().split("\\s+".toRegex())[1].toInt()
            reader.close()

            val usedMem = totalMem - freeMem
            ((100.0 * usedMem / totalMem)).toInt().toString()
        } catch (e: Exception) {
            "N/A"
        }
    }

    private fun getProcesses(): List<ProcessInfo> {
        return try {
            val process = Runtime.getRuntime().exec("su -c ps -A -o pid,user,%cpu,%mem,cmd,etime --sort=-%cpu")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines().drop(1)
            reader.close()

            lines.mapNotNull { line ->
                val parts = line.trim().split("\\s+".toRegex())
                if (parts.size >= 6) {
                    val pid = parts[0]
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
                    Log.e("GetProcesses", "Invalid line: $line")
                    null
                }
            }
        } catch (e: Exception) {
            Log.e("GetProcesses", "Error: ${e.message}")
            listOf()
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

    private fun sortProcesses(processes: List<ProcessInfo>): List<ProcessInfo> {
        val sorted = when (sortType) {
            "cpu" -> processes.sortedBy { it.cpu.toFloatOrNull() ?: 0f }
            "mem" -> processes.sortedBy { it.mem.toFloatOrNull() ?: 0f }
            "name" -> processes.sortedBy { it.cmd.lowercase() }
            "uptime" -> processes.sortedBy { it.uptime }
            else -> processes
        }
        return if (orderType == "desc") sorted.reversed() else sorted
    }

    private fun getCpuCores(): Int {
        return Runtime.getRuntime().availableProcessors()
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}