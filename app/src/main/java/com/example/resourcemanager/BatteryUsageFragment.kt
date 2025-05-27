package com.example.resourcemanager

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import com.example.resourcemanager.ProcessDetailActivity.SerializableMap
import com.example.resourcemanager.databinding.FragmentBatteryUsageBinding

class BatteryUsageFragment : Fragment() {

    private var _binding: FragmentBatteryUsageBinding? = null
    private val binding get() = _binding!!

    private var sourceData: Map<String, ProcessDetailActivity.SourceData>? = null
    private var batteryAvailable: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        arguments?.let {
            val serializableMap = it.getSerializable("source_data") as? SerializableMap
            sourceData = serializableMap?.map
            batteryAvailable = it.getBoolean("battery_available", false)
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentBatteryUsageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        populateTable()
    }

    private fun populateTable() {
        if (batteryAvailable && sourceData != null) {
            binding.tableLabel.text = "Battery and Time Usage by Source:"
            binding.sourceTable.visibility = View.VISIBLE

            binding.timeCpuFgs.text = sourceData?.get("CPU Foreground Services")?.time ?: "N/A"
            binding.batteryCpuFgs.text = sourceData?.get("CPU Foreground Services")?.batteryPercentage ?: "N/A"
            binding.mahCpuFgs.text = sourceData?.get("CPU Foreground Services")?.batteryMah ?: "N/A"

            binding.timeCpuFg.text = sourceData?.get("CPU Foreground")?.time ?: "N/A"
            binding.batteryCpuFg.text = sourceData?.get("CPU Foreground")?.batteryPercentage ?: "N/A"
            binding.mahCpuFg.text = sourceData?.get("CPU Foreground")?.batteryMah ?: "N/A"

            binding.timeCpuBg.text = sourceData?.get("CPU Background")?.time ?: "N/A"
            binding.batteryCpuBg.text = sourceData?.get("CPU Background")?.batteryPercentage ?: "N/A"
            binding.mahCpuBg.text = sourceData?.get("CPU Background")?.batteryMah ?: "N/A"

            binding.timeForegroundServices.text = sourceData?.get("Foreground Services")?.time ?: "N/A"
            binding.batteryForegroundServices.text = sourceData?.get("Foreground Services")?.batteryPercentage ?: "N/A"
            binding.mahForegroundServices.text = sourceData?.get("Foreground Services")?.batteryMah ?: "N/A"

            binding.timeForegroundActivities.text = sourceData?.get("Foreground Activities")?.time ?: "N/A"
            binding.batteryForegroundActivities.text = sourceData?.get("Foreground Activities")?.batteryPercentage ?: "N/A"
            binding.mahForegroundActivities.text = sourceData?.get("Foreground Activities")?.batteryMah ?: "N/A"

            binding.timeWakelock.text = sourceData?.get("Wakelock")?.time ?: "N/A"
            binding.batteryWakelock.text = sourceData?.get("Wakelock")?.batteryPercentage ?: "N/A"
            binding.mahWakelock.text = sourceData?.get("Wakelock")?.batteryMah ?: "N/A"

            binding.timeSensors.text = sourceData?.get("Sensors")?.time ?: "N/A"
            binding.batterySensors.text = sourceData?.get("Sensors")?.batteryPercentage ?: "N/A"
            binding.mahSensors.text = sourceData?.get("Sensors")?.batteryMah ?: "N/A"

            binding.timeNetwork.text = sourceData?.get("Network Usage")?.time ?: "N/A"
            binding.batteryNetwork.text = sourceData?.get("Network Usage")?.batteryPercentage ?: "N/A"
            binding.mahNetwork.text = sourceData?.get("Network Usage")?.batteryMah ?: "N/A"

            binding.timeJobscheduler.text = sourceData?.get("JobScheduler")?.time ?: "N/A"
            binding.batteryJobscheduler.text = sourceData?.get("JobScheduler")?.batteryPercentage ?: "N/A"
            binding.mahJobscheduler.text = sourceData?.get("JobScheduler")?.batteryMah ?: "N/A"

            binding.timeSync.text = sourceData?.get("Sync")?.time ?: "N/A"
            binding.batterySync.text = sourceData?.get("Sync")?.batteryPercentage ?: "N/A"
            binding.mahSync.text = sourceData?.get("Sync")?.batteryMah ?: "N/A"

            binding.timeAlarms.text = sourceData?.get("Alarms")?.time ?: "N/A"
            binding.batteryAlarms.text = sourceData?.get("Alarms")?.batteryPercentage ?: "N/A"
            binding.mahAlarms.text = sourceData?.get("Alarms")?.batteryMah ?: "N/A"

            binding.timeTop.text = sourceData?.get("Top")?.time ?: "N/A"
            binding.batteryTop.text = sourceData?.get("Top")?.batteryPercentage ?: "N/A"
            binding.mahTop.text = sourceData?.get("Top")?.batteryMah ?: "N/A"

        } else {
            binding.tableLabel.text = "Uh oh :(\nSeems like this is a system process\nNo battery stats for this one 😥"
            binding.sourceTable.visibility = View.GONE
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    companion object {
        @JvmStatic
        fun newInstance(sourceDataMap: Map<String, ProcessDetailActivity.SourceData>?, batteryAvailable: Boolean) =
            BatteryUsageFragment().apply {
                arguments = Bundle().apply {
                    sourceDataMap?.let { putSerializable("source_data", SerializableMap(it)) }
                    putBoolean("battery_available", batteryAvailable)
                }
            }
    }
}