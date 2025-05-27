package com.example.resourcemanager

import android.app.Activity.RESULT_OK
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import com.example.resourcemanager.databinding.FragmentProcessInfoBinding
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

class ProcessInfoFragment : Fragment() {

    private var _binding: FragmentProcessInfoBinding? = null
    private val binding get() = _binding!!

    private var processInfo: ProcessInfo? = null
    private var packageNameArg: String? = null
    private var batteryPercentageArg: String? = null

    // Таймер для оновлення
    private var updateJob: Job? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            processInfo = it.getSerializable("process_info") as ProcessInfo?
            packageNameArg = it.getString("package_name")
            batteryPercentageArg = it.getString("battery_percentage")
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentProcessInfoBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupInitialUI()
        setupClickListeners()
        startUpdates()
    }

    private fun setupInitialUI() {
        processInfo?.let {
            binding.pid.text = "PID: ${it.pid}"
            binding.uptime.text = "Uptime: ${formatUptime(it.uptime)}"
            binding.cpuUsage.text = "CPU Usage: ${it.cpu}%"
            binding.memUsage.text = "Memory Usage: ${it.mem}%"
            binding.user.text = "User: ${it.user}"
            binding.packageName.text = "Package Name: ${packageNameArg ?: "N/A"}"
            binding.batteryUsage.text = "Battery Usage: ${batteryPercentageArg ?: "Loading..."}"

            // Отримання PPID та батьківського процесу
            lifecycleScope.launch(Dispatchers.IO) {
                val (ppid, parentName) = getParentProcessInfo(it.pid)
                withContext(Dispatchers.Main) {
                    binding.ppid.text = "PPID: $ppid (Parent: $parentName)"
                }
            }

            // Налаштування кнопки налаштувань
            if (packageNameArg != null) {
                binding.settingsButton.visibility = View.VISIBLE
            }
        }
    }

    private fun setupClickListeners() {
        binding.findRelatedText.setOnClickListener{
            listener?.onUserSelected(processInfo?.user)
        }

        /*binding.findRelatedText.setOnClickListener {
            val resultIntent = Intent().apply {
                putExtra("selected_user", processInfo?.user)
            }
            activity?.setResult(ProcessDetailActivity.RESULT_OK, resultIntent)
            activity?.finish()
        }*/ //працює і так

        binding.settingsButton.setOnClickListener {
            packageNameArg?.let { pkg ->
                try {
                    val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                        data = Uri.parse("package:$pkg")
                    }
                    startActivity(intent)
                } catch (e: Exception) {
                    Log.e("ProcessInfoFragment", "Error opening settings for $pkg: ${e.message}")
                    Toast.makeText(requireContext(), "Не вдалося відкрити налаштування додатка", Toast.LENGTH_SHORT).show()
                }
            } ?: run {
                Toast.makeText(requireContext(), "Назва пакета недоступна", Toast.LENGTH_SHORT).show()
            }
        }

        binding.killButton.setOnClickListener {
            processInfo?.let { showKillConfirmationDialog(it) }
        }
    }

    private fun startUpdates() {
        updateJob?.cancel() // Скасувати попередні оновлення
        updateJob = lifecycleScope.launch {
            while (isActive) {
                processInfo?.pid?.let { pid ->
                    val updatedProcess = getProcessByPid(pid)
                    withContext(Dispatchers.Main) {
                        if (updatedProcess != null) {
                            binding.cpuUsage.text = "CPU Usage: ${updatedProcess.cpu}%"
                            binding.memUsage.text = "Memory Usage: ${updatedProcess.mem}%"
                            binding.uptime.text = "Uptime: ${formatUptime(updatedProcess.uptime)}"
                            binding.user.text = "User: ${updatedProcess.user}"
                            // Оновлюємо processInfo, якщо потрібно для kill dialog
                            processInfo = updatedProcess
                        } else {
                            Toast.makeText(requireContext(), "Процес $pid більше не існує", Toast.LENGTH_SHORT).show()
                            activity?.finish()
                        }
                    }
                }
                delay(1000)
            }
        }
    }

    // --- Функції з вашого Activity (адаптовані) ---

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
            Log.e("ProcessInfoFragment", "Error getting PPID: ${e.message}")
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
            Log.e("ProcessInfoFragment", "Error getting process name for PID $pid: ${e.message}")
            "N/A"
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
                        batteryUsageAvailable = processInfo?.batteryUsageAvailable // Зберігаємо старе значення
                    )
                } else {
                    null
                }
            }.firstOrNull()
        } catch (e: Exception) {
            Log.e("ProcessInfoFragment", "Error getting process $pid: ${e.message}")
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

    private fun showKillConfirmationDialog(processInfo: ProcessInfo) {
        val isSystemProcess = processInfo.user in listOf(
            "system", "root", "shell", "audioserver", "media", "credstore",
            "drm", "wifi", "keystore", "gps", "mediaex", "radio", "gpu_service",
            "webview_zygote", "secure_element", "vendor_qrtr", "cameraserver",
            "mdnsr", "vendor_rfs", "lmkd", "mediacodec", "bluetooth", "camera",
            "incidentd", "logd", "network_stack", "nobody", "prng_seeder",
            "statsd", "tombstoned"
        )

        val message = if (isSystemProcess) {
            "Це системний процес (${processInfo.user}). Його завершення може спричинити нестабільність системи. Продовжити?"
        } else {
            "Ви впевнені, що хочете завершити процес ${processInfo.pid} (${processInfo.cmd})?"
        }

        AlertDialog.Builder(requireContext())
            .setTitle("Попередження")
            .setMessage(message)
            .setPositiveButton("Так") { _, _ -> performKillProcess(processInfo.pid) }
            .setNegativeButton("Ні", null)
            .show()
    }

    private fun performKillProcess(pid: String) {
        lifecycleScope.launch(Dispatchers.IO) {
            val success = try {
                val process = Runtime.getRuntime().exec(arrayOf("su", "-c", "kill $pid"))
                process.waitFor()
                process.exitValue() == 0
            } catch (e: Exception) {
                Log.e("ProcessInfoFragment", "Error killing process $pid: ${e.message}", e)
                false
            }
            withContext(Dispatchers.Main) {
                if (success) {
                    Toast.makeText(requireContext(), "Процес $pid завершено", Toast.LENGTH_SHORT).show()
                    activity?.setResult(RESULT_OK, Intent().apply {
                        putExtra("process_killed", true)
                        putExtra("pid", pid)
                    })
                    activity?.finish()
                } else {
                    AlertDialog.Builder(requireContext())
                        .setTitle("Помилка")
                        .setMessage("Не вдалося завершити процес $pid. Можливо, потрібні root-дозволи.")
                        .setPositiveButton("OK", null)
                        .show()
                }
            }
        }
    }


    override fun onDestroyView() {
        super.onDestroyView()
        updateJob?.cancel() // Зупинити оновлення при знищенні View
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(processInfo: ProcessInfo, packageName: String?, batteryPercentage: String?) =
            ProcessInfoFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("process_info", processInfo)
                    putString("package_name", packageName)
                    putString("battery_percentage", batteryPercentage)
                }
            }
    }

    interface OnUserSelectedListener {
        fun onUserSelected(user: String?)
    }

    private lateinit var listener: OnUserSelectedListener

    override fun onAttach(context: Context) {
        super.onAttach(context)
        if (context is OnUserSelectedListener) {
            listener = context
        }
    }


}