package com.motionstudio.app

import android.content.Context
import android.net.Uri
import com.motionstudio.part1.*
import com.motionstudio.part2.*
import com.motionstudio.part3.EditorEngine
import com.motionstudio.part4.MediaEngine
import java.io.File
import java.util.concurrent.Executors

/** Real application/controller boundary between Compose UI and Parts 1–6. */
class MotionStudioConnector(private val context: Context) {
    private val executor = Executors.newSingleThreadExecutor()
    private val store = InMemoryProjectStore()
    private val bus = MotionStudioEventBus()
    private val autosave = AutosaveController(store)
    private var project: Project? = null
    private var timeline: TimelineEngine? = null
    private var editor: EditorEngine? = null
    private var selected = emptySet<String>()
    private var playing = false
    private var timeUs = 0L

    fun subscribe(listener: (MotionStudioApplicationState) -> Unit) = bus.subscribe(listener)
    fun state(): MotionStudioApplicationState = MotionStudioApplicationState(project, project?.compositions?.firstOrNull()?.id, timeUs, selected, playing)

    fun createProject(name: String = "Untitled Project", width: Int = 1920, height: Int = 1080, fps: Double = 30.0, durationUs: Long = 10_000_000L): Project {
        val p = Project(name = name, compositions = mutableListOf(Composition(name="Main Composition",width=width,height=height,frameRate=fps,durationUs=durationUs)))
        project = p; timeline = TimelineEngine(p.compositions.first()); selected = emptySet(); timeUs = 0; playing = false
        publish(); return p
    }

    fun openProject(p: Project) { project=p; timeline=TimelineEngine(p.compositions.firstOrNull() ?: Composition()); timeUs=0; selected=emptySet(); playing=false; publish() }
    fun saveProject() { project?.let { autosave.forceSave(it); publish() } }
    fun importUris(uris: List<Uri>) {
        val p = project ?: createProject()
        val comp = p.compositions.first()
        uris.forEach { uri ->
            val name = uri.lastPathSegment?.substringAfterLast('/')?.ifBlank { "Imported media" } ?: "Imported media"
            val mime = context.contentResolver.getType(uri)
            p.assets += MediaAsset(uri=uri.toString(),displayName=name,mimeType=mime)
            comp.layers += Layer(name=name,type=if (mime?.startsWith("audio/")==true) LayerType.AUDIO else LayerType.VIDEO,durationUs=comp.durationUs)
        }
        saveProject(); publish()
    }
    fun selectLayer(id: String?) { selected = id?.let { setOf(it) } ?: emptySet(); timeline?.let { id?.let(it::selectLayer) ?: it.clearSelection() }; publish() }
    fun seek(us: Long) { timeUs=(us.coerceAtLeast(0L)).coerceAtMost(project?.compositions?.firstOrNull()?.durationUs ?: 0L); timeline?.setPlayhead(timeUs); editor?.seek(timeUs/1_000_000.0); publish() }
    fun playPause() { playing=!playing; if (playing) editor?.play() else editor?.pause(); publish() }
    fun split() { timeline?.splitSelectedAtPlayhead(); publish() }
    fun moveSelected(deltaUs: Long) { timeline?.moveSelected(deltaUs); publish() }
    fun toggleVisibility(id:String) { project?.compositions?.firstOrNull()?.layers?.firstOrNull{it.id==id}?.let { it.visible=!it.visible }; publish() }
    fun toggleLock(id:String) { project?.compositions?.firstOrNull()?.layers?.firstOrNull{it.id==id}?.let { it.locked=!it.locked }; publish() }
    fun deleteSelected() { project?.compositions?.firstOrNull()?.layers?.removeAll { it.id in selected }; selected=emptySet(); publish() }
    fun addLayer(type:LayerType) { val c=project?.compositions?.firstOrNull() ?: return; c.layers += Layer(name=type.name.replace('_',' '),type=type,durationUs=c.durationUs); publish() }
    fun renderCpu(source: FrameBuffer): FrameBuffer = CpuReferenceBackend().render(source)
    fun shutdown() { executor.shutdownNow() }
    private fun publish() { val s=state(); bus.publish(s) }
}
