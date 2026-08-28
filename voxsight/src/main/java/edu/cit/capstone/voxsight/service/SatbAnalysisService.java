package edu.cit.capstone.voxsight.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * Orchestrates the Python music21 SATB analysis pipeline.
 *
 * Architecture Contract (v3.7):
 *   - Invokes satb_analyzer.py via ProcessBuilder
 *   - Reads JSON output from stdout
 *   - Returns raw MusicXML + normalized events (ORDER-FROZEN)
 *   - NEVER modifies the MusicXML content
 *
 * Pipeline Boundary Markers:
 *   PIPELINE_START_ANALYSIS → Python script start
 *   PIPELINE_FROZEN_KERNEL_START → chord_index assignment begins
 *   PIPELINE_FROZEN_KERNEL_END → events finalized
 *   PIPELINE_OUTPUT → JSON emitted
 */
@Service
public class SatbAnalysisService {

    private static final Logger log = LoggerFactory.getLogger(SatbAnalysisService.class);
    private static final int TIMEOUT_SECONDS = 120;
    private final ObjectMapper objectMapper = new ObjectMapper();

    /**
     * The path to the Python analyzer script, relative to the working directory.
     * This is set to the scripts/ folder in the voxsight backend root.
     */
    private final String scriptPath;

    public SatbAnalysisService() {
        // Resolve script path relative to working directory
        String userDir = System.getProperty("user.dir");
        this.scriptPath = new File(userDir, "scripts/satb_analyzer.py").getAbsolutePath();
    }

    /**
     * Analyze a MusicXML file and produce SATB metadata.
     *
     * @param musicXmlFile the raw MusicXML file from Audiveris
     * @return parsed analysis result containing score_metadata and events[]
     * @throws SatbAnalysisException if analysis fails
     */
    public SatbAnalysisResult analyze(File musicXmlFile) throws SatbAnalysisException {
        if (!musicXmlFile.exists()) {
            throw new SatbAnalysisException("MusicXML file not found: " + musicXmlFile.getAbsolutePath());
        }

        log.info("[SATB Analysis] Starting analysis for: {}", musicXmlFile.getName());

        try {
            // Run Python analyzer (which cleans and merges duplicate parts and lyrics)
            String jsonOutput = runPythonAnalyzer(musicXmlFile.getAbsolutePath());

            // Read cleaned MusicXML content safely, extracting from MXL if necessary
            String rawMusicXml = extractXmlContent(musicXmlFile);

            // Parse JSON output
            Map<String, Object> result = objectMapper.readValue(
                    jsonOutput, new TypeReference<Map<String, Object>>() {}
            );

            // Check for analysis error
            if (result.containsKey("error")) {
                String error = (String) result.get("error");
                log.warn("[SATB Analysis] Analyzer returned error: {}", error);
                throw new SatbAnalysisException("SATB analysis failed: " + error);
            }

            // Extract components
            String schemaVersion = (String) result.getOrDefault("schema_version", "1.0");

            @SuppressWarnings("unchecked")
            Map<String, Object> scoreMetadata = (Map<String, Object>) result.get("score_metadata");

            @SuppressWarnings("unchecked")
            List<Map<String, Object>> events = (List<Map<String, Object>>) result.get("events");

            if (events == null) events = List.of();
            if (scoreMetadata == null) scoreMetadata = Map.of();

            log.info("[SATB Analysis] Complete. {} events, confidence={}, type={}",
                    events.size(),
                    scoreMetadata.getOrDefault("satb_confidence", "?"),
                    scoreMetadata.getOrDefault("structure_type", "?"));

            return new SatbAnalysisResult(rawMusicXml, scoreMetadata, events, schemaVersion);

        } catch (SatbAnalysisException e) {
            throw e;
        } catch (Exception e) {
            log.error("[SATB Analysis] Unexpected error: ", e);
            throw new SatbAnalysisException("Analysis failed: " + e.getMessage(), e);
        }
    }

    /**
     * Execute the Python satb_analyzer.py script and capture its stdout.
     */
    private String runPythonAnalyzer(String xmlPath) throws SatbAnalysisException {
        try {
            ProcessBuilder pb = new ProcessBuilder("python", "-X", "utf8", scriptPath, xmlPath);
            pb.environment().put("PYTHONIOENCODING", "UTF-8");
            pb.environment().put("PYTHONUTF8", "1");
            pb.redirectErrorStream(false); // Keep stderr separate for logging

            Process process = pb.start();

            // Read stdout (JSON output)
            String stdout;
            try (InputStream is = process.getInputStream()) {
                stdout = new String(is.readAllBytes(), StandardCharsets.UTF_8);
            }

            // Read stderr (log messages)
            String stderr;
            try (InputStream es = process.getErrorStream()) {
                stderr = new String(es.readAllBytes(), StandardCharsets.UTF_8);
            }

            boolean finished = process.waitFor(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                throw new SatbAnalysisException("SATB analysis timed out after " + TIMEOUT_SECONDS + " seconds");
            }

            int exitCode = process.exitValue();

            // Log stderr output
            if (!stderr.isBlank()) {
                for (String line : stderr.split("\n")) {
                    log.info("[Python] {}", line.trim());
                }
            }

            if (exitCode != 0 && stdout.isBlank()) {
                throw new SatbAnalysisException("Python analyzer exited with code " + exitCode + ": " + stderr);
            }

            if (stdout.isBlank()) {
                throw new SatbAnalysisException("Python analyzer produced no output");
            }

            return stdout.trim();

        } catch (SatbAnalysisException e) {
            throw e;
        } catch (Exception e) {
            throw new SatbAnalysisException("Failed to run Python analyzer: " + e.getMessage(), e);
        }
    }

    /**
     * Immutable result from SATB analysis.
     * Events list is ORDER-FROZEN per Fix #40.
     */
    public record SatbAnalysisResult(
            String rawMusicXml,
            Map<String, Object> scoreMetadata,
            List<Map<String, Object>> events,  // ORDER-FROZEN — do not re-sort
            String schemaVersion
    ) {}

    /**
     * Exception for SATB analysis failures.
     */
    public static class SatbAnalysisException extends Exception {
        public SatbAnalysisException(String message) { super(message); }
        public SatbAnalysisException(String message, Throwable cause) { super(message, cause); }
    }

    private String extractXmlContent(File file) throws IOException {
        byte[] bytes = Files.readAllBytes(file.toPath());
        if (bytes.length >= 2 && bytes[0] == 0x50 && bytes[1] == 0x4B) { // ZIP signature
            try (java.util.zip.ZipInputStream zis = new java.util.zip.ZipInputStream(new ByteArrayInputStream(bytes))) {
                java.util.zip.ZipEntry entry;
                while ((entry = zis.getNextEntry()) != null) {
                    String name = entry.getName().toLowerCase();
                    if (!entry.isDirectory() && (name.endsWith(".xml") || name.endsWith(".musicxml"))) {
                        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
                        byte[] data = new byte[1024];
                        int count;
                        while ((count = zis.read(data, 0, data.length)) != -1) {
                            buffer.write(data, 0, count);
                        }
                        return new String(buffer.toByteArray(), StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
