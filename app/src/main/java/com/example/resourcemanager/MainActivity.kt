package com.example.resourcemanager
import android.os.Bundle
import android.widget.ArrayAdapter
import androidx.activity.ComponentActivity
import com.example.resourcemanager.databinding.ActivityMainBinding
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.InputStreamReader

class MainActivity : ComponentActivity() {
    private var isFrozen = false
    private lateinit var mylayout: ActivityMainBinding
    private val scope = CoroutineScope(Dispatchers.IO)
    private lateinit var adapter: ArrayAdapter<String>
    private val processList = mutableListOf<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        mylayout = ActivityMainBinding.inflate(layoutInflater)
        setContentView(mylayout.root)

        // Кнопка виходу
        mylayout.closeBtn.setOnClickListener {
            System.exit(0)
        }

        mylayout.freezeSwitch.setOnCheckedChangeListener { _, isChecked ->
            isFrozen = isChecked
        }


        // Ініціалізація адаптера з мутабельним списком
        adapter = ArrayAdapter(this, android.R.layout.simple_list_item_1, processList)
        mylayout.processList.adapter = adapter

        // Запуск оновлення даних
        scope.launch {
            while (isActive) {
                if (!isFrozen) {  // 🔹 Якщо список не "заморожений"
                    val cpuUsage = getCpuUsage()
                    val ramUsage = getRamUsage()
                    val processes = getProcesses()

                    withContext(Dispatchers.Main) {
                        mylayout.infoLabel.text = "CPU: $cpuUsage% | RAM: $ramUsage%"

                        // Оновлюємо список і повідомляємо адаптер
                        processList.clear()
                        processList.addAll(processes)
                        adapter.notifyDataSetChanged()
                    }

                    //delay(2000)
                }
                delay(1000) // Оновлення кожні 1 секунди
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

            val prevTotal = total
            val prevIdle = idle

            Thread.sleep(500)

            val process2 = Runtime.getRuntime().exec("su -c cat /proc/stat")
            val reader2 = BufferedReader(InputStreamReader(process2.inputStream))
            val line2 = reader2.readLine()
            reader2.close()

            val values2 = line2.split("\\s+".toRegex()).drop(1).map { it.toLong() }
            val idle2 = values2[3]
            val total2 = values2.sum()

            val diffTotal = total2 - prevTotal
            val diffIdle = idle2 - prevIdle

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

    private fun getProcesses(): List<String> {
        return try {
            val process = Runtime.getRuntime().exec("su -c ps -A -o pid,user,%cpu,%mem,cmd --sort=-%cpu")
            val reader = BufferedReader(InputStreamReader(process.inputStream))
            val lines = reader.readLines()//.drop(1) // Пропускаємо заголовок
            reader.close()
            //lines.take(50) // Беремо 10 топ процесів
            return(lines)
        } catch (e: Exception) {
            listOf("Немає даних")
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        scope.cancel()
    }
}
