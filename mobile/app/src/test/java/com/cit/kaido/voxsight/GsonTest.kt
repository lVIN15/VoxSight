import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.cit.kaido.voxsight.model.MusicalEvent

fun main() {
    val json = """[{"event_id":"t11040-p1-c0","measure_number":4,"tick_position":11040,"ticks_per_quarter":960,"pitch_midi":62,"pitch_name":"D4","duration_ticks":960,"duration_quarters":1.0,"voice_source":0,"staff_id":1,"part_id":1,"is_rest":false,"is_chord_member":false,"tie_type":null,"playback_track":"p1-s1","satb_voice":"A","satb_confidence":0.6685714285714286,"schema_version":"1.0"}]"""
    
    val gson = Gson()
    val eventType = object : TypeToken<List<MusicalEvent>>() {}.type
    val events: List<MusicalEvent> = gson.fromJson(json, eventType)
    println("Events parsed: ${events.size}")
}
