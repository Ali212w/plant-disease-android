package com.zm.plantdisease.ui.models

import android.graphics.Color
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.fragment.app.Fragment
import androidx.fragment.app.activityViewModels
import com.google.android.material.card.MaterialCardView
import com.google.android.material.snackbar.Snackbar
import com.zm.plantdisease.R
import com.zm.plantdisease.data.model.ModelInfo
import com.zm.plantdisease.data.model.Resource
import com.zm.plantdisease.databinding.FragmentModelsBinding
import com.zm.plantdisease.viewmodel.DetectViewModel
import java.io.File
import java.io.FileOutputStream

class ModelsFragment : Fragment() {

    private var _binding: FragmentModelsBinding? = null
    private val binding get() = _binding!!
    private val vm: DetectViewModel by activityViewModels()

    // Maps model name → its card view for lightweight selection highlighting
    private val modelCardMap = mutableMapOf<String, MaterialCardView>()

    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            val fileName = requireContext().contentResolver
                .query(uri, null, null, null, null)
                ?.use { cursor ->
                    cursor.moveToFirst()
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else "external_model.ptl"
                } ?: "external_model.ptl"

            val dest = File(requireContext().filesDir, fileName)
            requireContext().contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(dest).use { out -> inp.copyTo(out) }
            }

            val displayName = fileName.removeSuffix(".ptl").replace("_", " ")
            val error = vm.addExternalModel(
                modelPath = dest.absolutePath,
                modelName = displayName,
                description = "External model imported from local storage"
            )

            if (error != null) {
                Snackbar.make(binding.root, error, Snackbar.LENGTH_LONG).show()
                return@registerForActivityResult
            }

            Snackbar.make(binding.root, "تم تحميل $displayName", Snackbar.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Snackbar.make(binding.root, "خطأ: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        c: ViewGroup?,
        s: Bundle?
    ): View {
        _binding = FragmentModelsBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        var lastModelStates: List<String> = emptyList()
        vm.models.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Resource.Error -> {
                    binding.progressBar.visibility = View.GONE
                    Snackbar.make(binding.root, resource.message, Snackbar.LENGTH_LONG).show()
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    val newStates = resource.data.map { "${it.name}-${it.available}" }
                    if (newStates != lastModelStates) {
                        lastModelStates = newStates
                        buildModelList(resource.data)
                    }
                    refreshSelectionHighlight()
                }
            }
        }

        vm.selectedModel.observe(viewLifecycleOwner) {
            refreshSelectionHighlight()
        }

        vm.downloadProgress.observe(viewLifecycleOwner) { progressMap ->
            progressMap.forEach { (fileName, pct) ->
                binding.layoutMyModels.let { container ->
                    for (i in 0 until container.childCount) {
                        val card = container.getChildAt(i)
                        val tag = card.tag as? String
                        if (tag == fileName) {
                            val btn = card.findViewById<TextView>(R.id.btnDownloadModel)
                            btn?.text = "$pct%"
                            btn?.isEnabled = false
                        }
                    }
                }
                binding.layoutBaselineModels.let { container ->
                    for (i in 0 until container.childCount) {
                        val card = container.getChildAt(i)
                        val tag = card.tag as? String
                        if (tag == fileName) {
                            val btn = card.findViewById<TextView>(R.id.btnDownloadModel)
                            btn?.text = "$pct%"
                            btn?.isEnabled = false
                        }
                    }
                }
            }
        }

        binding.btnPreloadMyModels.text = "📂 إضافة نموذج خارجي"
        binding.btnPreloadMyModels.setOnClickListener {
            Snackbar.make(
                binding.root,
                "اختر ملف .ptl من التنزيلات أو أي مجلد",
                Snackbar.LENGTH_SHORT
            ).show()
            filePicker.launch("*/*")
        }

        vm.loadModels()
    }

    override fun onResume() {
        super.onResume()
        vm.loadModels()
    }

    private fun buildModelList(models: List<ModelInfo>) {
        val myList = binding.layoutMyModels
        val baselist = binding.layoutBaselineModels
        myList.removeAllViews()
        baselist.removeAllViews()
        modelCardMap.clear()

        models.forEach { model ->
            val card = buildModelCard(model)
            if (model.type == "mine" || model.type == "external") {
                myList.addView(card)
            } else {
                baselist.addView(card)
            }
        }
    }

    private fun buildModelCard(model: ModelInfo): View {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_model_card, binding.layoutMyModels, false)

        val card = view.findViewById<MaterialCardView>(R.id.cardModel)
        val dot = view.findViewById<View>(R.id.modelDot)
        val name = view.findViewById<TextView>(R.id.tvModelName)
        val desc = view.findViewById<TextView>(R.id.tvModelDesc)
        val badge = view.findViewById<TextView>(R.id.tvModelBadge)
        val warn = view.findViewById<TextView>(R.id.tvModelWarn)
        val tvSize = view.findViewById<TextView>(R.id.tvModelSize)
        val btnDelete = view.findViewById<TextView>(R.id.btnDeleteModel)
        val btnDownload = view.findViewById<TextView>(R.id.btnDownloadModel)

        name.text = model.name
        desc.text = model.description
        badge.text = when (model.type) {
            "mine" -> "نموذجك"
            "external" -> "خارجي"
            "baseline" -> "مقارنة"
            else -> "Ablation"
        }

        val badgeBg = when (model.type) {
            "mine" -> R.color.green_bg_low
            "external" -> R.color.cyan_bg_low
            "baseline" -> R.color.red_bg_low
            else -> R.color.cyan_bg_low
        }
        val badgeTxt = when (model.type) {
            "mine" -> R.color.green_primary
            "external" -> R.color.cyan
            "baseline" -> R.color.red
            else -> R.color.cyan
        }
        badge.setBackgroundResource(badgeBg)
        badge.setTextColor(requireContext().getColor(badgeTxt))

        try {
            dot.background.setTint(Color.parseColor(model.color))
        } catch (_: Exception) {
        }

        tvSize.visibility = View.VISIBLE
        if (model.available) {
            warn.visibility = View.GONE
            btnDownload.visibility = View.GONE
            btnDelete.visibility = if (model.isProtected) View.GONE else View.VISIBLE

            var sizeText = "🎯 ${model.accuracy}"
            if (model.fileName.isNotEmpty()) {
                val file = File(requireContext().filesDir, model.fileName)
                if (file.exists()) {
                    val sizeMb = file.length() / (1024.0 * 1024.0)
                    sizeText += " | 💾 %.1f MB".format(sizeMb)
                }
            }
            tvSize.text = sizeText
            card.alpha = 1.0f
        } else {
            warn.visibility = View.VISIBLE
            warn.text = "⚠️ لم يتم التحميل"
            card.alpha = 0.6f
            btnDelete.visibility = View.GONE
            tvSize.text = "🎯 ${model.accuracy}"
            if (model.downloadUrl.isNotEmpty()) {
                btnDownload.visibility = View.VISIBLE
            }
        }

        btnDelete.setOnClickListener {
            if (model.isProtected) return@setOnClickListener

            android.app.AlertDialog.Builder(requireContext())
                .setTitle("حذف النموذج")
                .setMessage("هل تريد حذف \"${model.name}\"؟")
                .setPositiveButton("🗑️ حذف") { _, _ ->
                    if (model.type == "external") {
                        vm.removeExternalModel(model)
                    } else {
                        com.zm.plantdisease.data.model.ModelDownloadManager.deleteModel(
                            requireContext(),
                            model.fileName
                        )
                        vm.loadModels()
                    }
                    Snackbar.make(binding.root, "تم الحذف", Snackbar.LENGTH_SHORT).show()
                }
                .setNegativeButton("إلغاء", null)
                .show()
        }

        view.tag = model.fileName

        btnDownload.setOnClickListener {
            if (model.downloadUrl.isEmpty() || model.fileName.isEmpty()) return@setOnClickListener
            btnDownload.text = "⏳"
            btnDownload.isEnabled = false
            _binding?.let {
                Snackbar.make(it.root, "جارٍ تحميل ${model.name}...", Snackbar.LENGTH_SHORT).show()
            }
            vm.downloadModelFile(
                context = requireContext().applicationContext,
                url = model.downloadUrl,
                fileName = model.fileName
            )
        }

        modelCardMap[model.name] = card

        val isSelected = vm.selectedModel.value?.name == model.name
        card.strokeColor = if (isSelected) {
            requireContext().getColor(R.color.green_primary)
        } else {
            requireContext().getColor(R.color.border)
        }
        card.strokeWidth = if (isSelected) 2 else 1

        card.setOnClickListener {
            if (!model.available) {
                Snackbar.make(binding.root, "يرجى تحميل النموذج أولاً", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.selectModel(model)
            Snackbar.make(binding.root, "تم اختيار ${model.name}", Snackbar.LENGTH_SHORT).show()
        }

        return view
    }

    private fun refreshSelectionHighlight() {
        if (!isAdded) return
        val selectedName = vm.selectedModel.value?.name
        modelCardMap.forEach { (name, card) ->
            val isSelected = name == selectedName
            card.strokeColor = if (isSelected) {
                requireContext().getColor(R.color.green_primary)
            } else {
                requireContext().getColor(R.color.border)
            }
            card.strokeWidth = if (isSelected) 2 else 1
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
