package org.kyowa.familyaddons.gui

import io.github.notenoughupdates.moulconfig.annotations.Category
import io.github.notenoughupdates.moulconfig.annotations.ConfigAccordionId
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorAccordion
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorBoolean
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorButton
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorColour
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorDropdown
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorKeybind
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorSlider
import io.github.notenoughupdates.moulconfig.annotations.ConfigEditorText
import io.github.notenoughupdates.moulconfig.annotations.ConfigOption
import org.kyowa.familyaddons.config.FamilyConfig
import org.kyowa.familyaddons.config.FamilyConfigManager
import java.lang.reflect.Field
import kotlin.math.roundToInt

/**
 * The option tree the config screen draws, read off the real config classes.
 * The MoulConfig annotations are kept as the description of each option (name,
 * text, editor kind, range, sub category); only the screen that draws them is
 * ours. Values are read from and written to the same fields the features use,
 * and saved to the same config.json, so nothing about the stored config changed.
 */
class OptionSpec(
    val key: String,
    val name: String,
    val desc: String,
    /** boolean, slider, text, dropdown, keybind, colour, button, accordion, shortcuts, gfslist, mobpicker */
    val type: String,
    val accordion: Int? = null,
    val id: Int? = null,
    val min: Float = 0f,
    val max: Float = 1f,
    val step: Float = 0.1f,
    val values: List<String> = emptyList(),
    val text: String = "",
    val field: Field,
    val holder: Any,
    /** The value a fresh config has, for the reset arrow. */
    val default: Any?,
)

class CategorySpec(val name: String, val desc: String, val key: String, val options: List<OptionSpec>)

object ConfigSpec {
    var categories: List<CategorySpec> = emptyList()
        private set

    /** Built once per config load; call again if the config object is replaced. */
    fun load() {
        val cfg = FamilyConfigManager.config
        val pristine = FamilyConfig()
        val out = ArrayList<CategorySpec>()
        for (cf in FamilyConfig::class.java.declaredFields) {
            val cat = cf.getAnnotation(Category::class.java) ?: continue
            cf.isAccessible = true
            val holder = cf.get(cfg) ?: continue
            val fresh = cf.get(pristine)
            val options = ArrayList<OptionSpec>()
            for (f in holder.javaClass.declaredFields) {
                val opt = f.getAnnotation(ConfigOption::class.java) ?: continue
                f.isAccessible = true
                val key = cf.name + "." + f.name
                val inAcc = f.getAnnotation(ConfigAccordionId::class.java)?.id
                val default = runCatching { fresh?.let { f.get(it) } }.getOrNull()
                val spec = when {
                    f.isAnnotationPresent(ConfigEditorAccordion::class.java) ->
                        OptionSpec(key, opt.name, opt.desc, "accordion", inAcc, id = f.getAnnotation(ConfigEditorAccordion::class.java).id, field = f, holder = holder, default = false)
                    key == "utilities.commandShortcuts" -> OptionSpec(key, opt.name, opt.desc, "shortcuts", inAcc, field = f, holder = holder, default = default)
                    key == "utilities.chatTimerEdit" -> OptionSpec(key, opt.name, opt.desc, "timerlist", inAcc, field = f, holder = holder, default = null)
                    key == "keybinds.customGfsEdit" -> OptionSpec(key, opt.name, opt.desc, "gfslist", inAcc, field = f, holder = holder, default = null)
                    key == "playerDisguise.mobId" -> OptionSpec(key, opt.name, opt.desc, "mobpicker", inAcc, field = f, holder = holder, default = default)
                    f.isAnnotationPresent(ConfigEditorBoolean::class.java) -> OptionSpec(key, opt.name, opt.desc, "boolean", inAcc, field = f, holder = holder, default = default)
                    f.isAnnotationPresent(ConfigEditorSlider::class.java) -> {
                        val s = f.getAnnotation(ConfigEditorSlider::class.java)
                        OptionSpec(key, opt.name, opt.desc, "slider", inAcc, min = s.minValue, max = s.maxValue, step = s.minStep, field = f, holder = holder, default = default)
                    }
                    f.isAnnotationPresent(ConfigEditorText::class.java) -> OptionSpec(key, opt.name, opt.desc, "text", inAcc, field = f, holder = holder, default = default)
                    f.isAnnotationPresent(ConfigEditorDropdown::class.java) ->
                        OptionSpec(key, opt.name, opt.desc, "dropdown", inAcc, values = f.getAnnotation(ConfigEditorDropdown::class.java).values.toList(), field = f, holder = holder, default = default)
                    f.isAnnotationPresent(ConfigEditorKeybind::class.java) -> OptionSpec(key, opt.name, opt.desc, "keybind", inAcc, field = f, holder = holder, default = default)
                    f.isAnnotationPresent(ConfigEditorColour::class.java) -> OptionSpec(key, opt.name, opt.desc, "colour", inAcc, field = f, holder = holder, default = default)
                    f.isAnnotationPresent(ConfigEditorButton::class.java) ->
                        OptionSpec(key, opt.name, opt.desc, "button", inAcc, text = f.getAnnotation(ConfigEditorButton::class.java).buttonText, field = f, holder = holder, default = null)
                    else -> continue
                }
                options.add(spec)
            }
            out.add(CategorySpec(cat.name, cat.desc, cf.name, options))
        }
        categories = out
    }
}

/** Reads and writes the live config fields; every write saves config.json. */
object Values {
    private fun get(o: OptionSpec): Any? = runCatching { o.field.get(o.holder) }.getOrNull()
    private fun put(o: OptionSpec, v: Any?) { runCatching { o.field.set(o.holder, v) }; save() }

    fun save() = FamilyConfigManager.save()

    fun bool(o: OptionSpec): Boolean = get(o) as? Boolean ?: false
    fun float(o: OptionSpec): Float = (get(o) as? Number)?.toFloat() ?: o.min
    fun int(o: OptionSpec): Int = (get(o) as? Number)?.toInt() ?: 0
    fun string(o: OptionSpec): String = get(o)?.toString() ?: ""

    fun set(o: OptionSpec, v: Boolean) = put(o, v)
    fun set(o: OptionSpec, v: Float) = put(o, coerce(o, v))
    fun set(o: OptionSpec, v: Int) = put(o, coerce(o, v))
    fun set(o: OptionSpec, v: String) = put(o, v)

    /** A slider may sit on an Int, Float or Double field. */
    private fun coerce(o: OptionSpec, v: Number): Any = when (o.field.type) {
        java.lang.Integer.TYPE, Integer::class.java -> v.toFloat().roundToInt()
        java.lang.Double.TYPE, java.lang.Double::class.java -> v.toDouble()
        java.lang.Long.TYPE, java.lang.Long::class.java -> v.toLong()
        else -> v.toFloat()
    }

    fun isDefault(o: OptionSpec): Boolean {
        if (o.type == "button" || o.type == "accordion" || o.type == "gfslist") return true
        val cur = get(o); val def = o.default
        if (cur is Number && def is Number) return kotlin.math.abs(cur.toDouble() - def.toDouble()) < 1e-6
        return cur == def
    }

    fun clear(o: OptionSpec) { if (o.default != null) put(o, o.default) }

    fun runButton(o: OptionSpec) { (get(o) as? Runnable)?.run() }

    // ── strings by "category.field" for data that is not one option ─────────
    private fun fieldFor(key: String): Pair<Field, Any>? {
        val (cat, name) = key.split(".", limit = 2).let { if (it.size == 2) it[0] to it[1] else return null }
        val cf = FamilyConfig::class.java.declaredFields.firstOrNull { it.name == cat } ?: return null
        cf.isAccessible = true
        val holder = cf.get(FamilyConfigManager.config) ?: return null
        val f = holder.javaClass.declaredFields.firstOrNull { it.name == name } ?: return null
        f.isAccessible = true
        return f to holder
    }
    fun rawString(key: String): String? = fieldFor(key)?.let { (f, h) -> f.get(h)?.toString() }
    fun setRaw(key: String, v: String) { fieldFor(key)?.let { (f, h) -> f.set(h, v); save() } }

    // ── UI memory: chosen look, panel positions; a map in General, saved with the rest ──
    private val ui get() = FamilyConfigManager.config.general.uiMemory
    fun uiString(key: String): String? = ui[key]
    fun uiInt(key: String, def: Int): Int = ui[key]?.toIntOrNull() ?: def
    fun setUi(key: String, v: String) { ui[key] = v; save() }
    fun setUi(key: String, v: Int) { ui[key] = v.toString(); save() }
}
