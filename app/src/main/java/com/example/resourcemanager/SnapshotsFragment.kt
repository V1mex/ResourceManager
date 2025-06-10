package com.example.resourcemanager

import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.TextView
import android.widget.Toast
import androidx.fragment.app.Fragment
import androidx.lifecycle.Observer
import androidx.lifecycle.lifecycleScope
import com.example.resourcemanager.databinding.FragmentSnapshotsBinding
import com.example.resourcemanager.sqliteNroom.Snapshot
import com.example.resourcemanager.sqliteNroom.SnapshotDao
import com.example.resourcemanager.sqliteNroom.SnapshotRepo
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.*

class SnapshotsFragment : Fragment() {

    private var _binding: FragmentSnapshotsBinding? = null
    private val binding get() = _binding!!

    private var processInfo: ProcessInfo? = null
    private lateinit var snapshotDao: SnapshotDao
    private var isDetailViewVisible = false
    private var selectedSnapshotId: Int? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        snapshotDao = SnapshotRepo.getDatabase(requireContext()).snapshotDao()
        arguments?.let {
            processInfo = it.getSerializable("process_info") as? ProcessInfo
        }
        // Відновлення стану
        savedInstanceState?.let {
            isDetailViewVisible = it.getBoolean("is_detail_view_visible", false)
            if (it.containsKey("selected_snapshot_id")) {
                selectedSnapshotId = it.getInt("selected_snapshot_id")
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnapshotsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Ініціалізація обробки кнопки "Назад"
        setupBackPressHandling()

        if (isDetailViewVisible && selectedSnapshotId != null) {
            showDetailViewById(selectedSnapshotId!!)
        } else {
            setupListView()
        }

        binding.createSnapshotButton.setOnClickListener { createSnapshot() }
    }

    private fun setupBackPressHandling() {
        // Android 13+ (API 33+): Використовуємо OnBackInvokedCallback
        requireActivity().onBackInvokedDispatcher.registerOnBackInvokedCallback(
            android.window.OnBackInvokedDispatcher.PRIORITY_DEFAULT
        ) {
            if (isDetailViewVisible) {
                showListView()
            } else {
                requireActivity().finish() // Або інша дія за замовчуванням
            }
        }
    }

    // Зберігаємо стан перед знищенням View
    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean("is_detail_view_visible", isDetailViewVisible)
        selectedSnapshotId?.let { outState.putInt("selected_snapshot_id", it) }
    }

    private fun setupListView() {
        isDetailViewVisible = false
        selectedSnapshotId = null
        binding.detailContainer.visibility = View.GONE
        binding.listContainer.visibility = View.VISIBLE

        val packageName = processInfo?.cmd ?: return
        snapshotDao.getSnapshotsForPackage(packageName).observe(viewLifecycleOwner, Observer { snapshots ->
            if (snapshots.isEmpty()) {
                binding.snapshotsList.visibility = View.GONE
                binding.noSnapshotsText.visibility = View.VISIBLE
            } else {
                binding.snapshotsList.visibility = View.VISIBLE
                binding.noSnapshotsText.visibility = View.GONE
                val adapter = SnapshotAdapter(requireContext(), snapshots)
                binding.snapshotsList.adapter = adapter
            }
        })

        binding.snapshotsList.setOnItemClickListener { _, _, position, _ ->
            val snapshot = binding.snapshotsList.adapter.getItem(position) as Snapshot
            showDetailView(snapshot)
        }
    }

    // Всередині класу SnapshotsFragment
    private fun showListView() {
        isDetailViewVisible = false
        selectedSnapshotId = null
        binding.detailContainer.visibility = View.GONE
        binding.listContainer.visibility = View.VISIBLE

        val packageName = processInfo?.cmd ?: return
        snapshotDao.getSnapshotsForPackage(packageName).observe(viewLifecycleOwner, Observer { snapshots ->
            if (snapshots.isEmpty()) {
                binding.snapshotsList.visibility = View.GONE
                binding.noSnapshotsText.visibility = View.VISIBLE
            } else {
                binding.snapshotsList.visibility = View.VISIBLE
                binding.noSnapshotsText.visibility = View.GONE
                // Переконуємось, що адаптер ініціалізовано
                if (binding.snapshotsList.adapter == null) {
                    val adapter = SnapshotAdapter(requireContext(), snapshots)
                    binding.snapshotsList.adapter = adapter
                } else {
                    (binding.snapshotsList.adapter as SnapshotAdapter).clear()
                    (binding.snapshotsList.adapter as SnapshotAdapter).addAll(snapshots)
                    (binding.snapshotsList.adapter as SnapshotAdapter).notifyDataSetChanged()
                }
            }
        })

        binding.snapshotsList.setOnItemClickListener { _, _, position, _ ->
            val snapshot = binding.snapshotsList.adapter.getItem(position) as Snapshot
            showDetailView(snapshot)
        }
    }

    private fun showDetailViewById(snapshotId: Int) {
        lifecycleScope.launch {
            val snapshot = snapshotDao.getSnapshotById(snapshotId)
            snapshot?.let { showDetailView(it) }
        }
    }

    private fun showDetailView(snapshot: Snapshot) {
        isDetailViewVisible = true
        selectedSnapshotId = snapshot.id
        binding.listContainer.visibility = View.GONE
        binding.detailContainer.visibility = View.VISIBLE

        // Заповнюємо таблицю даними зі снепшота
        val table = binding.detailContainer.findViewById<View>(R.id.source_table)
        if (table != null) {
            table.findViewById<TextView>(R.id.time_cpu_fgs).text = snapshot.timeCpuFgs
            table.findViewById<TextView>(R.id.battery_cpu_fgs).text = snapshot.batteryCpuFgs
            table.findViewById<TextView>(R.id.mah_cpu_fgs).text = snapshot.mahCpuFgs

            table.findViewById<TextView>(R.id.time_cpu_fg).text = snapshot.timeCpuFg
            table.findViewById<TextView>(R.id.battery_cpu_fg).text = snapshot.batteryCpuFg
            table.findViewById<TextView>(R.id.mah_cpu_fg).text = snapshot.mahCpuFg

            table.findViewById<TextView>(R.id.time_cpu_bg).text = snapshot.timeCpuBg
            table.findViewById<TextView>(R.id.battery_cpu_bg).text = snapshot.batteryCpuBg
            table.findViewById<TextView>(R.id.mah_cpu_bg).text = snapshot.mahCpuBg

            table.findViewById<TextView>(R.id.time_foreground_services).text = snapshot.timeForegroundServices
            table.findViewById<TextView>(R.id.battery_foreground_services).text = snapshot.batteryForegroundServices
            table.findViewById<TextView>(R.id.mah_foreground_services).text = snapshot.mahForegroundServices

            table.findViewById<TextView>(R.id.time_foreground_activities).text = snapshot.timeForegroundActivities
            table.findViewById<TextView>(R.id.battery_foreground_activities).text = snapshot.batteryForegroundActivities
            table.findViewById<TextView>(R.id.mah_foreground_activities).text = snapshot.mahForegroundActivities

            table.findViewById<TextView>(R.id.time_wakelock).text = snapshot.timeWakelock
            table.findViewById<TextView>(R.id.battery_wakelock).text = snapshot.batteryWakelock
            table.findViewById<TextView>(R.id.mah_wakelock).text = snapshot.mahWakelock

            table.findViewById<TextView>(R.id.time_sensors).text = snapshot.timeSensors
            table.findViewById<TextView>(R.id.battery_sensors).text = snapshot.batterySensors
            table.findViewById<TextView>(R.id.mah_sensors).text = snapshot.mahSensors

            table.findViewById<TextView>(R.id.time_network).text = snapshot.timeNetwork
            table.findViewById<TextView>(R.id.battery_network).text = snapshot.batteryNetwork
            table.findViewById<TextView>(R.id.mah_network).text = snapshot.mahNetwork

            table.findViewById<TextView>(R.id.time_jobscheduler).text = snapshot.timeJobscheduler
            table.findViewById<TextView>(R.id.battery_jobscheduler).text = snapshot.batteryJobscheduler
            table.findViewById<TextView>(R.id.mah_jobscheduler).text = snapshot.mahJobscheduler

            table.findViewById<TextView>(R.id.time_sync).text = snapshot.timeSync
            table.findViewById<TextView>(R.id.battery_sync).text = snapshot.batterySync
            table.findViewById<TextView>(R.id.mah_sync).text = snapshot.mahSync

            table.findViewById<TextView>(R.id.time_alarms).text = snapshot.timeAlarms
            table.findViewById<TextView>(R.id.battery_alarms).text = snapshot.batteryAlarms
            table.findViewById<TextView>(R.id.mah_alarms).text = snapshot.mahAlarms

            table.findViewById<TextView>(R.id.time_top).text = snapshot.timeTop
            table.findViewById<TextView>(R.id.battery_top).text = snapshot.batteryTop
            table.findViewById<TextView>(R.id.mah_top).text = snapshot.mahTop
        }
    }

    private fun createSnapshot() {
        val activity = requireActivity() as? ProcessDetailActivity ?: return
        val batteryData = activity.sourceDataMap
        val packageName = processInfo?.cmd

        if (batteryData == null || packageName == null) {
            Toast.makeText(requireContext(), "Дані про батарею ще не завантажені", Toast.LENGTH_SHORT).show()
            return
        }

        val snapshot = Snapshot(
            packageName = packageName,
            // CPU
            timeCpuFgs = batteryData["CPU Foreground Services"]?.time ?: "N/A",
            batteryCpuFgs = batteryData["CPU Foreground Services"]?.batteryPercentage ?: "N/A",
            mahCpuFgs = batteryData["CPU Foreground Services"]?.batteryMah ?: "N/A",

            timeCpuFg = batteryData["CPU Foreground"]?.time ?: "N/A",
            batteryCpuFg = batteryData["CPU Foreground"]?.batteryPercentage ?: "N/A",
            mahCpuFg = batteryData["CPU Foreground"]?.batteryMah ?: "N/A",

            timeCpuBg = batteryData["CPU Background"]?.time ?: "N/A",
            batteryCpuBg = batteryData["CPU Background"]?.batteryPercentage ?: "N/A",
            mahCpuBg = batteryData["CPU Background"]?.batteryMah ?: "N/A",

            // Foreground
            timeForegroundServices = batteryData["Foreground Services"]?.time ?: "N/A",
            batteryForegroundServices = batteryData["Foreground Services"]?.batteryPercentage ?: "N/A",
            mahForegroundServices = batteryData["Foreground Services"]?.batteryMah ?: "N/A",

            timeForegroundActivities = batteryData["Foreground Activities"]?.time ?: "N/A",
            batteryForegroundActivities = batteryData["Foreground Activities"]?.batteryPercentage ?: "N/A",
            mahForegroundActivities = batteryData["Foreground Activities"]?.batteryMah ?: "N/A",

            // Wakelock
            timeWakelock = batteryData["Wakelock"]?.time ?: "N/A",
            batteryWakelock = batteryData["Wakelock"]?.batteryPercentage ?: "N/A",
            mahWakelock = batteryData["Wakelock"]?.batteryMah ?: "N/A",

            // Sensors
            timeSensors = batteryData["Sensors"]?.time ?: "N/A",
            batterySensors = batteryData["Sensors"]?.batteryPercentage ?: "N/A",
            mahSensors = batteryData["Sensors"]?.batteryMah ?: "N/A",

            // Network
            timeNetwork = batteryData["Network Usage"]?.time ?: "N/A",
            batteryNetwork = batteryData["Network Usage"]?.batteryPercentage ?: "N/A",
            mahNetwork = batteryData["Network Usage"]?.batteryMah ?: "N/A",

            // Jobs
            timeJobscheduler = batteryData["JobScheduler"]?.time ?: "N/A",
            batteryJobscheduler = batteryData["JobScheduler"]?.batteryPercentage ?: "N/A",
            mahJobscheduler = batteryData["JobScheduler"]?.batteryMah ?: "N/A",

            // Sync
            timeSync = batteryData["Sync"]?.time ?: "N/A",
            batterySync = batteryData["Sync"]?.batteryPercentage ?: "N/A",
            mahSync = batteryData["Sync"]?.batteryMah ?: "N/A",

            // Alarms
            timeAlarms = batteryData["Alarms"]?.time ?: "N/A",
            batteryAlarms = batteryData["Alarms"]?.batteryPercentage ?: "N/A",
            mahAlarms = batteryData["Alarms"]?.batteryMah ?: "N/A",

            // Top
            timeTop = batteryData["Top"]?.time ?: "N/A",
            batteryTop = batteryData["Top"]?.batteryPercentage ?: "N/A",
            mahTop = batteryData["Top"]?.batteryMah ?: "N/A"
        )

        lifecycleScope.launch {
            snapshotDao.insertSnapshot(snapshot)
            Toast.makeText(requireContext(), "Знімок створено!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(processInfo: ProcessInfo) =
            SnapshotsFragment().apply {
                arguments = Bundle().apply {
                    putSerializable("process_info", processInfo)
                }
            }
    }
}

// Простий кастомний адаптер для відображення дати
class SnapshotAdapter(context: Context, private val snapshots: List<Snapshot>) :
    ArrayAdapter<Snapshot>(context, 0, snapshots) {

    private val dateFormat = SimpleDateFormat("dd MMMM yyyy, HH:mm:ss", Locale.getDefault())

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(android.R.layout.simple_list_item_1, parent, false)

        val snapshot = snapshots[position]
        val textView = view.findViewById<TextView>(android.R.id.text1)
        textView.text = "Знімок від ${dateFormat.format(Date(snapshot.timestamp))}"

        return view
    }
}

/*
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.resourcemanager.databinding.FragmentSnapshotsBinding

class SnapshotsFragment : Fragment() {

    private var _binding: FragmentSnapshotsBinding? = null
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentSnapshotsBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance() = SnapshotsFragment()
    }
}
 */