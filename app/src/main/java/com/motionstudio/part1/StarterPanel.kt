package com.motionstudio.part1

data class StarterPanelState(
    val showOnFirstLaunch: Boolean = true,
    val completedTutorial: Boolean = false,
    val selectedWorkspace: Workspace = Workspace.EDIT,
    val hasImportedMedia: Boolean = false,
    val recentProjectCount: Int = 0
)

enum class Workspace { EDIT, COMPOSITE, COLOR, AUDIO, EXPORT }

sealed interface StarterAction {
    data object NewProject : StarterAction
    data object OpenProject : StarterAction
    data object ImportMedia : StarterAction
    data object StartTutorial : StarterAction
    data object SkipTutorial : StarterAction
    data class SelectWorkspace(val workspace: Workspace) : StarterAction
}

class StarterPanelController(initial: StarterPanelState = StarterPanelState()) {
    var state: StarterPanelState = initial
        private set

    fun dispatch(action: StarterAction) {
        state = when (action) {
            StarterAction.NewProject, StarterAction.OpenProject,
            StarterAction.StartTutorial ->
                state.copy(showOnFirstLaunch = false)
            StarterAction.ImportMedia ->
                state.copy(showOnFirstLaunch = false, hasImportedMedia = true)
            StarterAction.SkipTutorial ->
                state.copy(showOnFirstLaunch = false, completedTutorial = true)
            is StarterAction.SelectWorkspace ->
                state.copy(selectedWorkspace = action.workspace)
        }
    }

    fun finishTutorial() {
        state = state.copy(completedTutorial = true)
    }
}
