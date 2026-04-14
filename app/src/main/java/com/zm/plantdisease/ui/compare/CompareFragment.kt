package com.zm.plantdisease.ui.compare

import android.graphics.Color
import android.net.Uri
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import coil.load
import coil.transform.RoundedCornersTransformation
import com.google.android.material.snackbar.Snackbar
import com.zm.plantdisease.R
import com.zm.plantdisease.data.model.PredictionResponse
import com.zm.plantdisease.data.model.Resource
import com.zm.plantdisease.databinding.FragmentCompareBinding
import com.zm.plantdisease.viewmodel.CompareViewModel
import java.io.File

class CompareFragment : Fragment() {

    private var _binding: FragmentCompareBinding? = null
    private val binding get() = _binding!!
    private val vm: CompareViewModel by viewModels()

    private var selectedImageUri: Uri? = null

    private val galleryLauncher = registerForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri -> uri?.let { setImage(it) } }

    override fun onCreateView(inflater: LayoutInflater, c: ViewGroup?,
                              s: Bundle?): View {
        _binding = FragmentCompareBinding.inflate(inflater, c, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.btnPickImage.setOnClickListener  { galleryLauncher.launch("image/*") }
        binding.btnChangeImage.setOnClickListener { clearImage() }
        binding.btnCompare.setOnClickListener     { runCompare() }

        vm.compareResult.observe(viewLifecycleOwner) { resource ->
            when (resource) {
                is Resource.Loading -> { binding.progressBar.visibility = View.VISIBLE; binding.layoutResults.visibility = View.GONE }
                is Resource.Error   -> { binding.progressBar.visibility = View.GONE; Snackbar.make(binding.root, resource.message, Snackbar.LENGTH_LONG).show() }
                is Resource.Success -> { binding.progressBar.visibility = View.GONE; showResults(resource.data) }
            }
        }
    }

    private fun setImage(uri: Uri) {
        selectedImageUri = uri
        binding.imgCompare.load(uri) {
            crossfade(true)
            transformations(RoundedCornersTransformation(20f))
        }
        binding.cardPickImage.visibility   = View.GONE
        binding.cardImagePreview.visibility = View.VISIBLE
        binding.btnCompare.visibility      = View.VISIBLE
    }

    private fun clearImage() {
        selectedImageUri = null
        binding.cardPickImage.visibility    = View.VISIBLE
        binding.cardImagePreview.visibility = View.GONE
        binding.btnCompare.visibility       = View.GONE
        binding.layoutResults.visibility    = View.GONE
    }

    private fun runCompare() {
        val uri = selectedImageUri ?: return
        val stream = requireContext().contentResolver.openInputStream(uri) ?: return
        vm.compare(stream, uri)
    }

    private fun showResults(results: Map<String, PredictionResponse>) {
        val container = binding.layoutResultsList
        container.removeAllViews()

        // ترتيب تنازلي حسب الثقة
        val sorted = results.entries.sortedByDescending { it.value.confidence }
        val medals = listOf("🥇", "🥈", "🥉")

        sorted.forEachIndexed { i, (name, data) ->
            val card = buildCompareCard(name, data, medals.getOrElse(i) { "" }, i)
            container.addView(card)
        }

        binding.layoutResults.visibility = View.VISIBLE
    }

    private fun buildCompareCard(
        modelName: String,
        data: PredictionResponse,
        medal: String,
        rank: Int
    ): View {
        val inflater = LayoutInflater.from(requireContext())
        val view = inflater.inflate(R.layout.item_compare_result, binding.layoutResultsList, false)

        view.findViewById<TextView>(R.id.tvMedal).text       = medal
        view.findViewById<TextView>(R.id.tvModelName).text   = modelName
        view.findViewById<TextView>(R.id.tvPredName).text    = data.predictedNameAr
        view.findViewById<TextView>(R.id.tvConfidence).text  = "%.1f%%".format(data.confidence)
        view.findViewById<TextView>(R.id.tvTimingMs).text    = "${data.totalMs?.toInt() ?: "—"} ms"

        val fill  = view.findViewById<View>(R.id.viewConfFill)
        val track = view.findViewById<View>(R.id.viewConfTrack)

        // لون حسب الترتيب
        val color = when (rank) {
            0    -> requireContext().getColor(R.color.green_primary)
            1    -> requireContext().getColor(R.color.blue)
            2    -> requireContext().getColor(R.color.purple)
            else -> requireContext().getColor(R.color.text_muted)
        }
        fill.background.setTint(color)
        view.findViewById<TextView>(R.id.tvConfidence).setTextColor(color)

        // انيميشن بعد رسم العنصر
        view.post {
            val targetW = (track.width * data.confidence / 100f).toInt()
            val anim = android.animation.ObjectAnimator.ofInt(fill, "width", 0, targetW)
            anim.duration = 900
            anim.interpolator = android.view.animation.DecelerateInterpolator()
            anim.start()
        }

        return view
    }

    override fun onDestroyView() { super.onDestroyView(); _binding = null }
}
