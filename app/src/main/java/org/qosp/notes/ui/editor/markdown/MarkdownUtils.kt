package org.qosp.notes.ui.editor.markdown

import org.qosp.notes.ui.utils.views.ExtendedEditText

enum class MarkdownSpan(val value: String) {
    BOLD("**"),
    ITALICS("_"),
    STRIKETHROUGH("~~"),
    CODE("`"),
    QUOTE(">"),
    HEADING("#"),
    HIGHLIGHT("=="),
}

fun ExtendedEditText.insertMarkdown(markdownSpan: MarkdownSpan) {
    val start = selectionStart
    val end = selectionEnd

    if (start < 0) return

    var line = text?.lines()?.get(currentLineIndex) ?: return
    val lineStart = currentLineStartPos
    val oldLength = line.length
    val s = markdownSpan.value

    when (markdownSpan) {
        MarkdownSpan.HEADING -> {
            line = when {
                line.matches(Regex("""($s)\1{5,} [^$s]*""")) -> {
                    line.replaceFirst(Regex("""($s)\1{5,} """), "")
                }
                line.matches(Regex("$s+ [^$s]*")) -> "$s$line"
                else -> "$s $line"
            }

            text?.replace(lineStart, lineStart + oldLength, line)
            setSelection(lineStart + line.length)
        }
        MarkdownSpan.QUOTE -> {
            line = when {
                line.matches(Regex("$s .*")) -> "$s$line"
                else -> "$s $line"
            }

            text?.replace(lineStart, lineStart + oldLength, line)
            setSelection(lineStart + line.length)
        }
        else -> {
            text?.insert(start, s)
            text?.insert(end + s.length, s)
            setSelection(start + s.length)
        }
    }
}

fun ExtendedEditText.toggleCheckmarkCurrentLine() {
    var line = text?.lines()?.get(currentLineIndex) ?: return
    val lineStart = currentLineStartPos
    val oldLength = line.length

    line = when {
        line.matches(Regex("[-+*] *\\[ ] +.*")) -> {
            line.replaceFirst("[ ]", "[x]").trimEnd() + " " // There's a strange bug which causes
            // text to be duplicated after pressing Enter
            // .trimEnd() + " " seems to be fixing it
        }

        line.matches(Regex("[-+*] *\\[[xX]] +.*")) -> {
            line.replaceFirst("[x]", "[ ]").trimEnd() + " "
        }

        else -> "- [ ] $line"
    }

    text?.replace(lineStart, lineStart + oldLength, line)
    setSelection(lineStart + line.length)
}

/** Black Pearl design 2 "list" button: adds or removes a "- " bullet at the start of the line. */
fun toggleBullet(line: String): String {
    val bullet = Regex("^(\\s*)[-+*] (?!\\[[ xX]] )")
    return if (bullet.containsMatchIn(line)) line.replaceFirst(bullet, "$1") else "- $line"
}

fun ExtendedEditText.toggleBulletCurrentLine() {
    val line = text?.lines()?.get(currentLineIndex) ?: return
    val lineStart = currentLineStartPos
    val newLine = toggleBullet(line)
    text?.replace(lineStart, lineStart + line.length, newLine)
    setSelection(lineStart + newLine.length)
}

fun hyperlinkMarkdown(url: String, content: String): String {
    return "[$content]($url)"
}

fun imageMarkdown(url: String, description: String): String {
    return "![alt text]($url \"$description\")"
}

