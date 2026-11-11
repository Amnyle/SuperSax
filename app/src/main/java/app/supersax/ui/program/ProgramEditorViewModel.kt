package app.supersax.ui.program

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.supersax.AppContainer
import app.supersax.domain.program.Block
import app.supersax.domain.program.BlockKind
import app.supersax.domain.program.Exercise
import app.supersax.domain.program.ExerciseType
import app.supersax.domain.program.PhoneDrillSlot
import app.supersax.domain.program.Program
import app.supersax.domain.program.ProgramDay
import app.supersax.domain.program.Target
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.util.UUID

/**
 * Edits one program (generated or manual: the same object). Changes are kept in a draft and
 * written on Save.
 */
class ProgramEditorViewModel(private val c: AppContainer, private val programId: String) : ViewModel() {
    private val _draft = MutableStateFlow<Program?>(null)
    val draft: StateFlow<Program?> = _draft.asStateFlow()
    private val _dirty = MutableStateFlow(false)
    val dirty: StateFlow<Boolean> = _dirty.asStateFlow()

    val maxPhoneShare: Double get() = c.content.generatorConfig.phoneDrills.maxShareOfSaxMinutes
    val availablePhoneDrills: List<ExerciseType> get() = c.availablePhoneDrills

    init {
        viewModelScope.launch { _draft.value = c.practice.program(programId) }
    }

    private fun edit(transform: (Program) -> Program) {
        _draft.value = _draft.value?.let(transform)
        _dirty.value = true
    }

    private fun editDay(dayId: String, transform: (ProgramDay) -> ProgramDay) =
        edit { p -> p.copy(days = p.days.map { if (it.id == dayId) transform(it) else it }) }

    private fun editBlock(dayId: String, blockId: String, transform: (Block) -> Block) =
        editDay(dayId) { d -> d.copy(blocks = d.blocks.map { if (it.id == blockId) transform(it) else it }) }

    fun rename(name: String) = edit { it.copy(name = name) }
    fun setEndurance(sec: Int) = edit { it.copy(enduranceCapSec = sec) }
    fun setRest(sec: Int) = edit { it.copy(restSec = sec) }

    fun addDay(dow: Int) = edit { p ->
        if (p.dayFor(dow) != null) p else p.copy(days = (p.days + ProgramDay(newId(), dow, emptyList())).sortedBy { it.dayOfWeek })
    }

    /** Copies one day's blocks onto another weekday. */
    fun copyDay(dayId: String, toDow: Int) = edit { p ->
        val src = p.days.firstOrNull { it.id == dayId } ?: return@edit p
        val copy = ProgramDay(newId(), toDow, src.blocks.map { b -> b.copy(id = newId(), exercises = b.exercises.map { it.copy(id = newId()) }) })
        p.copy(days = (p.days.filter { it.dayOfWeek != toDow } + copy).sortedBy { it.dayOfWeek })
    }

    fun removeDay(dayId: String) = edit { p -> p.copy(days = p.days.filter { it.id != dayId }) }

    fun addBlock(dayId: String, kind: BlockKind) = editDay(dayId) { d ->
        val ex = defaultExercise(kind)
        d.copy(blocks = (d.blocks + Block(newId(), kind, 5, listOf(ex))).sortedBy { it.kind.ordinal })
    }

    fun removeBlock(dayId: String, blockId: String) = editDay(dayId) { d -> d.copy(blocks = d.blocks.filter { it.id != blockId }) }
    fun setBlockMinutes(dayId: String, blockId: String, minutes: Int) = editBlock(dayId, blockId) { it.copy(minutes = minutes) }

    fun addExercise(dayId: String, blockId: String, catalogId: String) = editBlock(dayId, blockId) { b ->
        val type = c.content.exercises.byId(catalogId)?.type ?: return@editBlock b
        b.copy(exercises = b.exercises + Exercise(newId(), catalogId, type))
    }

    fun removeExercise(dayId: String, blockId: String, exerciseId: String) =
        editBlock(dayId, blockId) { b -> b.copy(exercises = b.exercises.filter { it.id != exerciseId }) }

    fun setTarget(dayId: String, blockId: String, exerciseId: String, transform: (Target) -> Target) =
        editBlock(dayId, blockId) { b -> b.copy(exercises = b.exercises.map { if (it.id == exerciseId) it.copy(target = transform(it.target)) else it }) }

    fun addPhoneDrill(dow: Int, type: ExerciseType, minutes: Int) = edit { p -> p.copy(phoneDrills = p.phoneDrills + PhoneDrillSlot(dow, minutes, type)) }
    fun removePhoneDrill(index: Int) = edit { p -> p.copy(phoneDrills = p.phoneDrills.filterIndexed { i, _ -> i != index }) }

    /** Exercises from the catalog that fit a block kind. */
    fun catalogFor(kind: BlockKind) = c.content.exercises.exercises.filter { kindOf(it.type) == kind }

    fun save(onSaved: () -> Unit) {
        val p = _draft.value ?: return
        viewModelScope.launch {
            c.practice.saveProgram(p, makeActive = false, nowMs = c.clock.nowMs())
            _dirty.value = false
            onSaved()
        }
    }

    private fun defaultExercise(kind: BlockKind): Exercise = when (kind) {
        BlockKind.LONG_TONES -> Exercise(newId(), "long-tones-basic", ExerciseType.LONG_TONE)
        BlockKind.SCALES -> Exercise(newId(), "scale-runs", ExerciseType.SCALE)
        BlockKind.NEW_MATERIAL -> Exercise(newId(), "new-material", ExerciseType.NEW_MATERIAL)
        BlockKind.PLAY_FOR_FUN -> Exercise(newId(), "play-for-fun", ExerciseType.PLAY_FOR_FUN)
        BlockKind.BREATHING -> Exercise(newId(), "breathing-pacer", ExerciseType.BREATHING_PACER)
    }

    private fun newId() = UUID.randomUUID().toString().take(8)

    companion object {
        fun kindOf(t: ExerciseType): BlockKind? = when (t) {
            ExerciseType.LONG_TONE, ExerciseType.RANGE_EXTENSION -> BlockKind.LONG_TONES
            ExerciseType.SCALE -> BlockKind.SCALES
            ExerciseType.NEW_MATERIAL -> BlockKind.NEW_MATERIAL
            ExerciseType.PLAY_FOR_FUN -> BlockKind.PLAY_FOR_FUN
            ExerciseType.BREATHING_PACER -> BlockKind.BREATHING
            else -> null
        }
    }
}
