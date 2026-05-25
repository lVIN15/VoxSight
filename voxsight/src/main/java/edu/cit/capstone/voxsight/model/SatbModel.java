package edu.cit.capstone.voxsight.model;

import com.fasterxml.jackson.annotation.JsonAnyGetter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SatbModel {
    private Map<String, List<NoteModel>> parts = new HashMap<>();

    public SatbModel() {
        parts.put("S", new ArrayList<>());
        parts.put("A", new ArrayList<>());
        parts.put("T", new ArrayList<>());
        parts.put("B", new ArrayList<>());
    }

    @JsonAnyGetter
    public Map<String, List<NoteModel>> getParts() {
        return parts;
    }

    public void addNote(String part, NoteModel note) {
        parts.computeIfAbsent(part, k -> new ArrayList<>()).add(note);
    }
}
