package org.qosp.notes.ui.links

import android.os.Bundle
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.Toolbar
import androidx.core.os.bundleOf
import androidx.core.view.isVisible
import androidx.core.widget.doAfterTextChanged
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.koin.android.ext.android.inject
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.qosp.notes.R
import org.qosp.notes.components.links.GraphNode
import org.qosp.notes.components.links.LinkIndex
import org.qosp.notes.components.links.WikiLinks
import org.qosp.notes.data.model.Note
import org.qosp.notes.data.repo.NoteRepository
import org.qosp.notes.databinding.FragmentNetworkBinding
import org.qosp.notes.preferences.NewNotesSyncable
import org.qosp.notes.preferences.PreferenceRepository
import org.qosp.notes.ui.common.BaseFragment

class NetworkFragment : BaseFragment(R.layout.fragment_network) {
    private val model: NetworkViewModel by viewModel()
    private val repo: NoteRepository by inject()
    private val preferences: PreferenceRepository by inject()
    private var dialog: android.app.Dialog? = null
    private val binding get() = FragmentNetworkBinding.bind(requireView())
    override val toolbar: Toolbar get() = binding.networkToolbar
    override val toolbarTitle: String get() = getString(R.string.nav_network)
    override val hasMenu = false

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        val ui = binding
        ui.networkUnlinkedChip.isChecked = model.showUnlinked.value
        ui.networkUnlinkedChip.setOnCheckedChangeListener { _, checked -> model.showUnlinked.value = checked }
        ui.networkFocusChip.setOnClickListener { model.focus.value = null }
        ui.networkRecenterChip.setOnClickListener { ui.graph.recenter() }
        ui.networkSearchField.doAfterTextChanged { ui.graph.query = it.toString() }
        ui.networkSearchField.setOnEditorActionListener { _, action, _ ->
            if (action != EditorInfo.IME_ACTION_SEARCH) return@setOnEditorActionListener false
            val matches = model.graph.value.nodes.filter { it.title.contains(ui.graph.query, true) }
            val builder = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.network_results)
            if (matches.isEmpty()) builder.setMessage(R.string.network_no_result)
            else builder.setItems(matches.map { it.title }.toTypedArray()) { _, i -> showNode(matches[i]) }
            dialog = builder.setNegativeButton(R.string.action_cancel, null).show()
            true
        }
        ui.graph.onNodeTap = ::showNode
        ui.networkAdd.setOnClickListener { chooseSource() }
        ui.networkEmptyView.setOnClickListener { model.index.value.resolve("The Bible")?.let { open(it.id) } }
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                model.graph.collect { graph ->
                    ui.graph.setGraph(graph.nodes, graph.edges, graph.positions)
                    ui.networkFocusChip.isVisible = model.focus.value != null
                    ui.networkEmptyView.isVisible = graph.nodes.isEmpty()
                    ui.networkAdd.isEnabled = model.index.value.titles.isNotEmpty()
                }
            }
        }
    }

    private fun showNode(node: GraphNode) {
        val index = model.index.value
        val note = index.resolve(node.title)
        val ctx = requireContext()
        val sheet = BottomSheetDialog(ctx)
        val pad = (20 * resources.displayMetrics.density).toInt()
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; setPadding(pad, pad, pad, pad) }
        fun label(value: String, size: Float) {
            body.addView(TextView(ctx).apply { text = value; textSize = size; setPadding(0, 0, 0, pad / 3) })
        }
        fun action(title: Int, click: () -> Unit) {
            body.addView(MaterialButton(ctx).apply {
                setText(title)
                setOnClickListener { sheet.dismiss(); click() }
            })
        }
        label(node.title, 24f)
        if (note == null) {
            val citers = index.ghostCitedBy(node.title)
            label(getString(R.string.network_ghost_message, citers.joinToString { it.title }), 16f)
            action(R.string.network_create) { createOrOpen(node.title, citers.firstOrNull()?.notebookId) }
        } else {
            label(getString(R.string.network_counts, index.outgoing(note.id).size,
                index.backlinks(note.id).distinctBy { it.from.id }.size), 14f)
            if (note.content.isNotBlank()) label(note.content.take(160), 16f)
            action(R.string.network_open) { open(note.id) }
            action(R.string.network_new_linked) { nameLinkedNote(note) }
            action(R.string.network_only_its_links) { model.focus.value = node.key }
        }
        action(R.string.action_cancel) {}
        sheet.setContentView(body)
        sheet.behavior.isDraggable = false
        sheet.behavior.state = com.google.android.material.bottomsheet.BottomSheetBehavior.STATE_EXPANDED
        dialog = sheet
        sheet.show()
    }

    private fun chooseSource() {
        val notes = model.index.value.titles.filter { canLink(it.title) }
        val ctx = requireContext()
        val input = EditText(ctx).apply { setSingleLine(); setHint(R.string.network_search) }
        val list = android.widget.ListView(ctx)
        val body = LinearLayout(ctx).apply { orientation = LinearLayout.VERTICAL; addView(input); addView(list) }
        val picker = MaterialAlertDialogBuilder(ctx).setTitle(R.string.network_source).setView(body)
            .setNegativeButton(R.string.action_cancel, null).create()
        fun filter(query: String) {
            val matches = notes.filter { it.title.contains(query, true) }.distinctBy { WikiLinks.key(it.title) }
            list.adapter = android.widget.ArrayAdapter(ctx, android.R.layout.simple_list_item_1, matches.map { it.title })
            list.setOnItemClickListener { _, _, i, _ -> picker.dismiss(); nameLinkedNote(matches[i]) }
        }
        filter("")
        input.doAfterTextChanged { filter(it.toString()) }
        dialog = picker
        picker.show()
    }

    private fun canLink(title: String): Boolean =
        title.isNotBlank() && WikiLinks.find("[[$title]]").singleOrNull()?.target == title.trim()

    private fun nameLinkedNote(source: Note) {
        val input = EditText(requireContext()).apply { setSingleLine(); setHint(R.string.network_title) }
        val prompt = MaterialAlertDialogBuilder(requireContext()).setTitle(R.string.network_new_linked).setView(input)
            .setNegativeButton(R.string.action_cancel, null).setPositiveButton(R.string.network_create, null).show()
        dialog = prompt
        val button = prompt.getButton(AlertDialog.BUTTON_POSITIVE)
        button.isEnabled = false
        input.doAfterTextChanged { button.isEnabled = canLink(it.toString()) && canLink(source.title) }
        button.setOnClickListener {
            val title = input.text.toString().trim()
            button.isEnabled = false
            viewLifecycleOwner.lifecycleScope.launch {
                val current = freshIndex()
                val parent = current.titles.firstOrNull { it.id == source.id }
                if (!prompt.isShowing) return@launch
                if (parent == null || !canLink(parent.title)) { prompt.dismiss(); return@launch }
                if (current.resolve(title) != null) {
                    input.error = getString(R.string.network_exists)
                    button.isEnabled = true
                    return@launch
                }
                prompt.dismiss()
                findNavController().navigate(R.id.fragment_editor, bundleOf("transitionName" to "editor_create",
                    "newNoteTitle" to title, "newNoteContent" to "[[${parent.title}]]",
                    "newNoteNotebookId" to (parent.notebookId ?: 0L)))
            }
        }
    }

    private suspend fun freshIndex() = withContext(Dispatchers.Default) { LinkIndex(repo.getNonDeleted().first()) }

    private fun createOrOpen(title: String, notebookId: Long?) {
        viewLifecycleOwner.lifecycleScope.launch {
            val localOnly = preferences.get<NewNotesSyncable>().first() == NewNotesSyncable.NO
            open(LinkActions.createOrOpen(title, notebookId, localOnly, repo, freshIndex()))
        }
    }

    private fun open(id: Long) {
        if (findNavController().currentDestination?.id == R.id.fragment_network)
            findNavController().navigate(R.id.fragment_editor, bundleOf("transitionName" to "editor_$id", "noteId" to id))
    }

    override fun onDestroyView() {
        dialog?.dismiss()
        dialog = null
        super.onDestroyView()
    }
}
