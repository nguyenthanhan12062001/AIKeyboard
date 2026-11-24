package com.ai.banphim.ime

/**
 * Cursor-aware text buffer helpers for the inline AI Translation Panel input box.
 * Keeps UTF-16 surrogate pairs (such as emojis) intact during cursor clamping and deletion.
 */
object TranslateTextBuffer {

  data class EditResult(
    val text: String,
    val cursorIndex: Int,
  )

  /** Clamps [index] within [0, text.length] and avoids splitting UTF-16 surrogate pairs. */
  fun clampCursor(text: String, index: Int): Int {
    val clamped = index.coerceIn(0, text.length)
    if (clamped in 1 until text.length &&
      Character.isLowSurrogate(text[clamped]) &&
      Character.isHighSurrogate(text[clamped - 1])
    ) {
      return (clamped + 1).coerceAtMost(text.length)
    }
    return clamped
  }

  /** Inserts [inserted] at the clamped [cursorIndex] and advances the cursor past the inserted text. */
  fun insertAtCursor(text: String, cursorIndex: Int, inserted: String): EditResult {
    val safeCursor = clampCursor(text, cursorIndex)
    if (inserted.isEmpty()) return EditResult(text, safeCursor)
    val newText = text.substring(0, safeCursor) + inserted + text.substring(safeCursor)
    return EditResult(newText, safeCursor + inserted.length)
  }

  /** Deletes either a full code point or the preceding word before [cursorIndex]. */
  fun backspaceAtCursor(text: String, cursorIndex: Int, wordDelete: Boolean): EditResult {
    val safeCursor = clampCursor(text, cursorIndex)
    if (safeCursor == 0 || text.isEmpty()) return EditResult(text, 0)

    val before = text.substring(0, safeCursor)
    val after = text.substring(safeCursor)

    val newBefore =
      if (wordDelete) {
        before.dropLastWhile { it.isWhitespace() }.dropLastWhile { !it.isWhitespace() }
      } else {
        val codePoint = Character.codePointBefore(before, before.length)
        val charCount = Character.charCount(codePoint)
        before.dropLast(charCount)
      }

    return EditResult(newBefore + after, newBefore.length)
  }

  /** Returns the start offset of the contiguous word token immediately before [cursorIndex]. */
  fun findTokenStartBeforeCursor(
    text: String,
    cursorIndex: Int,
    isWordChar: (Char) -> Boolean,
  ): Int {
    var i = clampCursor(text, cursorIndex)
    while (i > 0 && isWordChar(text[i - 1])) {
      i--
    }
    return i
  }
}
