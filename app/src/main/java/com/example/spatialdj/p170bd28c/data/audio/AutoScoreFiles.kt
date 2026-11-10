package com.example.spatialdj.p170bd28c.data.audio

import com.example.spatialdj.p170bd28c.domain.model.*
import org.json.JSONObject

object AutoScoreFiles {
    fun read(text: String): AutoScore {
        val json=JSONObject(text)
        val steps=json.getJSONArray("steps")
        val volumes=json.getJSONArray("volumes")
        fun notes(array: org.json.JSONArray) = List(array.length()) {
            val note=array.getJSONObject(it)
            NoteEvent(note.getDouble("at"),note.getInt("pad"),note.optDouble("velocity",.8).toFloat())
        }
        return AutoScore(
            title=json.optString("title","自动舞曲"),
            bpm=json.getInt("bpm"),bars=json.getInt("bars"),master=json.getDouble("master").toFloat(),
            volumes=List(volumes.length()) { volumes.getDouble(it).toFloat() },
            steps=List(steps.length()) { index ->
                val step=steps.getJSONObject(index)
                AutoStep(step.getInt("lane"),notes(step.getJSONArray("notes")))
            },
            sections=json.optJSONArray("sections")?.let { sections -> List(sections.length()) {
                val section=sections.getJSONObject(it)
                val gains=section.getJSONArray("gains"); val lanes=section.getJSONArray("lanes")
                AutoSection(section.getString("title"),section.getInt("loops"),
                    List(gains.length()) { index -> gains.getDouble(index).toFloat() },
                    List(lanes.length()) { index -> notes(lanes.getJSONArray(index)) },
                    part=if(section.has("part")) SongPart.valueOf(section.getString("part")) else SongPart.Drop)
            } } ?: emptyList(),
        ).validate()
    }
}
