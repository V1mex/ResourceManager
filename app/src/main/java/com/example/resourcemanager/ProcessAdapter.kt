package com.example.resourcemanager

import android.content.Context
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView

class ProcessAdapter(context: Context, private val processes: MutableList<ProcessInfo>) :
    ArrayAdapter<ProcessInfo>(context, android.R.layout.simple_list_item_1, processes) {

    private var filteredProcesses = mutableListOf<ProcessInfo>()
    var searchType: String = "pid"

    init {
        filteredProcesses.addAll(processes)
    }

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = super.getView(position, convertView, parent)
        val process = filteredProcesses[position]
        (view as TextView).text = "PID: ${process.pid}, CMD: ${process.cmd}, CPU: ${process.cpu}%, MEM: ${process.mem}%, Uptime: ${formatUptime(process.uptime)}"
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