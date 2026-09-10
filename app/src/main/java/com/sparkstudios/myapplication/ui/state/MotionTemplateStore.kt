package com.sparkstudios.myapplication.ui

import android.content.Context
import io.motionguard.exercise.MotionFrame
import io.motionguard.exercise.MotionTemplate
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

class MotionTemplateStore(context: Context) {
    private val file = File(context.applicationContext.filesDir, "motion_templates.json")

    fun loadAll(): List<MotionTemplate> {
        if (!file.exists()) return emptyList()
        return runCatching {
            val root = JSONArray(file.readText())
            List(root.length()) { index -> root.getJSONObject(index).toTemplate() }
        }.getOrDefault(emptyList())
    }

    fun find(id: String): MotionTemplate? = loadAll().firstOrNull { it.id == id }

    fun save(template: MotionTemplate) {
        val templates = loadAll().filterNot { it.id == template.id } + template
        val root = JSONArray()
        templates.forEach { root.put(it.toJson()) }
        file.writeText(root.toString())
    }

    private fun JSONObject.toTemplate(): MotionTemplate {
        val framesJson = getJSONArray("frames")
        return MotionTemplate(
            id = getString("id"),
            name = getString("name"),
            durationMs = getLong("durationMs"),
            frames = List(framesJson.length()) { index -> framesJson.getJSONObject(index).toMotionFrame() },
        )
    }

    private fun JSONObject.toMotionFrame(): MotionFrame =
        MotionFrame(
            timestamp = getLong("timestamp"),
            normalizedKeypoints = getJSONArray("normalizedKeypoints").toFloatArray(),
            jointAngles = getJSONArray("jointAngles").toFloatArray(),
        )

    private fun MotionTemplate.toJson(): JSONObject =
        JSONObject()
            .put("id", id)
            .put("name", name)
            .put("durationMs", durationMs)
            .put("frames", JSONArray().also { framesArray ->
                frames.forEach { framesArray.put(it.toJson()) }
            })

    private fun MotionFrame.toJson(): JSONObject =
        JSONObject()
            .put("timestamp", timestamp)
            .put("normalizedKeypoints", normalizedKeypoints.toJsonArray())
            .put("jointAngles", jointAngles.toJsonArray())

    private fun JSONArray.toFloatArray(): FloatArray =
        FloatArray(length()) { index -> getDouble(index).toFloat() }

    private fun FloatArray.toJsonArray(): JSONArray =
        JSONArray().also { array -> forEach { array.put(it.toDouble()) } }
}
