package com.example.resourcemanager

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class ProcessAdapter(context: Context, private val processes: MutableList<ProcessInfo>) :
    ArrayAdapter<ProcessInfo>(context, R.layout.list_item, processes) {

    private var filteredProcesses = mutableListOf<ProcessInfo>()
    var searchType: String = "pid"

    init {
        filteredProcesses.addAll(processes)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context).inflate(R.layout.list_item, parent, false)
        val process = filteredProcesses[position]

        // Знаходимо TextView у макеті
        val cmdText = view.findViewById<TextView>(R.id.cmd_text)
        val pidText = view.findViewById<TextView>(R.id.pid_text)
        val userText = view.findViewById<TextView>(R.id.user_text)
        val cpuText = view.findViewById<TextView>(R.id.cpu_text)
        val memText = view.findViewById<TextView>(R.id.mem_text)
        val uptimeText = view.findViewById<TextView>(R.id.uptime_text)

        // Заповнюємо TextView даними
        cmdText.text = process.cmd
        pidText.text = "PID: ${process.pid}"
        userText.text = "User: ${process.user}"
        cpuText.text = "CPU: ${process.cpu}%"
        memText.text = "Memory: ${process.mem}%"
        uptimeText.text = "Uptime: ${formatUptime(process.uptime)}"

        return view
    }

    override fun getCount(): Int = filteredProcesses.size

    override fun getItem(position: Int): ProcessInfo = filteredProcesses[position]

    fun updateData(newProcesses: List<ProcessInfo>, query: String = "") {
        processes.clear()
        processes.addAll(newProcesses)
        filter(query)
    }

    fun filter(query: String) {
        filteredProcesses.clear()
        if (query.isEmpty()) {
            filteredProcesses.addAll(processes)
        } else {
            filteredProcesses.addAll(processes.filter {
                when (searchType) {
                    "pid" -> it.pid.contains(query, ignoreCase = true)
                    "name" -> it.cmd.contains(query, ignoreCase = true)
                    else -> false
                }
            })
        }
        notifyDataSetChanged()
    }

    private fun formatUptime(seconds: Long): String {
        val hours = seconds / 3600
        val minutes = (seconds % 3600) / 60
        val secs = seconds % 60
        return "${hours}h ${minutes}m ${secs}s"
    }
}