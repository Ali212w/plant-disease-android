package com.zm.plantdisease.ui.history

import android.os.Bundle
import android.view.*
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import com.zm.plantdisease.data.firebase.FirebaseManager
import com.zm.plantdisease.data.model.Resource
import com.zm.plantdisease.databinding.FragmentHistoryBinding
import com.zm.plantdisease.viewmodel.HistoryViewModel

class HistoryFragment : Fragment() {

    private var _binding: FragmentHistoryBinding? = null
    private val binding get() = _binding!!
    private val vm: HistoryViewModel by viewModels()
    private val adapter = HistoryAdapter { item ->
        // حذف عند الضغط المطوّل — يعمل مع الضيف والمستخدم الحقيقي
        val userId = FirebaseManager.userId
        vm.delete(userId, item.id)
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?,
                              s: Bundle?): View {
        _binding = FragmentHistoryBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.recyclerHistory.adapter = adapter

        val userId = FirebaseManager.userId  // guest or real UID — never null

        vm.history.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Resource.Error   -> binding.progressBar.visibility = View.GONE
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    binding.tvEmpty.visibility = if (resource.data.isEmpty()) View.VISIBLE else View.GONE
                    adapter.submitList(resource.data)
                }
            }
        }

        vm.stats.observe(viewLifecycleOwner) { stats ->
            @Suppress("UNCHECKED_CAST")
            val byModel = stats["byModel"] as? Map<String, Int>
            binding.tvTotalCount.text  = "${stats["total"] ?: 0}"
            binding.tvAvgConf.text     = "%.1f%%".format((stats["avgConfidence"] as? Double) ?: 0.0)
            binding.tvTopModel.text    = byModel?.maxByOrNull { it.value }?.key?.split("—")?.firstOrNull()?.trim() ?: "—"
        }

        vm.loadHistory(userId)
        vm.loadStats(userId)
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
