package org.qosp.notes.data.model

import android.os.Parcelable
import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation
import kotlinx.parcelize.Parcelize
import kotlinx.serialization.Serializable
import java.time.Instant

@Entity(
    tableName = "notes",
    foreignKeys = [
        ForeignKey(
            onDelete = ForeignKey.SET_NULL,
            entity = Notebook::class,
            parentColumns = ["id"],
            childColumns = ["notebookId"]
        ),
    ]
)
@Serializable
data class NoteEntity(
    val title: String,
    val content: String,
    val isList: Boolean,
    val taskList: List<NoteTask>,
    val isArchived: Boolean,
    val isDeleted: Boolean,
    val isPinned: Boolean,
    val isHidden: Boolean,
    val isMarkdownEnabled: Boolean,
    val isLocalOnly: Boolean,
    val isCompactPreview: Boolean,
    val screenAlwaysOn: Boolean,
    val creationDate: Long,
    val modifiedDate: Long,
    val deletionDate: Long?,
    val attachments: List<Attachment>,
    val color: NoteColor,
    @ColumnInfo(index = true)
    val notebookId: Long?,
    @PrimaryKey(autoGenerate = true)
    val id: Long,
)

@Serializable
@Parcelize
data class Note(
    val title: String = "",
    val content: String = "",
    val isList: Boolean = false,
    val taskList: List<NoteTask> = listOf(),
    val isArchived: Boolean = false,
    val isDeleted: Boolean = false,
    val isPinned: Boolean = false,
    val isHidden: Boolean = false,
    val isMarkdownEnabled: Boolean = true,
    val isLocalOnly: Boolean = false,
    val isCompactPreview: Boolean = false,
    val screenAlwaysOn: Boolean = false,
    val creationDate: Long = Instant.now().epochSecond,
    val modifiedDate: Long = Instant.now().epochSecond,
    val deletionDate: Long? = null,
    val attachments: List<Attachment> = listOf(),
    val color: NoteColor = NoteColor.Default,
    val notebookId: Long? = null,
    val id: Long = 0L,
    @Relation(
        entity = Tag::class,
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(
            value = NoteTagJoin::class,
            parentColumn = "noteId",
            entityColumn = "tagId",
        )
    )
    val tags: List<Tag> = listOf(),
    @Relation(
        parentColumn = "id",
        entityColumn = "noteId",
    )
    val reminders: List<Reminder> = listOf(),
) : Parcelable {

    fun isEmpty(): Boolean {
        val baseCondition =
            title.isBlank() && attachments.isEmpty() && reminders.isEmpty() && tags.isEmpty()
        return when {
            isList -> baseCondition && taskList.isEmpty()
            else -> baseCondition && content.isBlank()
        }
    }

    /**
     * "Convert to list": one item per non-empty line. Black Pearl: Markdown and plain-text checkboxes ("- [ ] milk",
     * "[x] bread", "☐ / ☑"), bullets ("- ", "• ") and numbers ("1. ") become real checkboxes instead of staying in the
     * text of the item; "[x]" / "☑" keep the item ticked.
     */
    private companion object {
        // optional bullet or number, optional checkbox, then the text; group 1 = the checkbox
        val TASK_LINE = Regex("""^(?:[-*+•◦▪‣·]\s+|\d{1,3}[.)]\s+)?(\[[ xX✓✔]?]|[☐☑☒□])?\s*(.*)$""")

        // a Markdown task line as written by taskListToMd(): "- [ ] text" / "- [x] text"
        val MD_TASK_LINE = Regex("^\\s*[-+*] *\\[([ xX])](.*)$")
    }

    fun stringToTaskList(): List<NoteTask> {
        var nextId = 0L
        return content.lines().mapNotNull { line ->
            val match = TASK_LINE.matchEntire(line.trim()) ?: return@mapNotNull null
            val (box, text) = match.destructured
            val clean = text.trim()
            if (clean.isEmpty()) return@mapNotNull null
            val done = box == "☑" || box == "☒" || (box.startsWith("[") && box.trim('[', ']', ' ').isNotEmpty())
            NoteTask(nextId++, clean, done)
        }
    }

    /** True when every non-blank line of [content] is a Markdown task, so it can become a checklist losslessly. */
    fun isMdTaskListOnly(content: String): Boolean =
        content.lines().all { it.isBlank() || MD_TASK_LINE.matches(it) }

    /**
     * Black Pearl: applies remote Markdown to a checklist note. It stays a checklist only when every line is a task;
     * any other line (heading, paragraph, ...) turns the note into a text note with the remote content as-is, so no
     * line is dropped or glued onto a task.
     */
    fun withRemoteListContent(content: String): Note =
        if (isMdTaskListOnly(content)) copy(content = "", taskList = mdToTaskList(content), isList = true)
        else copy(content = content, taskList = listOf(), isList = false)

    fun mdToTaskList(content: String): List<NoteTask> {
        val regex = MD_TASK_LINE
        val tasks = mutableListOf<NoteTask>()
        content.lines().forEachIndexed { index, line ->
            val result = regex.find(line)
            val task = result?.let {
                val checked = it.groupValues[1].lowercase() == "x"
                NoteTask(id = index.toLong(), content = it.groupValues[2].trim(), isDone = checked)
            } ?: tasks.removeLastOrNull()?.let { t -> t.copy(content = t.content + line.trim()) }
            task?.let { tasks.add(it) }
        }
        return tasks.toList()
    }

    fun toStorableContent(): String {
        return when {
            isList -> taskListToMd()
            else -> content
        }
    }

    fun taskListToMd(): String {
        return taskList.joinToString("\n") {
            val prefix = if (it.isDone) "- [x]" else "- [ ]"
            "$prefix ${it.content.trim()}"
        }
    }

    fun taskListToString(withCheckmarks: Boolean = false): String {
        return taskList.joinToString("\n") {
            val prefix = when {
                withCheckmarks -> if (it.isDone) "☑ " else "☐ "
                else -> ""
            }
            "$prefix${it.content.trim()}"
        }
    }

    fun toEntity(): NoteEntity = NoteEntity(
        title = title,
        content = content,
        isList = isList,
        taskList = taskList,
        isArchived = isArchived,
        isDeleted = isDeleted,
        isPinned = isPinned,
        isHidden = isHidden,
        isMarkdownEnabled = isMarkdownEnabled,
        isLocalOnly = isLocalOnly,
        isCompactPreview = isCompactPreview,
        screenAlwaysOn = screenAlwaysOn,
        creationDate = creationDate,
        modifiedDate = modifiedDate,
        deletionDate = deletionDate,
        attachments = attachments,
        color = color,
        notebookId = notebookId,
        id = id
    )
}
