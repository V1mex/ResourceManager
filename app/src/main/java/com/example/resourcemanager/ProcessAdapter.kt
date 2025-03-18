package com.example.resourcemanager

import android.content.Context
import android.widget.ArrayAdapter

class ProcessAdapter(context: Context, private val processes: MutableList<String>) :
    ArrayAdapter<String>(context, android.R.layout.simple_list_item_1, processes) {

    fun updateData(newProcesses: List<String>) {
        processes.clear()  // Очищаємо старі дані
        processes.addAll(newProcesses)  // Додаємо нові процеси
        notifyDataSetChanged()  // Повідомляємо ListView про зміну
    }
}
