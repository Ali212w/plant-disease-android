package com.zm.plantdisease.ui.detect

import android.Manifest
import android.animation.ObjectAnimator
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.snackbar.Snackbar
import com.zm.plantdisease.R
import com.zm.plantdisease.data.firebase.FirebaseManager
import com.zm.plantdisease.data.model.CLASS_EMOJIS
import com.zm.plantdisease.data.model.ClassResult
import com.zm.plantdisease.data.model.ModelInfo
import com.zm.plantdisease.data.model.Resource
import com.zm.plantdisease.databinding.FragmentDetectBinding
import com.zm.plantdisease.viewmodel.DetectViewModel
import java.io.File

class DetectFragment : Fragment() {

    private var _binding: FragmentDetectBinding? = null
    private val binding get() = _binding!!
    private val vm: DetectViewModel by viewModels()

    private var selectedImageUri: Uri? = null
    private var cameraImageUri: Uri?   = null

    // ── Gallery Picker ────────────────────────────────────────────────────────
    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { setImage(it) } }

    // ── Camera ────────────────────────────────────────────────────────────────
    private val cameraLauncher = registerForActivityResult(
        ActivityResultContracts.TakePicture()
    ) { ok -> if (ok) cameraImageUri?.let { setImage(it) } }

    // ── طلب صلاحية الكاميرا ───────────────────────────────────────────────────
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchCamera()
        else Snackbar.make(binding.root,
            "يجب منح صلاحية الكاميرا لالتقاط الصور",
            Snackbar.LENGTH_LONG).show()
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View {
        _binding = FragmentDetectBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        setupListeners()
        observeViewModel()
        vm.loadModels()
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  SETUP
    // ════════════════════════════════════════════════════════════════════════════

    private fun setupListeners() {
        binding.btnGallery.setOnClickListener {
            galleryLauncher.launch("image/*")
        }

        binding.btnCamera.setOnClickListener {
            // تحقق من الصلاحية أولاً
            if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                == PackageManager.PERMISSION_GRANTED
            ) {
                launchCamera()
            } else {
                cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
            }
        }

        binding.btnChangeImage.setOnClickListener { clearImage() }
        binding.btnPredict.setOnClickListener { runPredict() }
        binding.btnNewDiagnosis.setOnClickListener { clearImage() }

        // اضغط على بطاقة النموذج → انتقل لصفحة النماذج
        binding.cardSelectedModel.setOnClickListener {
            requireActivity().findViewById<com.google.android.material.bottomnavigation.BottomNavigationView>(
                R.id.bottomNav)?.selectedItemId = R.id.navModels
        }
    }

    private fun launchCamera() {
        val file = File.createTempFile("img_", ".jpg", requireContext().cacheDir)
        cameraImageUri = FileProvider.getUriForFile(
            requireContext(), "${requireContext().packageName}.provider", file)
        cameraLauncher.launch(cameraImageUri!!)
    }


    private fun observeViewModel() {
        vm.selectedModel.observe(viewLifecycleOwner) { model ->
            model?.let { updateSelectedModelCard(it) }
        }

        vm.prediction.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> showLoading(true)
                is Resource.Success -> { showLoading(false); showResult(resource.data) }
                is Resource.Error   -> {
                    showLoading(false)
                    Snackbar.make(binding.root, resource.message, Snackbar.LENGTH_LONG).show()
                }
            }
        }
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  IMAGE
    // ════════════════════════════════════════════════════════════════════════════

    private fun setImage(uri: Uri) {
        selectedImageUri = uri
        binding.imgPreview.load(uri) {
            crossfade(true)
            transformations(RoundedCornersTransformation(24f))
        }
        binding.cardUpload.visibility  = View.GONE
        binding.cardPreview.visibility = View.VISIBLE
        binding.btnPredict.visibility  = View.VISIBLE
        binding.layoutResult.visibility = View.GONE
    }

    private fun clearImage() {
        selectedImageUri = null
        binding.cardUpload.visibility   = View.VISIBLE
        binding.cardPreview.visibility  = View.GONE
        binding.btnPredict.visibility   = View.GONE
        binding.layoutResult.visibility = View.GONE
        binding.layoutLoading.visibility = View.GONE
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  PREDICT
    // ════════════════════════════════════════════════════════════════════════════

    private fun runPredict() {
        val uri   = selectedImageUri ?: run {
            Snackbar.make(binding.root, "اختر صورة أولاً", Snackbar.LENGTH_SHORT).show(); return
        }
        val model = vm.selectedModel.value ?: run {
            Snackbar.make(binding.root, "اختر نموذجاً أولاً", Snackbar.LENGTH_SHORT).show(); return
        }
        val userId = FirebaseManager.currentUser?.uid ?: ""

        binding.tvLoadingText.text = "جاري التحليل بـ ${model.name.split("—")[0].trim()}…"
        val stream = requireContext().contentResolver.openInputStream(uri) ?: return
        vm.predict(model.name, stream, userId, uri)
    }

    // ════════════════════════════════════════════════════════════════════════════
    //  UI STATE
    // ════════════════════════════════════════════════════════════════════════════

    private fun showLoading(loading: Boolean) {
        binding.layoutLoading.visibility = if (loading) View.VISIBLE else View.GONE
        binding.btnPredict.isEnabled     = !loading
        if (loading) binding.layoutResult.visibility = View.GONE
    }

    private fun updateSelectedModelCard(model: ModelInfo) {
        binding.tvSelectedModelName.text = model.name
        binding.tvSelectedModelDesc.text = model.description
        try {
            val color = Color.parseColor(model.color)
            binding.modelColorDot.background.setTint(color)
        } catch (_: Exception) {}
    }

    private fun showResult(data: com.zm.plantdisease.data.model.PredictionResponse) {
        binding.layoutResult.visibility = View.VISIBLE
        binding.cardPreview.visibility  = View.GONE
        binding.btnPredict.visibility   = View.GONE

        // الفئة
        val emoji = CLASS_EMOJIS.getOrElse(data.predictedClass) { "🌿" }
        binding.tvResultEmoji.text   = emoji
        binding.tvResultNameAr.text  = data.predictedNameAr
        binding.tvResultNameEn.text  = data.predictedNameEn
        binding.tvConfidence.text    = "%.1f%%".format(data.confidence)
        binding.tvModelUsed.text     = "🧠 ${data.modelName}"
        binding.tvTimingTotal.text   = "${data.totalMs?.toInt() ?: "—"}"
        binding.tvTimingPredict.text = "${data.predictMs?.toInt() ?: "—"}"

        // الأشرطة البيانية
        buildBars(data.allClasses)
    }

    private fun buildBars(classes: List<ClassResult>) {
        val container = binding.layoutBars
        container.removeAllViews()
        val inflater = LayoutInflater.from(requireContext())

        classes.forEachIndexed { i, cls ->
            val row = inflater.inflate(R.layout.item_bar, container, false)
            row.findViewById<TextView>(R.id.tvBarName).text  = cls.nameAr
            row.findViewById<TextView>(R.id.tvBarValue).text = "%.1f%%".format(cls.probability)

            val fill = row.findViewById<View>(R.id.viewBarFill)
            val track = row.findViewById<View>(R.id.viewBarTrack)

            // لون الشريط
            val tint = when (i) {
                0    -> ContextCompat.getColor(requireContext(), R.color.green_primary)
                1    -> ContextCompat.getColor(requireContext(), R.color.blue)
                else -> ContextCompat.getColor(requireContext(), R.color.bg_surface3)
            }
            fill.background.setTint(tint)

            container.addView(row)

            // انيميشن الشريط
            row.post {
                val targetW = (track.width * cls.probability / 100f).toInt()
                ObjectAnimator.ofInt(fill, "width", 0, targetW).apply {
                    duration = 900
                    interpolator = DecelerateInterpolator()
                    start()
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
