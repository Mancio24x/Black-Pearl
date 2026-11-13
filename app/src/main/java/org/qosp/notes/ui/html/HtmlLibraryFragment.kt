package org.qosp.notes.ui.html

import android.os.Bundle
import android.text.format.DateFormat
import android.view.LayoutInflater
import android.view.Menu
import android.view.MenuInflater
import android.view.MenuItem
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.PopupMenu
import android.widget.TextView
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.launch
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.components.html.HtmlDocument
import org.qosp.notes.components.html.HtmlRules
import org.qosp.notes.databinding.FragmentHtmlLibraryBinding
import org.qosp.notes.databinding.LayoutHtmlDocBinding
import org.qosp.notes.ui.common.BaseFragment

/** Library of imported HTML documents (drawer entry "HTML"). Independent from notes and the Network. */
class HtmlLibraryFragment : BaseFragment(R.layout.fragment_html_library) {
    private val model: HtmlLibraryViewModel by viewModel()
    private val binding get() = FragmentHtmlLibraryBinding.bind(requireView())
    override val toolbar: Toolbar get() = binding.htmlToolbar
    override val toolbarTitle: String get() = getString(R.string.nav_html)
    override val hasMenu = true
    private val SORT_ITEM = 0x4854

    private val picker = registerForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) model.import(uri)
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {
        menu.clear()
        menu.add(Menu.NONE, SORT_ITEM, Menu.NONE, R.string.html_sort).apply {
            setIcon(R.drawable.ic_sort)
            setShowAsAction(MenuItem.SHOW_AS_ACTION_ALWAYS)
        }
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId != SORT_ITEM) return super.onOptionsItemSelected(item)
        val sorts = HtmlRules.Sort.values()
        val labels = arrayOf(R.string.html_sort_name, R.string.html_sort_imported, R.string.html_sort_opened)
            .map(::getString).toTypedArray()
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.html_sort)
            .setSingleChoiceItems(labels, sorts.indexOf(model.sort.value)) { d, i ->
                model.sort.value = sorts[i]
                d.dismiss()
            }.show()
        return true
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val adapter = DocAdapter(::open, ::showMenu)
        binding.htmlList.layoutManager = LinearLayoutManager(requireContext())
        binding.htmlList.adapter = adapter
        binding.htmlSearch.setText(model.query.value)
        binding.htmlSearch.doAfterTextChanged { model.query.value = it?.toString().orEmpty() }
        binding.htmlImport.setOnClickListener {
            picker.launch(arrayOf("text/html", "application/zip", "application/x-zip-compressed", "application/octet-stream"))
        }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                launch {
                    model.docs.collect {
                        adapter.submitList(it)
                        binding.htmlEmpty.isVisible = it.isEmpty()
                    }
                }
                launch { model.messages.collect { Snackbar.make(binding.root, it, Snackbar.LENGTH_SHORT).show() } }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        model.refresh()
    }

    private fun open(doc: HtmlDocument) {
        findNavController().navigate(R.id.fragment_html_viewer, bundleOf("docId" to doc.id))
    }

    private fun showMenu(anchor: View, doc: HtmlDocument) {
        PopupMenu(requireContext(), anchor).apply {
            menu.add(0, 1, 0, R.string.html_rename)
            menu.add(0, 2, 1, R.string.html_reset)
            menu.add(0, 3, 2, R.string.html_delete)
            setOnMenuItemClickListener {
                when (it.itemId) {
                    1 -> rename(doc)
                    2 -> confirm(getString(R.string.html_reset_confirm, doc.title), R.string.html_reset) { model.resetProgress(doc) }
                    3 -> confirm(getString(R.string.html_delete_confirm, doc.title), R.string.html_delete) { model.delete(doc) }
                }
                true
            }
        }.show()
    }

    private fun rename(doc: HtmlDocument) {
        val field = EditText(requireContext()).apply { setText(doc.title); setSelectAllOnFocus(true); setSingleLine() }
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.html_rename)
            .setView(field, 48, 16, 48, 0)
            .setPositiveButton(android.R.string.ok) { _, _ -> model.rename(doc, field.text.toString()) }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun confirm(message: String, positive: Int, action: () -> Unit) {
        MaterialAlertDialogBuilder(requireContext())
            .setMessage(message)
            .setPositiveButton(positive) { _, _ -> action() }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }
}

private class DocAdapter(
    val onOpen: (HtmlDocument) -> Unit,
    val onMenu: (View, HtmlDocument) -> Unit,
) : ListAdapter<HtmlDocument, DocAdapter.Holder>(
    object : DiffUtil.ItemCallback<HtmlDocument>() {
        override fun areItemsTheSame(a: HtmlDocument, b: HtmlDocument) = a.id == b.id
        override fun areContentsTheSame(a: HtmlDocument, b: HtmlDocument) = a == b
    },
) {
    class Holder(val b: LayoutHtmlDocBinding) : RecyclerView.ViewHolder(b.root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        Holder(LayoutHtmlDocBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun onBindViewHolder(holder: Holder, position: Int) {
        val doc = getItem(position)
        val ctx = holder.b.root.context
        val fmt = DateFormat.getMediumDateFormat(ctx)
        val time = DateFormat.getTimeFormat(ctx)
        fun at(ms: Long) = java.util.Date(ms).let { "${fmt.format(it)} ${time.format(it)}" }
        holder.b.docTitle.text = doc.title
        val opened = if (doc.lastOpenedAt > 0) ctx.getString(R.string.html_opened_on, at(doc.lastOpenedAt))
        else ctx.getString(R.string.html_never_opened)
        val saved = if (doc.progressUpdatedAt > 0) " · " + ctx.getString(R.string.html_progress_saved) else ""
        holder.b.docSubtitle.text = ctx.getString(R.string.html_imported_on, at(doc.importedAt)) + "\n" + opened + saved
        holder.b.root.setOnClickListener { onOpen(doc) }
        holder.b.docMenu.setOnClickListener { onMenu(it, doc) }
    }
}
