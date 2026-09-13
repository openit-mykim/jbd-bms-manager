package com.gytxtx.openjbd

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.fragment.app.Fragment

class ComingSoonFragment : Fragment() {
    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View = inflater.inflate(R.layout.fragment_coming_soon, container, false)

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        val titleRes = requireArguments().getInt(ARG_TITLE_RES)
        view.findViewById<TextView>(R.id.txt_coming_soon_section).setText(titleRes)
        view.findViewById<TextView>(R.id.txt_coming_soon).setText(R.string.development_in_progress)
    }

    companion object {
        private const val ARG_TITLE_RES = "title_res"

        fun newInstance(@StringRes titleRes: Int) = ComingSoonFragment().apply {
            arguments = Bundle().apply { putInt(ARG_TITLE_RES, titleRes) }
        }
    }
}
