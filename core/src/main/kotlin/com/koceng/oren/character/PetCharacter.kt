package com.koceng.oren.character

import com.badlogic.gdx.Gdx
import com.badlogic.gdx.graphics.g3d.model.Node
import com.badlogic.gdx.math.Matrix4
import com.badlogic.gdx.math.Quaternion
import com.badlogic.gdx.math.MathUtils
import com.badlogic.gdx.graphics.g3d.utils.AnimationController
import com.badlogic.gdx.math.Vector3
import com.badlogic.gdx.math.collision.BoundingBox
import com.badlogic.gdx.utils.Disposable
import net.mgsx.gltf.loaders.glb.GLBLoader
import net.mgsx.gltf.scene3d.scene.Scene
import net.mgsx.gltf.scene3d.scene.SceneAsset
import java.util.IdentityHashMap
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.sin

enum class PetState {
    IDLE,
    LISTENING,
    THINKING, // Mikir: kepala miring, alis naik, mata melirik
    TALKING,
    DIZZY,    // Reaksi kepala dipukul/disentuh (Pusing)
    LAUGHING, // Reaksi perut dielus/disentuh (Tertawa)
    EATING,   // Reaksi makan
    SLEEPING  // Mode tidur
}

enum class HitZone {
    HEAD,
    BELLY,
    NONE
}

/** Pose penting sebuah node sebelum modifikasi prosedural diterapkan. */
private data class StoredPose(val localTransform: Matrix4 = Matrix4())

class PetCharacter(glbFilePath: String = "models/cartoon_cat.glb") : Disposable {

    val sceneAsset: SceneAsset = GLBLoader().load(Gdx.files.internal(glbFilePath))
    val scene: Scene = Scene(sceneAsset.scene)

    var currentState: PetState = PetState.IDLE
        private set

    val fullModelBounds = BoundingBox()
    val localHeadBox = BoundingBox()
    val localBellyBox = BoundingBox()

    val worldHeadBox = BoundingBox()
    val worldBellyBox = BoundingBox()

    private val animIdleName: String
    private val animListeningName: String
    private val animTalkingName: String
    private val animDizzyName: String
    private val animLaughName: String
    private val animEatingName: String
    private val animSleepingName: String

    private var stateTime: Float = 0f
    private var dizzyTimer: Float = 0f
    private var laughTimer: Float = 0f
    private var eatTimer: Float = 0f
    private var talkBlend: Float = 0f

    private val headNodes = mutableListOf<Node>()
    private val jawNodes = mutableListOf<Node>()
    private val leftArmNodes = mutableListOf<Node>()
    private val rightArmNodes = mutableListOf<Node>()
    private val browNodes = mutableListOf<Node>()
    private val lidUpNodes = mutableListOf<Node>()
    private val lidDownNodes = mutableListOf<Node>()
    private val focusNodes = mutableListOf<Node>()
    private val earNodes = mutableListOf<Node>()
    private val weaponNodes = mutableListOf<Node>()

    private val poseNodes = mutableListOf<Node>()
    private val poseBase = IdentityHashMap<Node, StoredPose>()

    private var blinkTimer = 2.5f
    private var blinkRemaining = 0f

    private val reactionListener = object : AnimationController.AnimationListener {
        override fun onEnd(animation: AnimationController.AnimationDesc?) {
            if (currentState != PetState.SLEEPING) {
                playIdle()
            }
        }

        override fun onLoop(animation: AnimationController.AnimationDesc?) {}
    }

    init {
        scene.modelInstance.transform.idt()
        scene.modelInstance.calculateBoundingBox(fullModelBounds)
        val min = fullModelBounds.min
        val max = fullModelBounds.max
        val height = max.y - min.y
        val width = max.x - min.x
        val depth = max.z - min.z

        Gdx.app.log("PetCharacter", "Model Bounds: min=$min, max=$max, height=$height")

        localHeadBox.set(
            Vector3(min.x + width * 0.15f, min.y + height * 0.65f, min.z + depth * 0.15f),
            Vector3(max.x - width * 0.15f, max.y, max.z - depth * 0.15f)
        )

        localBellyBox.set(
            Vector3(min.x + width * 0.2f, min.y + height * 0.30f, min.z + depth * 0.2f),
            Vector3(max.x - width * 0.2f, min.y + height * 0.65f, max.z - depth * 0.2f)
        )

        updateWorldBounds()

        val animMap = scene.modelInstance.animations.map { it.id }.toSet()
        Gdx.app.log("PetCharacter", "Daftar animasi di GLB: $animMap")

        animIdleName = findAnimation(animMap, listOf("Idle_Harmonic"), listOf("harmonic"))
        animListeningName = findAnimation(animMap, listOf("Idle_Invasive", "Arm_Idle"), listOf("invasive"))
        animTalkingName = findAnimation(animMap, listOf("Idle_Harmonic"), listOf("harmonic"))
        animDizzyName = findAnimation(animMap, listOf("Arm_Hit"), listOf("hit"))
        animLaughName = findAnimation(animMap, listOf(), listOf("omical"))
        animEatingName = findAnimation(animMap, listOf("Idle_Harmonic"), listOf("harmonic"))
        animSleepingName = findAnimation(animMap, listOf("Idle_Dreamer"), listOf("dreamer"))

        collectNodes(scene.modelInstance.nodes)

        playIdle()
    }

    private fun collectNodes(nodes: Iterable<Node>) {
        for (node in nodes) {
            val id = node.id.lowercase()
            poseNodes.add(node)

            when {
                id.contains("weapon") || id.contains("sword") || id.contains("lapagun") ->
                    weaponNodes.add(node)

                id.contains("jaw") || id.contains("mouth") || id.contains("chin") ||
                        id.contains("lip") || id.contains("muzzle") || id.contains("snout") ->
                    jawNodes.add(node)

                id.contains("head") || id.contains("neck") || id.contains("neack") ->
                    headNodes.add(node)

                id.contains("arm") || id.contains("shoulder") || id.contains("sholdier") ||
                        id.contains("forearm") || id.contains("hand") || id.contains("clavicle") -> {
                    if (id.contains("left") || id.contains("_l") || id.contains(".l")) {
                        leftArmNodes.add(node)
                    } else if (id.contains("right") || id.contains("_r") || id.contains(".r")) {
                        rightArmNodes.add(node)
                    }
                }

                id.startsWith("brov") -> browNodes.add(node)
                id.contains("veko_up") -> lidUpNodes.add(node)
                id.contains("veko_dwn") -> lidDownNodes.add(node)
                id.contains("eyes_fokus") -> focusNodes.add(node)
                id.startsWith("aye_") -> earNodes.add(node)
            }

            if (node.hasChildren()) {
                collectNodes(node.children)
            }
        }
    }

    private fun findAnimation(
        available: Set<String>,
        exact: List<String>,
        contains: List<String> = emptyList()
    ): String {
        for (name in exact) {
            if (available.contains(name)) return name
        }
        for (needle in contains) {
            for (name in available) {
                if (name.lowercase().contains(needle)) return name
            }
        }
        return ""
    }

    fun updateWorldBounds() {
        val transform = scene.modelInstance.transform
        worldHeadBox.set(localHeadBox).mul(transform)
        worldBellyBox.set(localBellyBox).mul(transform)
    }

    private fun restorePoses() {
        for (node in poseNodes) {
            val base = poseBase[node] ?: continue
            node.localTransform.set(base.localTransform)
        }
    }

    private fun capturePoses() {
        for (node in poseNodes) {
            val pose = poseBase.getOrPut(node) { StoredPose() }
            pose.localTransform.set(node.localTransform)
        }
    }

    fun update(delta: Float) {
        stateTime += delta

        if (dizzyTimer > 0f) {
            dizzyTimer -= delta
            if (dizzyTimer <= 0f && currentState == PetState.DIZZY) playIdle()
        }
        if (laughTimer > 0f) {
            laughTimer -= delta
            if (laughTimer <= 0f && currentState == PetState.LAUGHING) playIdle()
        }
        if (eatTimer > 0f) {
            eatTimer -= delta
            if (eatTimer <= 0f && currentState == PetState.EATING) playIdle()
        }

        restorePoses()
        scene.animationController.update(delta)
        capturePoses()

        applyExpressions(delta)

        talkBlend = MathUtils.lerp(
            talkBlend,
            if (currentState == PetState.TALKING) 1f else 0f,
            delta * 7.5f
        )
        if (talkBlend > 0.01f) {
            applyTalkingUpperBodyGestures()
        }

        applyProceduralAnimation()

        // Sembunyikan senjata tiap frame (clip tertentu mengubah scale node ini)
        for (node in weaponNodes) {
            node.localTransform.idt().scale(0f, 0f, 0f)
        }

        // Pastikan semua modifikasi localTransform (mulut, alis, kedip, dll.)
        // benar-benar dipakai renderer setelah root transform berubah.
        scene.modelInstance.calculateTransforms()

        updateWorldBounds()
    }

    private fun applyExpressions(delta: Float) {
        // --- Kedip ---
        blinkTimer -= delta
        var lidScale = 1f
        if (blinkRemaining > 0f) {
            blinkRemaining -= delta
            lidScale = 0.22f
        } else if (blinkTimer <= 0f) {
            blinkRemaining = 0.11f
            blinkTimer = 2.6f + MathUtils.random(0f, 1.8f)
            lidScale = 0.22f
        } else {
            lidScale = when (currentState) {
                PetState.LAUGHING -> 0.55f
                PetState.DIZZY -> 0.70f
                PetState.SLEEPING -> 0.12f
                else -> 1f
            }
        }
        for (node in lidUpNodes + lidDownNodes) {
            val base = poseBase[node]?.localTransform ?: continue
            node.localTransform.set(base).scale(1f, lidScale, 1f)
        }

        // --- Alis: naik saat menyimak/mikir, turun saat pusing ---
        val browLift = when (currentState) {
            PetState.LISTENING, PetState.THINKING -> 0.018f
            PetState.TALKING -> 0.008f
            PetState.LAUGHING -> 0.012f
            PetState.DIZZY -> -0.014f
            else -> 0f
        }
        for ((index, node) in browNodes.withIndex()) {
            val base = poseBase[node]?.localTransform ?: continue
            val lift = if (currentState == PetState.THINKING && index % 2 == 0) {
                browLift * 1.7f
            } else {
                browLift
            }
            node.localTransform.set(base).translate(0f, lift, 0f)
        }

        // --- Telinga tegak saat fokus ---
        val earTilt = if (currentState == PetState.LISTENING || currentState == PetState.THINKING) {
            -7f
        } else {
            0f
        }
        for (node in earNodes) {
            val base = poseBase[node]?.localTransform ?: continue
            node.localTransform.set(base).rotate(Vector3.Z, earTilt)
        }

        // --- Mata melirik saat mikir ---
        val focusX = if (currentState == PetState.THINKING) {
            MathUtils.sin(stateTime * 2.2f) * 0.025f
        } else {
            0f
        }
        for (node in focusNodes) {
            val base = poseBase[node]?.localTransform ?: continue
            node.localTransform.set(base).translate(focusX, 0f, 0f)
        }
    }

    private fun applyTalkingUpperBodyGestures() {
        val speechPulse =
            (abs(sin(stateTime * 9.5f)) * 0.65f + abs(sin(stateTime * 15.2f)) * 0.35f) * talkBlend

        val headPitch = (sin(stateTime * 5.2f) * 2.2f + speechPulse * 2.0f) * talkBlend
        val headYaw = sin(stateTime * 2.3f) * 3.8f * talkBlend
        val headRoll = cos(stateTime * 1.9f) * 2.2f * talkBlend
        for (node in headNodes) {
            val base = poseBase[node]?.localTransform ?: continue
            node.localTransform.set(base)
                .rotate(Vector3.X, headPitch)
                .rotate(Vector3.Y, headYaw)
                .rotate(Vector3.Z, headRoll)
        }

        // Rahang: buka-tutup mengikuti pulse suara (rotasi sekitar X)
        val jawOpen = speechPulse * 35f
        for (node in jawNodes) {
            val base = poseBase[node]?.localTransform ?: continue
            node.localTransform.set(base)
                .rotate(Vector3.X, jawOpen)
                // Dorong sedikit ke depan supaya gerakan terbaca jelas
                .translate(0f, 0f, 0.03f * speechPulse)
        }

        val leftArmPitch = sin(stateTime * 3.1f) * 8.5f * talkBlend
        val leftArmRoll = cos(stateTime * 2.2f) * 5.0f * talkBlend
        for (node in leftArmNodes) {
            val base = poseBase[node]?.localTransform ?: continue
            node.localTransform.set(base)
                .rotate(Vector3.Y, leftArmPitch)
                .rotate(Vector3.Z, leftArmRoll)
        }

        val rightArmPitch = -sin(stateTime * 3.1f) * 8.5f * talkBlend
        val rightArmRoll = -cos(stateTime * 2.2f) * 5.0f * talkBlend
        for (node in rightArmNodes) {
            val base = poseBase[node]?.localTransform ?: continue
            node.localTransform.set(base)
                .rotate(Vector3.Y, rightArmPitch)
                .rotate(Vector3.Z, rightArmRoll)
        }

        scene.modelInstance.calculateTransforms()
    }

    private fun applyProceduralAnimation() {
        var posY = 0f
        var rotX = 0f
        var rotZ = 0f
        var scaleX = 1f
        var scaleY = 1f
        var scaleZ = 1f

        when (currentState) {
            PetState.IDLE -> {
                val breath = sin(stateTime * 3.2f)
                scaleY = 1.0f + 0.024f * breath
                scaleX = 1.0f - 0.012f * breath
                scaleZ = 1.0f - 0.012f * breath
            }

            PetState.LISTENING -> {
                rotX = 3.5f
                val tilt = sin(stateTime * 2.5f) * 2.0f
                rotZ = tilt
                scaleY = 1.02f
            }

            PetState.THINKING -> {
                rotX = -1.5f
                rotZ = sin(stateTime * 1.6f) * 2.2f
                scaleY = 1.0f + 0.01f * sin(stateTime * 2.4f)
            }

            PetState.TALKING -> {
                posY = 0f
                rotX = 0f
                rotZ = 0f
                scaleX = 1f
                scaleY = 1f
                scaleZ = 1f
            }

            PetState.DIZZY -> {
                val wobble = sin(stateTime * 18.0f) * 11.0f
                rotZ = wobble
                rotX = cos(stateTime * 14.0f) * 4.0f
                scaleY = 1.0f - 0.06f * abs(sin(stateTime * 18.0f))
                scaleX = 1.0f + 0.04f * abs(sin(stateTime * 18.0f))
                scaleZ = scaleX
            }

            PetState.LAUGHING -> {
                val hop = abs(sin(stateTime * 13.0f)) * 0.075f
                posY = hop
                rotZ = sin(stateTime * 9.0f) * 6.0f
                scaleY = 1.0f + 0.05f * sin(stateTime * 13.0f)
            }

            PetState.EATING -> {
                val chew = abs(sin(stateTime * 10.0f)) * 0.035f
                posY = chew
                rotX = sin(stateTime * 10.0f) * 5.5f
                scaleY = 1.0f + 0.03f * sin(stateTime * 10.0f)
            }

            PetState.SLEEPING -> {
                posY = -0.04f
                val sleepBreath = sin(stateTime * 1.6f)
                scaleY = 0.95f + 0.022f * sleepBreath
                scaleX = 1.03f - 0.011f * sleepBreath
                scaleZ = 1.03f - 0.011f * sleepBreath
                rotX = -5.0f
                rotZ = 2.0f
            }
        }

        scene.modelInstance.transform.idt()
            .translate(0f, posY, 0f)
            .rotate(Vector3.X, rotX)
            .rotate(Vector3.Z, rotZ)
            .scale(scaleX, scaleY, scaleZ)
    }

    fun playIdle() {
        currentState = PetState.IDLE
        if (animIdleName.isNotEmpty()) {
            scene.animationController.animate(animIdleName, -1, 1f, null, 0.25f)
        }
    }

    fun playListening() {
        if (currentState == PetState.DIZZY || currentState == PetState.LAUGHING ||
            currentState == PetState.SLEEPING
        ) return
        currentState = PetState.LISTENING
        if (animListeningName.isNotEmpty()) {
            scene.animationController.animate(animListeningName, -1, 1f, null, 0.2f)
        }
    }

    fun playThinking() {
        if (currentState == PetState.SLEEPING) wakeUp()
        currentState = PetState.THINKING
        if (animListeningName.isNotEmpty()) {
            scene.animationController.animate(animListeningName, -1, 0.9f, null, 0.2f)
        }
    }

    fun playTalking() {
        if (currentState == PetState.SLEEPING) return
        currentState = PetState.TALKING
        if (animTalkingName.isNotEmpty()) {
            scene.animationController.animate(animTalkingName, -1, 1f, null, 0.25f)
        }
    }

    fun playDizzy() {
        if (currentState == PetState.SLEEPING) wakeUp()
        currentState = PetState.DIZZY
        dizzyTimer = 1.0f
        if (animDizzyName.isNotEmpty()) {
            scene.animationController.animate(animDizzyName, 1, 1.3f, reactionListener, 0.1f)
        }
    }

    fun playLaughing() {
        if (currentState == PetState.SLEEPING) wakeUp()
        currentState = PetState.LAUGHING
        laughTimer = 1.2f
        if (animLaughName.isNotEmpty()) {
            scene.animationController.animate(animLaughName, 1, 1.5f, reactionListener, 0.1f)
        }
    }

    fun playEating() {
        if (currentState == PetState.SLEEPING) wakeUp()
        currentState = PetState.EATING
        eatTimer = 1.2f
        if (animEatingName.isNotEmpty()) {
            scene.animationController.animate(animEatingName, 1, 1.2f, reactionListener, 0.15f)
        }
    }

    fun playSleeping() {
        currentState = PetState.SLEEPING
        if (animSleepingName.isNotEmpty()) {
            scene.animationController.animate(animSleepingName, -1, 0.7f, null, 0.25f)
        }
    }

    fun wakeUp() {
        if (currentState == PetState.SLEEPING) {
            playIdle()
        }
    }

    override fun dispose() {
        sceneAsset.dispose()
    }
}
