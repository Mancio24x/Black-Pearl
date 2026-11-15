package org.qosp.notes.ui.cards

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.drawable.BitmapDrawable
import android.os.Bundle
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.components.cards.CardCodes
import org.qosp.notes.components.cards.LoyaltyCard
import org.qosp.notes.databinding.FragmentCardsBinding
import org.qosp.notes.databinding.LayoutCardItemBinding
import org.qosp.notes.ui.common.BaseFragment

/** Loyalty cards (drawer entry "Cards"): scan a barcode once, show it again at the till. Independent from notes. */
class CardsFragment : BaseFragment(R.layout.fragment_cards) {
    private val model: CardsViewModel by viewModel()
    private val binding get() = FragmentCardsBinding.bind(requireView())
    override val toolbar: Toolbar get() = binding.cardsToolbar
    override val toolbarTitle: String get() = getString(R.string.nav_cards)
    override val hasMenu = false

    private val cameraScanner = registerForActivityResult(ScanContract()) { result ->
        val code = result.contents ?: return@registerForActivityResult
        askName(CardCodes.Scan(code, result.formatName ?: CardCodes.formatForTyped(code)))
    }

    private val picturePicker = registerForActivityResult(ActivityResultContracts.GetContent()) { uri ->
        if (uri != null) model.scanPicture(uri)
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = CardAdapter(::show, ::showMenu)
        binding.cardsList.layoutManager = LinearLayoutManager(requireContext())
        binding.cardsList.adapter = adapter
        binding.cardsAdd.setOnClickListener { chooseHowToAdd() }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.cards.collect {
                        adapter.submitList(it)
                        binding.cardsEmpty.isVisible = it.isEmpty()
                    }
                }
                launch {
                    model.pictureScans.collect { scan ->
                        if (scan != null) askName(scan)
                        else Snackbar.make(binding.root, R.string.cards_not_found, Snackbar.LENGTH_LONG).show()
                    }
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.refresh()
    }

    private fun chooseHowToAdd() {
        val labels = arrayOf(R.string.cards_add_camera, R.string.cards_add_picture, R.string.cards_add_typed)
            .map(::getString).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cards_add)
            .setItems(labels) { _, which ->
                when (which) {
                    0 -> cameraScanner.launch(
                        ScanOptions().setOrientationLocked(false).setBeepEnabled(false)
                            .setPrompt(getString(R.string.cards_scan_prompt)),
                    )
                    1 -> picturePicker.launch("image/*")
                    2 -> askName(null)
                }
            }.show()
    }

    /** Asks the card's name; with no [scan] it also asks the number (typed by hand). */
    private fun askName(scan: CardCodes.Scan?) {
        val name = EditText(requireContext()).apply { setHint(R.string.cards_name); setSingleLine() }
        val number = EditText(requireContext()).apply { setHint(R.string.cards_number); setSingleLine() }
        val fields = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            addView(name)
            if (scan == null) addView(number)
        }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(scan?.code ?: getString(R.string.cards_add_typed))
            .setView(fields, 48, 16, 48, 0)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val typed = number.text.toString().trim()
                val result = scan ?: CardCodes.Scan(typed, CardCodes.formatForTyped(typed))
                if (result.code.isNotEmpty()) model.add(name.text.toString(), result)
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    /** Shows the code large, black on white, with the screen at full brightness so the till can read it. */
    private fun show(card: LoyaltyCard) {
        val square = CardCodes.isTwoDimensional(card.format)
        val matrix = CardCodes.encode(card.code, card.format, if (square) 700 else 1000, if (square) 700 else 340)
        if (matrix == null) {
            Snackbar.make(binding.root, R.string.cards_cannot_show, Snackbar.LENGTH_LONG).show()
            return
        }
        val bitmap = Bitmap.createBitmap(CardCodes.toPixels(matrix), matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        val content = LinearLayout(requireContext()).apply {
            orientation = LinearLayout.VERTICAL
            setBackgroundColor(Color.WHITE)
            setPadding(24, 48, 24, 32)
            addView(
                ImageView(context).apply {
                    // No smoothing: the bars must stay sharp when the picture is stretched.
                    setImageDrawable(BitmapDrawable(resources, bitmap).apply { isFilterBitmap = false })
                    adjustViewBounds = true
                },
                LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
            )
            addView(
                TextView(context).apply {
                    text = card.code
                    gravity = Gravity.CENTER
                    setTextColor(Color.BLACK)
                    textSize = 18f
                    setTextIsSelectable(true)
                    setPadding(0, 24, 0, 0)
                },
            )
        }
        val dialog = MaterialAlertDialogBuilder(requireContext())
            .setTitle(card.name)
            .setView(content)
            .setPositiveButton(android.R.string.ok, null)
            .show()
        dialog.window?.let { it.attributes = it.attributes.apply { screenBrightness = 1f } }
    }

    private fun showMenu(anchor: View, card: LoyaltyCard) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.cards_rename)
            menu.add(0, 2, 1, R.string.cards_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> rename(card)
                    2 -> MaterialAlertDialogBuilder(requireContext())
                        .setMessage(getString(R.string.cards_delete_confirm, card.name))
                        .setPositiveButton(R.string.cards_delete) { _, _ -> model.delete(card) }
                        .setNegativeButton(android.R.string.cancel, null)
                        .show()
                }
                true
            }
        }.show()
    }

    private fun rename(card: LoyaltyCard) {
        val field = EditText(requireContext()).apply { setText(card.name); setSelectAllOnFocus(true); setSingleLine() }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.cards_rename)
            .setView(field, 48, 16, 48, 0)
            .setPositiveButton(android.R.string.ok) { _, _ -> model.rename(card, field.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

private class CardAdapter(
    val onOpen: (LoyaltyCard) -> Unit,
    val onMenu: (View, LoyaltyCard) -> Unit,
) : ListAdapter<LoyaltyCard, CardAdapter.Holder>(
    object : DiffUtil.ItemCallback<LoyaltyCard>() {
        override fun areItemsTheSame(a: LoyaltyCard, b: LoyaltyCard) = a.id == b.id
        override fun areContentsTheSame(a: LoyaltyCard, b: LoyaltyCard) = a == b
    },
) {
    class Holder(val b: LayoutCardItemBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutCardItemBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val card = getItem(position)
        holder.b.cardName.text = card.name
        holder.b.cardCode.text = card.code
        holder.b.root.setOnClickListener { onOpen(card) }
        holder.b.cardMenu.setOnClickListener { onMenu(it, card) }
    }
}
