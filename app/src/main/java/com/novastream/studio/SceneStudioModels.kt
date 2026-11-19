package com.novastream.studio

import org.json.JSONArray
import org.json.JSONObject

data class SceneDraft(
    val caption: String = "",
    val secondCaption: String = "",
    val thirdCaption: String = "",
    val chroma: Boolean = false,
    val visualFilter: String = "Normal",
    val sensitivity: Float = 0.72f,
    val imagePlacements: List<ImageLayerPlacement> = emptyList(),
    val imagePaths: List<String> = emptyList(),
    val videoPath: String = ""
)

data class ImageLayerPlacement(
    val x: Float = 5f,
    val y: Float = 5f,
    val scale: Float = 22f
)

object SceneDraftStore {
    fun decode(raw: String): MutableMap<String, SceneDraft> {
        val result = mutableMapOf<String, SceneDraft>()
        if (raw.isBlank()) return result
        return try {
            val root = JSONObject(raw)
            val keys = root.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                val value = root.optJSONObject(key) ?: continue
                result[key] = SceneDraft(
                    caption = value.optString("caption", ""),
                    secondCaption = value.optString("secondCaption", ""),
                    thirdCaption = value.optString("thirdCaption", ""),
                    chroma = value.optBoolean("chroma", false),
                    visualFilter = value.optString("visualFilter", "Normal"),
                    sensitivity = value.optDouble("sensitivity", 0.72).toFloat().coerceIn(0.1f, 1f),
                    imagePlacements = decodePlacements(value.optJSONArray("imagePlacements")?.toString() ?: ""),
                    imagePaths = decodeStrings(value.optJSONArray("imagePaths")),
                    videoPath = value.optString("videoPath", "")
                )
            }
            result
        } catch (_: Exception) {
            mutableMapOf()
        }
    }

    fun encode(drafts: Map<String, SceneDraft>): String {
        val root = JSONObject()
        drafts.forEach { (name, draft) ->
            root.put(name, JSONObject()
                .put("caption", draft.caption)
                .put("secondCaption", draft.secondCaption)
                .put("thirdCaption", draft.thirdCaption)
                .put("chroma", draft.chroma)
                .put("visualFilter", draft.visualFilter)
                .put("sensitivity", draft.sensitivity.toDouble())
                .put("imagePlacements", JSONArray(encodePlacements(draft.imagePlacements)))
                .put("imagePaths", JSONArray().apply { draft.imagePaths.take(5).forEach { put(it) } })
                .put("videoPath", draft.videoPath))
        }
        return root.toString()
    }

    fun encodePaths(paths: List<String>): String {
        val array = JSONArray()
        paths.take(5).forEach { path -> if (path.isNotBlank()) array.put(path) }
        return array.toString()
    }

    fun decodePaths(raw: String): List<String> {
        if (raw.isBlank()) return emptyList()
        return try {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until minOf(array.length(), 5)) {
                    val path = array.optString(index, "")
                    if (path.isNotBlank()) add(path)
                }
            }
        } catch (_: Exception) {
            emptyList()
        }
    }

    private fun decodeStrings(array: JSONArray?): List<String> {
        if (array == null) return emptyList()
        return buildList {
            for (index in 0 until minOf(array.length(), 5)) {
                val path = array.optString(index, "")
                if (path.isNotBlank()) add(path)
            }
        }
    }

    fun encodePlacements(placements: List<ImageLayerPlacement>): String {
        val array = JSONArray()
        placements.forEach { placement ->
            array.put(JSONObject()
                .put("x", placement.x.toDouble())
                .put("y", placement.y.toDouble())
                .put("scale", placement.scale.toDouble()))
        }
        return array.toString()
    }

    fun decodePlacements(raw: String): MutableList<ImageLayerPlacement> {
        val result = mutableListOf<ImageLayerPlacement>()
        if (raw.isBlank()) return result
        return try {
            val array = JSONArray(raw)
            for (index in 0 until array.length()) {
                val item = array.optJSONObject(index) ?: continue
                result.add(ImageLayerPlacement(
                    x = item.optDouble("x", 5.0).toFloat().coerceIn(0f, 100f),
                    y = item.optDouble("y", 5.0).toFloat().coerceIn(0f, 100f),
                    scale = item.optDouble("scale", 22.0).toFloat().coerceIn(5f, 60f)
                ))
            }
            result
        } catch (_: Exception) {
            mutableListOf()
        }
    }
}
