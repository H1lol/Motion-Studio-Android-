package com.motionstudio.part1

interface EditCommand { fun execute(); fun undo() }

class UndoRedoManager(private val maxHistory: Int = 200) {
    private val undoStack = ArrayDeque<EditCommand>()
    private val redoStack = ArrayDeque<EditCommand>()

    fun execute(command: EditCommand) {
        command.execute()
        undoStack.addLast(command)
        if (undoStack.size > maxHistory) undoStack.removeFirst()
        redoStack.clear()
    }
    fun undo() {
        val c = undoStack.removeLastOrNull() ?: return
        c.undo(); redoStack.addLast(c)
    }
    fun redo() {
        val c = redoStack.removeLastOrNull() ?: return
        c.execute(); undoStack.addLast(c)
    }
    fun clear() { undoStack.clear(); redoStack.clear() }
}
