package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.ProcessedMusicXmlDto;
import edu.cit.capstone.voxsight.service.MusicXmlProcessingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.io.File;
import java.nio.file.Files;
import java.util.List;

@RestController
@RequestMapping("/musicxml")
public class MusicXmlController {

    private final MusicXmlProcessingService processingService;
    private final String outputsDir;

    @Autowired
    public MusicXmlController(MusicXmlProcessingService processingService) {
        this.processingService = processingService;
        // Assuming the application is started from the project root where 'outputs' folder exists
        String userDir = System.getProperty("user.dir");
        this.outputsDir = new File(userDir, "outputs").getAbsolutePath();
    }

    @GetMapping("/{id}/processed")
    public ResponseEntity<ProcessedMusicXmlDto> getProcessed(@PathVariable String id) {
        File tempXmlFile = null;
        try {
            // Locate the MusicXML file – check .xml, .mxl, and .musicxml
            File xmlFile = new File(outputsDir, id + ".xml");
            if (!xmlFile.exists()) {
                File mxlFile = new File(outputsDir, id + ".mxl");
                if (mxlFile.exists()) {
                    byte[] xmlBytes = extractXmlFromMxl(mxlFile);
                    tempXmlFile = File.createTempFile("voxsight-", ".xml");
                    Files.write(tempXmlFile.toPath(), xmlBytes);
                    xmlFile = tempXmlFile;
                } else {
                    File musicXmlFile = new File(outputsDir, id + ".musicxml");
                    if (musicXmlFile.exists()) {
                        xmlFile = musicXmlFile;
                    } else {
                        return ResponseEntity.notFound().build();
                    }
                }
            }

            // Read raw XML content safely
            byte[] xmlBytes = Files.readAllBytes(xmlFile.toPath());
            String rawXml = new String(xmlBytes, java.nio.charset.StandardCharsets.UTF_8);
            // Parse notes
            List<edu.cit.capstone.voxsight.model.NoteModel> notes = processingService.parseMusicXml(xmlFile);
            // SATB classification
            edu.cit.capstone.voxsight.model.SatbModel satb = processingService.classifySatb(notes);
            // Tempo extraction (fallback default inside service)
            int tempo = processingService.extractTempo(xmlFile);
            // Build DTO
            ProcessedMusicXmlDto dto = new ProcessedMusicXmlDto(rawXml, satb, tempo);
            return ResponseEntity.ok(dto);
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.status(500).build();
        } finally {
            if (tempXmlFile != null && tempXmlFile.exists()) {
                tempXmlFile.delete();
            }
        }
    }

    private byte[] extractXmlFromMxl(File mxlFile) throws java.io.IOException {
        try (java.util.zip.ZipFile zipFile = new java.util.zip.ZipFile(mxlFile)) {
            java.util.Enumeration<? extends java.util.zip.ZipEntry> entries = zipFile.entries();
            while (entries.hasMoreElements()) {
                java.util.zip.ZipEntry entry = entries.nextElement();
                if (entry.getName().endsWith(".xml") && !entry.getName().equals("META-INF/container.xml")) {
                    try (java.io.InputStream is = zipFile.getInputStream(entry)) {
                        return is.readAllBytes();
                    }
                }
            }
        }
        throw new java.io.IOException("No valid XML file found in MXL container");
    }
}
