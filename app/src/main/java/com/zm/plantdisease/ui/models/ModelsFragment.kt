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

    // ── فتح ملف .ptl من ملفات الجوال ──────────────────────────────────────────
    private val filePicker = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri ?: return@registerForActivityResult
        try {
            // استخراج اسم الملف
            val fileName = requireContext().contentResolver
                .query(uri, null, null, null, null)
                ?.use { cursor ->
                    cursor.moveToFirst()
                    val idx = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (idx >= 0) cursor.getString(idx) else "external_model.ptl"
                } ?: "external_model.ptl"

            // نسخ الملف إلى التخزين الداخلي
            val dest = File(requireContext().filesDir, fileName)
            requireContext().contentResolver.openInputStream(uri)?.use { inp ->
                FileOutputStream(dest).use { out -> inp.copyTo(out) }
            }

            // اسم العرض = اسم الملف بدون الامتداد
            val displayName = fileName.removeSuffix(".ptl").replace("_", " ")

            // إضافة النموذج الخارجي للـ ViewModel
            vm.addExternalModel(
                modelPath = dest.absolutePath,
                modelName = displayName,
                description = "نموذج خارجي من ملفي الجوال"
            )
            Snackbar.make(binding.root, "✅ تم تحميل $displayName", Snackbar.LENGTH_SHORT).show()

        } catch (e: Exception) {
            Snackbar.make(binding.root, "❌ خطأ: ${e.message}", Snackbar.LENGTH_LONG).show()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?,
                              s: Bundle?): View {
        _binding = FragmentModelsBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        vm.models.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> binding.progressBar.visibility = View.VISIBLE
                is Resource.Error   -> {
                    binding.progressBar.visibility = View.GONE
                    Snackbar.make(binding.root, resource.message, Snackbar.LENGTH_LONG).show()
                }
                is Resource.Success -> {
                    binding.progressBar.visibility = View.GONE
                    buildModelList(resource.data)
                }
            }
        }

        vm.selectedModel.observe(viewLifecycleOwner) { _ -> refreshSelection() }

        // زر إضافة نموذج خارجي (يحل محل preload)
        binding.btnPreloadMyModels.text = "📂 إضافة نموذج خارجي"
        binding.btnPreloadMyModels.setOnClickListener {
            Snackbar.make(binding.root,
                "اختر ملف .ptl من التنزيلات أو أي مجلد",
                Snackbar.LENGTH_SHORT).show()
            filePicker.launch("*/*")
        }

        if (vm.models.value == null) vm.loadModels()
    }

    private fun buildModelList(models: List<ModelInfo>) {
        val myList   = binding.layoutMyModels
        val baselist = binding.layoutBaselineModels
        myList.removeAllViews()
        baselist.removeAllViews()

        models.forEach { model ->
            val card = buildModelCard(model)
            if (model.type == "mine" || model.type == "external")
                myList.addView(card)
            else
                baselist.addView(card)
        }
    }

    private fun buildModelCard(model: ModelInfo): View {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_model_card, binding.layoutMyModels, false)

        val card  = view.findViewById<MaterialCardView>(R.id.cardModel)
        val dot   = view.findViewById<View>(R.id.modelDot)
        val name  = view.findViewById<TextView>(R.id.tvModelName)
        val desc  = view.findViewById<TextView>(R.id.tvModelDesc)
        val badge = view.findViewById<TextView>(R.id.tvModelBadge)
        val warn  = view.findViewById<TextView>(R.id.tvModelWarn)

        name.text  = model.name
        desc.text  = model.description
        badge.text = when(model.type) {
            "mine"     -> "نموذجك"
            "external" -> "خارجي"
            "baseline" -> "مقارنة"
            else       -> "Ablation"
        }

        val badgeBg = when(model.type) {
            "mine"     -> R.color.green_bg_low
            "external" -> R.color.cyan_bg_low
            "baseline" -> R.color.red_bg_low
            else       -> R.color.cyan_bg_low
        }
        val badgeTxt = when(model.type) {
            "mine"     -> R.color.green_primary
            "external" -> R.color.cyan
            "baseline" -> R.color.red
            else       -> R.color.cyan
        }
        badge.setBackgroundResource(badgeBg)
        badge.setTextColor(requireContext().getColor(badgeTxt))

        try { dot.background.setTint(Color.parseColor(model.color)) } catch(_: Exception) {}

        if (!model.available) {
            warn.visibility = View.VISIBLE
            card.alpha = 0.6f
        }

        val isSelected = vm.selectedModel.value?.id == model.id
        card.strokeColor = if (isSelected)
            requireContext().getColor(R.color.green_primary)
        else requireContext().getColor(R.color.border)
        card.strokeWidth = if (isSelected) 2 else 1

        card.setOnClickListener {
            if (!model.available) {
                Snackbar.make(binding.root, "ملف النموذج غير موجود", Snackbar.LENGTH_SHORT).show()
                return@setOnClickListener
            }
            vm.selectModel(model)
            Snackbar.make(binding.root, "✅ تم اختيار ${model.name}", Snackbar.LENGTH_SHORT).show()
        }

        return view
    }

    private fun refreshSelection() {
        vm.models.value?.let { res ->
            if (res is Resource.Success) buildModelList(res.data)
        }
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
