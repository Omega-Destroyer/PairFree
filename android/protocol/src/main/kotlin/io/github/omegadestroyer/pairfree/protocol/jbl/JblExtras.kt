package io.github.omegadestroyer.pairfree.protocol.jbl

import io.github.omegadestroyer.pairfree.protocol.Form
import io.github.omegadestroyer.pairfree.protocol.Setting
import io.github.omegadestroyer.pairfree.protocol.SettingChange

/**
 * The JBL settings beyond noise control, auto power-off and auto play/pause (docs/jbl/PROTOCOL.md section 4.8):
 * Smart Audio & Video, voice prompts, left/right balance, VoiceAware, SafeSound, 3D spatial sound, the sound effect
 * and audio limiter for calls, speak to chat, Hi-Res audio and Find My Buds. A model has those its JBL dashboard
 * shows and its control class sends ([JblModel.extras]); each goes the way its family's class sends it: Harman
 * commands for the direct and feature families (with feature records for the effect, the limiter and Hi-Res on the
 * feature family), protocol 4's features for the others.
 */
internal class JblExtras(private val model: JblModel) {
    private val p4 get() = model.family == JblModel.Family.P4
    private fun has(id: String) = id in model.extras

    private var smartSwitch: List<Int>? = null
    private var voicePrompts: Boolean? = null
    private var voicePromptLevel = JblP4.IGNORE_LEVEL
    private var balanceOn: Boolean? = null
    private var balanceLevel: Int? = null
    private var voiceAwareOn: Boolean? = null
    private var voiceAwareLevel: Int? = null
    private var safeSound: Boolean? = null
    private var safeSoundLevel = JblP4.IGNORE_LEVEL
    private var spatialOn: Boolean? = null
    private var spatialType: Int? = null
    private var effectOn: Boolean? = null
    private var effect: Int? = null
    private var limiter: Boolean? = null
    private var talkOn: Boolean? = null
    private var talkSeconds: Int? = null
    private var hiRes: Boolean? = null
    private var adaptiveAnc: Boolean? = null
    private var lowVolumeEq: Boolean? = null
    private var autoCompensation: Boolean? = null
    private var finding = false
    // Each gesture's action, by JBL's gesture id (1 left tap, 2 right tap and so on).
    private val gestures = mutableMapOf<Int, Int>()
    private var mutedLeft = false
    private var mutedRight = false

    /** The reads of a direct or feature model, after its own. */
    fun reads(): List<JblFrame> = if (p4) emptyList() else buildList {
        if (has(SMART_AV)) add(JblPacket.of(JblCommand.SMART_SWITCH, READ))
        if (has(VOICE_PROMPTS)) add(JblPacket.of(JblCommand.VOICE_PROMPTS, PROMPT_READ))
        if (has(BALANCE)) add(JblPacket.of(JblCommand.BALANCE, READ))
        if (has(VOICE_AWARE)) add(JblPacket.of(JblCommand.VOICE_AWARE, READ))
        if (has(SAFE_SOUND)) add(JblPacket.of(JblCommand.SAFE_SOUND, READ))
        if (has(SPATIAL)) add(JblPacket.of(JblCommand.SPATIAL, READ))
        if (has(SPEAK_TO_CHAT)) add(JblPacket.of(JblCommand.SPEAK_TO_CHAT, READ))
        if (has(LOW_VOLUME_EQ) && model.family == JblModel.Family.DIRECT) add(JblPacket.of(JblCommand.LOW_VOLUME_EQ, READ))
        if (has(GESTURES)) add(gestureRead())
        if (has(ADAPTIVE_ANC) || has(AUTO_COMPENSATION)) add(JblPacket.of(JblCommand.NOISE_MODES, ADVANCED_READ))
        val features = listOfNotNull(
            JblFeature.SOUND_EFFECT.takeIf { has(SOUND_EFFECT) },
            JblFeature.AUDIO_LIMITER.takeIf { has(AUDIO_LIMITER) },
            JblFeature.HI_RES.takeIf { has(HI_RES) },
            JblFeature.LOW_VOLUME_EQ.takeIf { has(LOW_VOLUME_EQ) && model.family == JblModel.Family.FEATURE },
        )
        if (features.isNotEmpty()) add(JblPacket.featureGet(features))
    }

    /** The protocol 4 features read with the model's own. */
    fun p4Features(): List<Int> = if (!p4) emptyList() else listOfNotNull(
        JblP4.FEATURE_SMART_SWITCH.takeIf { has(SMART_AV) },
        JblP4.FEATURE_VOICE_PROMPT.takeIf { has(VOICE_PROMPTS) },
        JblP4.FEATURE_BALANCE.takeIf { has(BALANCE) },
        JblP4.FEATURE_SIDETONE.takeIf { has(VOICE_AWARE) },
        JblP4.FEATURE_MAX_VOLUME_LIMIT.takeIf { has(SAFE_SOUND) },
        JblP4.FEATURE_SPATIAL_SCENE.takeIf { has(SPATIAL) },
        JblP4.FEATURE_SOUND_EFFECT.takeIf { has(SOUND_EFFECT) },
        JblP4.FEATURE_AUDIO_LIMITER.takeIf { has(AUDIO_LIMITER) },
        JblP4.FEATURE_SMART_TALK.takeIf { has(SPEAK_TO_CHAT) },
        JblP4.FEATURE_HI_RES.takeIf { has(HI_RES) },
        JblP4.FEATURE_ADAPTIVE_ANC.takeIf { has(ADAPTIVE_ANC) },
        JblP4.FEATURE_LOW_VOLUME_EQ.takeIf { has(LOW_VOLUME_EQ) },
        JblP4.FEATURE_AUTO_COMPENSATION.takeIf { has(AUTO_COMPENSATION) },
    )

    /** The commands whose answers [aa] reads. */
    fun reportsAa(command: Int): Boolean = command in AA_COMMANDS

    /** A Harman reply (02 is the reply's sub-command for each). */
    fun aa(command: Int, p: ByteArray) {
        if (p.isEmpty()) return
        when (command) {
            JblCommand.SMART_SWITCH -> if (p.u8(0) == RET && p.size >= 9) smartSwitch = (0 until 4).map { p.u16(1 + it * 2) }
            // 05 and the type; 02 is the prompt language's version, which PairFree does not show.
            JblCommand.VOICE_PROMPTS -> if (p.u8(0) == PROMPT_RET && p.size >= 2) voicePrompts = p.u8(1) == 1
            JblCommand.BALANCE -> if (p.u8(0) == RET && p.size >= 5) {
                balanceOn = p.u8(2) == 1
                balanceLevel = p.u8(4)
            }
            JblCommand.VOICE_AWARE -> if (p.u8(0) == RET && p.size >= 2) {
                voiceAwareLevel = p.u8(1)
                voiceAwareOn = if (p.size >= 3) p.u8(2) != 0 else p.u8(1) != 0
            }
            JblCommand.SAFE_SOUND -> if (p.u8(0) == RET && p.size >= 3) safeSound = p.u8(2) == 1
            JblCommand.LOW_VOLUME_EQ -> if (p.u8(0) == RET && p.size >= 2) lowVolumeEq = p.u8(1) == 1
            JblCommand.SPATIAL -> if (p.u8(0) == RET && p.size >= 3) {
                spatialOn = p.u8(1) == 1
                spatialType = p.u8(2)
            }
            JblCommand.SPEAK_TO_CHAT -> if (p.u8(0) == RET && p.size >= 3) {
                talkOn = p.u8(1) == 1
                talkSeconds = p.u8(2)
            }
            // Advanced noise cancelling (AdvancedAncCmd.parse): 22, then pairs of type and value. Noise control reads the other sub-commands.
            JblCommand.NOISE_MODES -> if (p.u8(0) == ADVANCED_RET) {
                var at = 1
                while (at + 1 < p.size) {
                    when (p.u8(at)) {
                        ADVANCED_ADAPTIVE -> adaptiveAnc = p.u8(at + 1) == 1
                        ADVANCED_AUTO_COMPENSATION -> autoCompensation = p.u8(at + 1) == 1
                    }
                    at += 2
                }
            }
            // 01 or 02, then pairs of gesture and action (HandleParse.parseGestureData).
            JblCommand.GESTURES -> if (p.u8(0) == 1 || p.u8(0) == 2) {
                var at = 1
                while (at + 1 < p.size) {
                    WIRE_GESTURES[p.u8(at)]?.let { gestures[it] = p.u8(at + 1) }
                    at += 2
                }
            }
        }
    }

    /** A feature record (command 06) of the feature family. */
    fun feature(id: Int, v: ByteArray) {
        if (v.isEmpty()) return
        when (id) {
            JblFeature.SOUND_EFFECT -> if (v.size >= 2) {
                effectOn = v.u8(0) == 1
                effect = v.u8(1)
            }
            JblFeature.AUDIO_LIMITER -> limiter = v.u8(0) == 1
            JblFeature.HI_RES -> hiRes = v.u8(0) == 1
            JblFeature.LOW_VOLUME_EQ -> lowVolumeEq = v.u8(0) == 1
        }
    }

    /** A protocol 4 feature: most are a status bit (7) and a level (bits 0 to 6). */
    fun p4(id: Int, v: ByteArray) {
        if (v.isEmpty()) return
        val on = v.u8(0) and 0x80 != 0
        val level = v.u8(0) and 0x7F
        when (id) {
            JblP4.FEATURE_SMART_SWITCH -> if (v.size >= 8) smartSwitch = (0 until 4).map { v.u16(it * 2) }
            JblP4.FEATURE_VOICE_PROMPT -> {
                voicePrompts = on
                voicePromptLevel = level
            }
            // Protocol 4 sends the balance 50 lower: 34 to 66 (gn.l0, General4Control.setLRBalanceStatus).
            JblP4.FEATURE_BALANCE -> {
                balanceOn = on
                balanceLevel = level + P4_BALANCE_OFFSET
            }
            JblP4.FEATURE_SIDETONE -> {
                voiceAwareOn = on
                voiceAwareLevel = level
            }
            JblP4.FEATURE_MAX_VOLUME_LIMIT -> {
                safeSound = on
                safeSoundLevel = level
            }
            JblP4.FEATURE_SPATIAL_SCENE -> {
                spatialOn = on
                spatialType = P4_SPATIAL.entries.firstOrNull { it.value == level }?.key ?: level
            }
            JblP4.FEATURE_SOUND_EFFECT -> {
                effectOn = on
                effect = level
            }
            JblP4.FEATURE_AUDIO_LIMITER -> limiter = on
            JblP4.FEATURE_SMART_TALK -> {
                talkOn = on
                talkSeconds = level
            }
            JblP4.FEATURE_HI_RES -> hiRes = on
            JblP4.FEATURE_ADAPTIVE_ANC -> adaptiveAnc = on
            JblP4.FEATURE_LOW_VOLUME_EQ -> lowVolumeEq = on
            JblP4.FEATURE_AUTO_COMPENSATION -> autoCompensation = on
        }
    }

    /** Adaptive noise cancelling and auto compensation, the noise card's "customize" entries in JBL's app. */
    fun adaptiveRows(): List<Setting> = listOfNotNull(
        adaptiveAnc?.takeIf { has(ADAPTIVE_ANC) }?.let { Setting.Toggle(ADAPTIVE_KEY, it) },
        autoCompensation?.takeIf { has(AUTO_COMPENSATION) }?.let { Setting.Toggle(COMPENSATION_KEY, it) },
    )

    /** The rows for the sound tab, the call settings and the rest. */
    fun soundRows(): List<Setting> = listOfNotNull(
        smartRow(),
        spatialOn?.takeIf { has(SPATIAL) }?.let { on ->
            Setting.Choice(SPATIAL_KEY, if (on) SPATIAL_TYPES[spatialType] ?: SPATIAL_TYPES[MOVIE] else OFF, listOf(OFF) + SPATIAL_TYPES.values)
        },
        balanceLevel?.takeIf { has(BALANCE) }?.let { l ->
            val shown = if (balanceOn == true) l.coerceIn(BALANCE_MIN, BALANCE_MAX) else BALANCE_MIDDLE
            Setting.Level(BALANCE_KEY, shown - BALANCE_MIN, 0, BALANCE_MAX - BALANCE_MIN)
        },
        safeSound?.takeIf { has(SAFE_SOUND) }?.let { Setting.Toggle(SAFE_SOUND_KEY, it) },
        hiRes?.takeIf { has(HI_RES) }?.let { Setting.Toggle(HI_RES_KEY, it) },
        lowVolumeEq?.takeIf { has(LOW_VOLUME_EQ) }?.let { Setting.Toggle(LOW_EQ_KEY, it) },
    )

    fun callRows(): List<Setting> = listOfNotNull(
        voiceAwareOn?.takeIf { has(VOICE_AWARE) }?.let { on ->
            Setting.Choice(VOICE_AWARE_KEY, if (on) LEVELS[voiceAwareLevel] ?: LEVELS.getValue(LEVEL_MID) else OFF, listOf(OFF) + LEVELS.values)
        },
        effectOn?.takeIf { has(SOUND_EFFECT) }?.let { on ->
            val names = model.effects.ifEmpty { DEFAULT_EFFECTS }
            Setting.Choice(EFFECT_KEY, if (on) names.getOrNull(effect ?: 0) else OFF, listOf(OFF) + names)
        },
        limiter?.takeIf { has(AUDIO_LIMITER) }?.let { Setting.Toggle(LIMITER_KEY, it) },
    )

    fun otherRows(): List<Setting> = listOfNotNull(
        talkOn?.takeIf { has(SPEAK_TO_CHAT) }?.let { on ->
            Setting.Choice(TALK_KEY, if (on) (talkSeconds ?: TALK_SECONDS.first()).coerceAtLeast(TALK_SECONDS.first()).toString() else OFF,
                listOf(OFF) + TALK_SECONDS.map { it.toString() })
        },
        voicePrompts?.takeIf { has(VOICE_PROMPTS) }?.let { Setting.Toggle(PROMPTS_KEY, it) },
    )

    /** Find My Buds: one button for both earbuds, and while they beep a switch to mute each, as on Galaxy Buds. */
    fun findRows(): List<Setting> = if (!has(FIND_BUDS)) emptyList() else buildList {
        add(Setting.Action(FIND_KEY, active = finding))
        if (finding && model.form != Form.HEADPHONES) {
            add(Setting.Toggle(MUTE_LEFT, mutedLeft))
            add(Setting.Toggle(MUTE_RIGHT, mutedRight))
        }
    }

    /** The touch set of one earbud ("left" or "right"), as JBL's gesture page offers them. */
    fun touchRows(side: String): List<Setting> {
        if (!has(GESTURES)) return emptyList()
        val ids = SIDE_GESTURES.getValue(side)
        if (ids.none { it in gestures }) return emptyList()
        return listOf(Setting.Choice(TOUCH_KEYS.getValue(side), touchSet(ids), TOUCH_SETS))
    }

    /** The set a side's actions make, named by its tap as JBL's app names it (CustomGesture.generateName), else by its hold. */
    private fun touchSet(ids: List<Int>): String {
        return setName(gestures[ids[0]] ?: 0).takeUnless { it == NONE } ?: setName(gestures[ids[3]] ?: 0)
    }

    private fun setName(action: Int): String = when (action) {
        0 -> NONE
        ACTION_VOLUME_UP -> VOLUME
        ACTION_PLAY_PAUSE_VA -> PLAYBACK
        in NOISE_CYCLES -> AMBIENT
        // Play/pause alone, a voice assistant set or the sports set: from JBL's other gesture pages.
        ACTION_PLAY_PAUSE, ACTION_SPORTS, in ASSISTANTS -> OTHER
        else -> NONE
    }

    private fun smartRow(): Setting.Choice? {
        if (!has(SMART_AV)) return null
        val now = smartSwitch ?: return null
        val modes = model.smartAudioVideo
        // As JBL's app compares them: the first three numbers, and the fourth only where the model gives four.
        val current = modes.entries.firstOrNull { (_, v) -> v.indices.all { now.getOrNull(it) == v[it] } }?.key
        return Setting.Choice(SMART_KEY, current?.let { SMART_NAMES[it] }, SMART_ORDER.filter { it in modes }.map { SMART_NAMES.getValue(it) })
    }

    /** The frames for a change to one of these rows, or null when the change is not one of them. */
    fun change(change: SettingChange): List<JblFrame>? = when (change) {
        is SettingChange.Toggle -> when (change.key) {
            PROMPTS_KEY -> listOf(prompts(change.on))
            SAFE_SOUND_KEY -> listOf(safe(change.on))
            LIMITER_KEY -> listOf(limit(change.on))
            HI_RES_KEY -> listOf(hiResSet(change.on))
            LOW_EQ_KEY -> listOf(lowEq(change.on))
            ADAPTIVE_KEY -> listOf(advanced(ADVANCED_ADAPTIVE, JblP4.FEATURE_ADAPTIVE_ANC, change.on).also { adaptiveAnc = change.on })
            COMPENSATION_KEY -> listOf(advanced(ADVANCED_AUTO_COMPENSATION, JblP4.FEATURE_AUTO_COMPENSATION, change.on).also { autoCompensation = change.on })
            MUTE_LEFT, MUTE_RIGHT -> mute(change.key == MUTE_LEFT, change.on)
            else -> null
        }
        is SettingChange.Choice -> when (change.key) {
            TOUCH_LEFT -> touch("left", change.option)?.let(::listOf)
            TOUCH_RIGHT -> touch("right", change.option)?.let(::listOf)
            SMART_KEY -> smart(change.option)?.let(::listOf)
            SPATIAL_KEY -> listOf(spatial(change.option))
            VOICE_AWARE_KEY -> listOf(voiceAware(change.option))
            EFFECT_KEY -> effectSet(change.option)?.let(::listOf)
            TALK_KEY -> listOf(talk(change.option))
            else -> null
        }
        is SettingChange.Level -> if (change.key == BALANCE_KEY) listOf(balance(change.value + BALANCE_MIN)) else null
        is SettingChange.Press -> if (change.key == FIND_KEY) find(!finding) else null
    }

    /** The read that confirms a change to [key]. */
    fun readBack(key: String): JblFrame? {
        if (p4) {
            val f = when (key) {
                SMART_KEY -> JblP4.FEATURE_SMART_SWITCH
                PROMPTS_KEY -> JblP4.FEATURE_VOICE_PROMPT
                BALANCE_KEY -> JblP4.FEATURE_BALANCE
                VOICE_AWARE_KEY -> JblP4.FEATURE_SIDETONE
                SAFE_SOUND_KEY -> JblP4.FEATURE_MAX_VOLUME_LIMIT
                SPATIAL_KEY -> JblP4.FEATURE_SPATIAL_SCENE
                EFFECT_KEY -> JblP4.FEATURE_SOUND_EFFECT
                LIMITER_KEY -> JblP4.FEATURE_AUDIO_LIMITER
                TALK_KEY -> JblP4.FEATURE_SMART_TALK
                HI_RES_KEY -> JblP4.FEATURE_HI_RES
                ADAPTIVE_KEY -> JblP4.FEATURE_ADAPTIVE_ANC
                LOW_EQ_KEY -> JblP4.FEATURE_LOW_VOLUME_EQ
                COMPENSATION_KEY -> JblP4.FEATURE_AUTO_COMPENSATION
                else -> return null
            }
            return JblP4Packet.get(listOf(f))
        }
        return when (key) {
            SMART_KEY -> JblPacket.of(JblCommand.SMART_SWITCH, READ)
            PROMPTS_KEY -> JblPacket.of(JblCommand.VOICE_PROMPTS, PROMPT_READ)
            BALANCE_KEY -> JblPacket.of(JblCommand.BALANCE, READ)
            VOICE_AWARE_KEY -> JblPacket.of(JblCommand.VOICE_AWARE, READ)
            SAFE_SOUND_KEY -> JblPacket.of(JblCommand.SAFE_SOUND, READ)
            SPATIAL_KEY -> JblPacket.of(JblCommand.SPATIAL, READ)
            TALK_KEY -> JblPacket.of(JblCommand.SPEAK_TO_CHAT, READ)
            TOUCH_LEFT, TOUCH_RIGHT -> gestureRead()
            ADAPTIVE_KEY, COMPENSATION_KEY -> JblPacket.of(JblCommand.NOISE_MODES, ADVANCED_READ)
            LOW_EQ_KEY -> if (model.family == JblModel.Family.DIRECT) {
                JblPacket.of(JblCommand.LOW_VOLUME_EQ, READ)
            } else {
                JblPacket.featureGet(listOf(JblFeature.LOW_VOLUME_EQ))
            }
            EFFECT_KEY -> JblPacket.featureGet(listOf(JblFeature.SOUND_EFFECT))
            LIMITER_KEY -> JblPacket.featureGet(listOf(JblFeature.AUDIO_LIMITER))
            HI_RES_KEY -> JblPacket.featureGet(listOf(JblFeature.HI_RES))
            else -> null
        }
    }

    /** One advanced noise cancelling switch: a type and its value on command 91, or its protocol 4 feature (General4Control.setAdvanceAnc). */
    private fun advanced(type: Int, feature: Int, on: Boolean): JblFrame = if (p4) {
        JblP4Packet.set(feature to JblP4Packet.statusLevel(on, JblP4.IGNORE_LEVEL))
    } else {
        JblPacket.of(JblCommand.NOISE_MODES, ADVANCED_SET, type, if (on) 1 else 0)
    }

    private fun gestureRead(): JblFrame = JblPacket.of(JblCommand.GESTURES, 1, ALL_GESTURES)

    /**
     * One earbud's four gestures set to [option]'s actions (CustomGesture's sets, with the voice assistant JBL's
     * app uses by default). The noise set keeps the cycle the earbud's tap already has.
     */
    private fun touch(side: String, option: String): JblFrame? {
        val ids = SIDE_GESTURES.getValue(side)
        val noise = gestures[ids[0]]?.takeIf { it in NOISE_CYCLES } ?: model.gestureNoise
        val actions = when (option) {
            PLAYBACK -> listOf(ACTION_PLAY_PAUSE_VA, ACTION_NEXT, ACTION_PREVIOUS, ACTION_ASSISTANT)
            AMBIENT -> listOf(noise, ACTION_TALK_THRU, 0, ACTION_ASSISTANT)
            VOLUME -> listOf(ACTION_VOLUME_UP, ACTION_VOLUME_DOWN, 0, ACTION_ASSISTANT)
            NONE -> listOf(0, 0, 0, 0)
            else -> return null
        }
        val pairs = ids.zip(actions).flatMap { (id, action) ->
            gestures[id] = action
            listOf(ID_WIRE.getValue(id), action)
        }
        return JblPacket.of(JblCommand.GESTURES, SET, *pairs.toIntArray())
    }

    private fun smart(option: String): JblFrame? {
        val mode = SMART_NAMES.entries.firstOrNull { it.value == option }?.key ?: return null
        val v = model.smartAudioVideo[mode] ?: return null
        // Three numbers leave the fourth at 65535, as JBL's app writes them.
        val all = (v + List(4) { NO_LATENCY }).take(4)
        smartSwitch = all
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_SMART_SWITCH to all.flatMap { listOf(it and 0xFF, it shr 8) }.map { it.toByte() }.toByteArray())
        } else {
            JblPacket.of(JblCommand.SMART_SWITCH, SET, *all.flatMap { listOf(it and 0xFF, it shr 8) }.toIntArray())
        }
    }

    private fun prompts(on: Boolean): JblFrame {
        voicePrompts = on
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_VOICE_PROMPT to JblP4Packet.statusLevel(on, voicePromptLevel))
        } else {
            JblPacket.of(JblCommand.VOICE_PROMPTS, PROMPT_SET, if (on) 1 else 0)
        }
    }

    private fun balance(level: Int): JblFrame {
        val l = level.coerceIn(BALANCE_MIN, BALANCE_MAX)
        // The middle is no balance at all, which JBL's switch turns off.
        val on = l != BALANCE_MIDDLE
        balanceOn = on
        balanceLevel = l
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_BALANCE to JblP4Packet.statusLevel(on, l - P4_BALANCE_OFFSET))
        } else {
            JblPacket.of(JblCommand.BALANCE, SET, BALANCE_STATUS, if (on) 1 else 0, BALANCE_VALUE, l)
        }
    }

    private fun voiceAware(option: String): JblFrame {
        val level = LEVELS.entries.firstOrNull { it.value == option }?.key
        val on = level != null
        val l = level ?: voiceAwareLevel?.takeIf { it in LEVELS } ?: LEVEL_MID
        voiceAwareOn = on
        voiceAwareLevel = l
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_SIDETONE to JblP4Packet.statusLevel(on, l))
        } else {
            JblPacket.of(JblCommand.VOICE_AWARE, SET, l, if (on) 1 else 0)
        }
    }

    private fun safe(on: Boolean): JblFrame {
        safeSound = on
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_MAX_VOLUME_LIMIT to JblP4Packet.statusLevel(on, safeSoundLevel))
        } else {
            JblPacket.of(JblCommand.SAFE_SOUND, SET, SAFE_SOUND_STATUS, if (on) 1 else 0)
        }
    }

    private fun spatial(option: String): JblFrame {
        val type = SPATIAL_TYPES.entries.firstOrNull { it.value == option }?.key
        val on = type != null
        val t = type ?: spatialType?.takeIf { it in SPATIAL_TYPES } ?: MOVIE
        spatialOn = on
        spatialType = t
        return if (p4) {
            // Protocol 4 numbers movie 1 and music 2 (General4Control.set3DSpatialStatus).
            JblP4Packet.set(JblP4.FEATURE_SPATIAL_SCENE to JblP4Packet.statusLevel(on, P4_SPATIAL.getValue(t)))
        } else {
            JblPacket.of(JblCommand.SPATIAL, SET, if (on) 1 else 0, t)
        }
    }

    private fun effectSet(option: String): JblFrame? {
        val names = model.effects.ifEmpty { DEFAULT_EFFECTS }
        val index = names.indexOf(option)
        if (index < 0 && option != OFF) return null
        val on = index >= 0
        val e = if (on) index else effect ?: 0
        effectOn = on
        effect = e
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_SOUND_EFFECT to JblP4Packet.statusLevel(on, e))
        } else {
            JblPacket.featureSet(JblFeature.SOUND_EFFECT, if (on) 1 else 0, e)
        }
    }

    private fun limit(on: Boolean): JblFrame {
        limiter = on
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_AUDIO_LIMITER to JblP4Packet.statusLevel(on, JblP4.IGNORE_LEVEL))
        } else {
            JblPacket.featureSet(JblFeature.AUDIO_LIMITER, if (on) 1 else 0)
        }
    }

    private fun hiResSet(on: Boolean): JblFrame {
        hiRes = on
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_HI_RES to JblP4Packet.statusLevel(on, JblP4.IGNORE_LEVEL))
        } else {
            JblPacket.featureSet(JblFeature.HI_RES, if (on) 1 else 0)
        }
    }

    /** Low volume EQ: command 9E on the direct family (LowEQCompensationCmd), a feature record or protocol 4 feature on the others. */
    private fun lowEq(on: Boolean): JblFrame {
        lowVolumeEq = on
        val v = if (on) 1 else 0
        return when (model.family) {
            JblModel.Family.DIRECT -> JblPacket.of(JblCommand.LOW_VOLUME_EQ, SET, v)
            JblModel.Family.FEATURE -> JblPacket.featureSet(JblFeature.LOW_VOLUME_EQ, v)
            JblModel.Family.P4 -> JblP4Packet.set(JblP4.FEATURE_LOW_VOLUME_EQ to JblP4Packet.statusLevel(on, JblP4.IGNORE_LEVEL))
        }
    }

    private fun talk(option: String): JblFrame {
        val seconds = option.toIntOrNull()
        val on = seconds != null
        val s = seconds ?: talkSeconds?.takeIf { it in TALK_SECONDS } ?: TALK_SECONDS.first()
        talkOn = on
        talkSeconds = s
        return if (p4) {
            JblP4Packet.set(JblP4.FEATURE_SMART_TALK to JblP4Packet.statusLevel(on, s))
        } else {
            JblPacket.of(JblCommand.SPEAK_TO_CHAT, SET, if (on) 1 else 0, s)
        }
    }

    /** Starts both earbuds beeping, or stops them. */
    private fun find(start: Boolean): List<JblFrame> {
        finding = start
        mutedLeft = false
        mutedRight = false
        return beep(left = start, right = start)
    }

    private fun mute(left: Boolean, muted: Boolean): List<JblFrame>? {
        if (!finding) return null
        if (left) mutedLeft = muted else mutedRight = muted
        return if (p4) beep(!mutedLeft, !mutedRight) else listOf(JblPacket.of(JblCommand.BEEPING, beepValue(left, !muted)))
    }

    /** The frames that leave the left and right earbuds beeping or not. */
    private fun beep(left: Boolean, right: Boolean): List<JblFrame> {
        if (p4) {
            // One byte: bit 7 the case, 6 the right, 5 the left, and the bits below set (BitListFeature).
            val value = ((if (right) 1 else 0) shl 6) or ((if (left) 1 else 0) shl 5) or 0x1F
            return listOf(JblP4Packet.set(JblP4.FEATURE_FIND_MY_BUDS to byteArrayOf(value.toByte())))
        }
        // One earbud per command, as JBL's app sends them.
        return listOf(JblPacket.of(JblCommand.BEEPING, beepValue(true, left)), JblPacket.of(JblCommand.BEEPING, beepValue(false, right)))
    }

    private fun beepValue(left: Boolean, on: Boolean): Int = when {
        left && on -> BEEP_LEFT
        left -> STOP_LEFT
        on -> BEEP_RIGHT
        else -> STOP_RIGHT
    }

    private fun ByteArray.u8(at: Int): Int = this[at].toInt() and 0xFF
    private fun ByteArray.u16(at: Int): Int = u8(at) or (u8(at + 1) shl 8)

    companion object {
        const val SMART_AV = "smart_audio_video"
        const val VOICE_PROMPTS = "voice_prompts"
        const val BALANCE = "balance"
        const val VOICE_AWARE = "voice_aware"
        const val SAFE_SOUND = "safe_sound"
        const val SPATIAL = "spatial"
        const val SOUND_EFFECT = "sound_effect"
        const val AUDIO_LIMITER = "audio_limiter"
        const val SPEAK_TO_CHAT = "speak_to_chat"
        const val HI_RES = "hi_res"
        const val FIND_BUDS = "find_buds"
        const val GESTURES = "gestures"
        const val ADAPTIVE_ANC = "adaptive_anc"
        const val LOW_VOLUME_EQ = "low_volume_eq"
        const val LOW_EQ_KEY = "jbl_low_volume_eq"
        const val AUTO_COMPENSATION = "auto_compensation"
        const val ADAPTIVE_KEY = "jbl_adaptive_anc"
        const val COMPENSATION_KEY = "jbl_auto_compensation"

        // Advanced noise cancelling on command 91 (AdvancedAncCmd): its sub-commands and the two types PairFree sets.
        private const val ADVANCED_SET = 0x20
        private const val ADVANCED_READ = 0x21
        private const val ADVANCED_RET = 0x22
        private const val ADVANCED_ADAPTIVE = 0x01
        private const val ADVANCED_AUTO_COMPENSATION = 0x08

        // The rows' keys.
        const val SMART_KEY = "jbl_smart_audio_video"
        const val PROMPTS_KEY = "voice_prompts"
        const val BALANCE_KEY = "balance"
        const val VOICE_AWARE_KEY = "jbl_voice_aware"
        const val SAFE_SOUND_KEY = "jbl_safe_sound"
        const val SPATIAL_KEY = "jbl_spatial"
        const val EFFECT_KEY = "jbl_call_effect"
        const val LIMITER_KEY = "jbl_audio_limiter"
        const val TALK_KEY = "jbl_speak_to_chat"
        const val HI_RES_KEY = "jbl_hi_res"
        const val FIND_KEY = "find"
        const val MUTE_LEFT = "find_mute_left"
        const val MUTE_RIGHT = "find_mute_right"
        const val OFF = "off"
        const val TOUCH_LEFT = "jbl_touch_left"
        const val TOUCH_RIGHT = "jbl_touch_right"
        private val TOUCH_KEYS = mapOf("left" to TOUCH_LEFT, "right" to TOUCH_RIGHT)

        // The touch sets (CustomGesture), the ones JBL's gesture page offers first.
        const val PLAYBACK = "playback"
        const val AMBIENT = "ambient"
        const val VOLUME = "volume"
        const val NONE = "none"
        const val OTHER = "other"
        private val TOUCH_SETS = listOf(PLAYBACK, AMBIENT, VOLUME, NONE)

        // JBL's gesture ids for each side's tap, double tap, triple tap and hold, and their bytes on the wire
        // (GestureType's order through CmdBase.values).
        private val SIDE_GESTURES = mapOf("left" to listOf(1, 3, 7, 49), "right" to listOf(2, 4, 8, 50))
        private val ID_WIRE = mapOf(1 to 0x06, 3 to 0x07, 7 to 0x08, 49 to 0x0C, 2 to 0x09, 4 to 0x0A, 8 to 0x0B, 50 to 0x0E)
        private val WIRE_GESTURES = ID_WIRE.entries.associate { it.value to it.key }
        private const val ALL_GESTURES = 0xFF

        // Actions (GestureActionType by JBL's ids).
        private const val ACTION_VOLUME_UP = 1
        private const val ACTION_VOLUME_DOWN = 2
        private const val ACTION_TALK_THRU = 4
        private const val ACTION_NEXT = 5
        private const val ACTION_PREVIOUS = 6
        private const val ACTION_PLAY_PAUSE = 8
        private const val ACTION_PLAY_PAUSE_VA = 10
        private const val ACTION_SPORTS = 15
        private const val ACTION_ASSISTANT = 161
        // Noise cancelling and Ambient Aware, noise cancelling and ambient sound, noise cancelling and off, Ambient Aware and off.
        private val NOISE_CYCLES = setOf(9, 11, 12, 13)
        private val ASSISTANTS = setOf(160, 164, 166, 168)

        /** The feature records and protocol 4 features these settings are read from. */
        val FEATURE_IDS = setOf(JblFeature.SOUND_EFFECT, JblFeature.AUDIO_LIMITER, JblFeature.HI_RES, JblFeature.LOW_VOLUME_EQ)
        val P4_IDS = setOf(
            JblP4.FEATURE_SMART_SWITCH, JblP4.FEATURE_VOICE_PROMPT, JblP4.FEATURE_BALANCE, JblP4.FEATURE_SIDETONE,
            JblP4.FEATURE_MAX_VOLUME_LIMIT, JblP4.FEATURE_SPATIAL_SCENE, JblP4.FEATURE_SOUND_EFFECT, JblP4.FEATURE_AUDIO_LIMITER,
            JblP4.FEATURE_SMART_TALK, JblP4.FEATURE_HI_RES, JblP4.FEATURE_ADAPTIVE_ANC, JblP4.FEATURE_AUTO_COMPENSATION,
            JblP4.FEATURE_LOW_VOLUME_EQ,
        )

        private val AA_COMMANDS = setOf(
            JblCommand.SMART_SWITCH, JblCommand.VOICE_PROMPTS, JblCommand.BALANCE, JblCommand.VOICE_AWARE,
            JblCommand.SAFE_SOUND, JblCommand.SPATIAL, JblCommand.SPEAK_TO_CHAT, JblCommand.GESTURES, JblCommand.LOW_VOLUME_EQ,
        )

        // The sub-commands of these Harman commands (CmdBase): 00 set, 01 read, 02 reply; voice prompts 03, 04 and 05.
        private const val SET = 0
        private const val READ = 1
        private const val RET = 2
        private const val PROMPT_SET = 3
        private const val PROMPT_READ = 4
        private const val PROMPT_RET = 5

        // Smart Audio & Video's modes, as JBL's app names them (f0: normal, music, video), and the empty latency.
        private val SMART_ORDER = listOf("normal", "audio", "video")
        private val SMART_NAMES = mapOf("normal" to "standard", "audio" to "audio", "video" to "video")
        private const val NO_LATENCY = 0xFFFF

        // Balance: 84 (left) to 116 (right), 100 in the middle (LeftRightBalanceCard); its record ids.
        private const val BALANCE_MIN = 84
        private const val BALANCE_MAX = 116
        private const val BALANCE_MIDDLE = 100
        private const val P4_BALANCE_OFFSET = 50
        private const val BALANCE_STATUS = 1
        private const val BALANCE_VALUE = 2
        private const val SAFE_SOUND_STATUS = 1

        // VoiceAware's levels (VoiceAwareCard).
        private const val LEVEL_MID = 2
        private val LEVELS = mapOf(1 to "low", LEVEL_MID to "mid", 3 to "high")

        // 3D spatial sound's types (Spatial3DCard), and protocol 4's numbers for them.
        private const val MOVIE = 2
        private val SPATIAL_TYPES = mapOf(1 to "music", MOVIE to "movie", 3 to "game")
        private val P4_SPATIAL = mapOf(1 to 2, MOVIE to 1, 3 to 3)

        // The sound effect's types (SoundEffectType: Standard, TrebleBoost, BassBoost), by the default list.
        private val DEFAULT_EFFECTS = listOf("standard", "treble", "bass")

        // Speak to chat: the seconds before the music comes back (SpeakToChatCard).
        private val TALK_SECONDS = listOf(5, 15, 20)

        // Command 36's values (CmdBase.values_ear_beeping by BesBeepingType).
        private const val STOP_RIGHT = 0x00
        private const val STOP_LEFT = 0x01
        private const val BEEP_RIGHT = 0x10
        private const val BEEP_LEFT = 0x11
    }
}
