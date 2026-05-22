package edu.cit.capstone.voxsight.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import edu.cit.capstone.voxsight.model.SatbModel;

public class ProcessedMusicXmlDto {
    @JsonProperty("musicxml")
    private String rawXml;
    @JsonProperty("satb")
    private SatbModel satb;
    @JsonProperty("tempo")
    private int tempo;

    public ProcessedMusicXmlDto() {}

    public ProcessedMusicXmlDto(String rawXml, SatbModel satb, int tempo) {
        this.rawXml = rawXml;
        this.satb = satb;
        this.tempo = tempo;
    }

    public String getRawXml() {
        return rawXml;
    }

    public void setRawXml(String rawXml) {
        this.rawXml = rawXml;
    }

    public SatbModel getSatb() {
        return satb;
    }

    public void setSatb(SatbModel satb) {
        this.satb = satb;
    }

    public int getTempo() {
        return tempo;
    }

    public void setTempo(int tempo) {
        this.tempo = tempo;
    }
}
