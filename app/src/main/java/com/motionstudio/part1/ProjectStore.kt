package com.motionstudio.part1

import java.util.concurrent.atomic.AtomicLong

interface ProjectStore {
    fun save(project: Project)
    fun load(projectId: ProjectId): Project?
    fun delete(projectId: ProjectId)
}

class InMemoryProjectStore : ProjectStore {
    private val projects = linkedMapOf<ProjectId, Project>()
    override fun save(project: Project) { projects[project.id] = project }
    override fun load(projectId: ProjectId) = projects[projectId]
    override fun delete(projectId: ProjectId) { projects.remove(projectId) }
}

class AutosaveController(
    private val store: ProjectStore,
    private val intervalMs: Long = 30_000L
) {
    private val lastSave = AtomicLong(0L)

    fun maybeSave(project: Project, nowMs: Long = System.currentTimeMillis()): Boolean {
        val previous = lastSave.get()
        if (nowMs - previous < intervalMs) return false
        if (lastSave.compareAndSet(previous, nowMs)) {
            store.save(project)
            return true
        }
        return false
    }

    fun forceSave(project: Project) {
        store.save(project)
        lastSave.set(System.currentTimeMillis())
    }
}
