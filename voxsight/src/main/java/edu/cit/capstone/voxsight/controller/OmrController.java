package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.OmrAnalysisResponse;
import edu.cit.capstone.voxsight.dto.OmrResponse;
import edu.cit.capstone.voxsight.service.SatbAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")
public class OmrController {

    private static final Logger log = LoggerFactory.getLogger(OmrController.class);

    private final String uploadsDirPath;
    private final String outputsDirPath;
    private final String audiverisPath = "C:\\Program Files\\Audiveris\\Audiveris.exe";
    private final SatbAnalysisService satbAnalysisService;

    public OmrController(SatbAnalysisService satbAnalysisService) {
        this.satbAnalysisService = satbAnalysisService;
        String userDir = System.getProperty("user.dir");
        this.uploadsDirPath = new File(userDir, "uploads").getAbsolutePath();
        this.outputsDirPath = new File(userDir, "outputs").getAbsolutePath();

        // Ensure directories exist
        new File(uploadsDirPath).mkdirs();
        new File(outputsDirPath).mkdirs();
    }

    @PostMapping("/convert")
    public ResponseEntity<OmrResponse> convert(@RequestParam("musicFile") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(OmrResponse.ofError("File is empty"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(OmrResponse.ofError("Filename is missing"));
        }

        log.info("[Job Started] Processing {} with Audiveris...", originalFilename);

        // Save file to uploads/ with unique timestamp to prevent collisions
        String uniqueName = System.currentTimeMillis() + "-" + originalFilename.replaceAll("\\s+", "_");
        File uploadedFile = new File(uploadsDirPath, uniqueName);
        try {
            file.transferTo(uploadedFile);
        } catch (IOException e) {
            log.error("Failed to save uploaded file: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrResponse.ofError("Failed to save uploaded file: " + e.getMessage()));
        }

        // Run Audiveris
        StringBuilder audiverisLog = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    audiverisPath,
                    "-batch",
                    "-export",
                    "MusicXML",
                    "-output",
                    uploadsDirPath,
                    uploadedFile.getAbsolutePath()
            );
            String tessdataDir = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "AudiverisLtd" + File.separator + "audiveris" + File.separator + "config" + File.separator + "tessdata";
            pb.environment().put("TESSDATA_PREFIX", tessdataDir);
            pb.redirectErrorStream(true); // Merge stdout and stderr
            Process process = pb.start();

            // Read output logs in real-time and capture for error analysis
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Exit forced. Failure")) {
                        continue; // Misleading non-fatal warning on completion
                    }
                    log.info("[Audiveris Log] {}", line);
                    audiverisLog.append(line).append("\n");
                }
            }

            int exitCode = process.waitFor();
            log.info("Audiveris finished with exit code: {}", exitCode);
            // We ignore non-zero exit codes because Audiveris frequently returns warning codes even on success.

        } catch (Exception e) {
            log.error("Error executing Audiveris: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrResponse.ofError("Something went wrong while processing your file. Please try again."));
        }

        // Search for output files
        String baseName = getBaseName(uniqueName);
        File resultFile = locateOutputFile(baseName);

        if (resultFile != null && resultFile.exists()) {
            String extension = getExtension(resultFile.getName());
            File targetFile = new File(outputsDirPath, baseName + extension);

            try {
                // Move result to the outputs folder
                Files.move(resultFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                log.info("[Success] Moved {} to {}", resultFile.getName(), targetFile.getAbsolutePath());

                // Post-process MusicXML to merge duplicate/split parts and clean misplaced credits
                cleanMusicXml(targetFile);

                String fileUrl = "/outputs/" + baseName + extension;
                return ResponseEntity.ok(OmrResponse.ofSuccess(fileUrl, baseName + extension));
            } catch (IOException e) {
                log.error("Failed to move output file: ", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrResponse.ofError("The file was converted but couldn't be saved. Please try again."));
            }
        } else {
            // Analyze Audiveris log for user-friendly error
            String friendlyError = analyzeAudiverisLog(audiverisLog.toString());
            log.error("[Error] Audiveris finished but output not found for baseName: {}. Friendly error: {}", baseName, friendlyError);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrResponse.ofError(friendlyError));
        }
    }

    /**
     * Enhanced endpoint: Audiveris OMR → SATB Analysis Pipeline.
     *
     * Architecture Contract (v3.7):
     *   - Runs Audiveris to produce MusicXML
     *   - Passes MusicXML to satb_analyzer.py via SatbAnalysisService
     *   - Returns raw MusicXML + score_metadata + events[] (ORDER-FROZEN)
     *   - NEVER modifies MusicXML content
     */
    @PostMapping("/analyze")
    public ResponseEntity<OmrAnalysisResponse> analyze(@RequestParam("musicFile") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(OmrAnalysisResponse.ofError("File is empty"));
        }

        String originalFilename = file.getOriginalFilename();
        if (originalFilename == null) {
            return ResponseEntity.badRequest().body(OmrAnalysisResponse.ofError("Filename is missing"));
        }

        log.info("[Analyze] Starting full pipeline for: {}", originalFilename);

        // Step 1: Save uploaded file
        String uniqueName = System.currentTimeMillis() + "-" + originalFilename.replaceAll("\\s+", "_");
        File uploadedFile = new File(uploadsDirPath, uniqueName);
        try {
            file.transferTo(uploadedFile);
        } catch (IOException e) {
            log.error("Failed to save uploaded file: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrAnalysisResponse.ofError("Failed to save uploaded file"));
        }

        // Step 2: Run Audiveris
        StringBuilder audiverisLog = new StringBuilder();
        try {
            ProcessBuilder pb = new ProcessBuilder(
                    audiverisPath, "-batch", "-export", "MusicXML",
                    "-output", uploadsDirPath, uploadedFile.getAbsolutePath()
            );
            String tessdataDir = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "AudiverisLtd" + File.separator + "audiveris" + File.separator + "config" + File.separator + "tessdata";
            pb.environment().put("TESSDATA_PREFIX", tessdataDir);
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Exit forced. Failure")) {
                        continue; // Misleading non-fatal warning on completion
                    }
                    log.info("[Audiveris] {}", line);
                    audiverisLog.append(line).append("\n");
                }
            }
            process.waitFor();
        } catch (Exception e) {
            log.error("Error executing Audiveris: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrAnalysisResponse.ofError("Audiveris processing failed"));
        }

        // Step 3: Locate MusicXML output
        String baseName = getBaseName(uniqueName);
        File resultFile = locateOutputFile(baseName);

        if (resultFile == null || !resultFile.exists()) {
            String friendlyError = analyzeAudiverisLog(audiverisLog.toString());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrAnalysisResponse.ofError(friendlyError));
        }

        // Step 4: Move to outputs folder
        String extension = getExtension(resultFile.getName());
        File targetFile = new File(outputsDirPath, baseName + extension);
        try {
            Files.move(resultFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            // Post-process MusicXML to merge duplicate/split parts and clean misplaced credits
            cleanMusicXml(targetFile);
        } catch (IOException e) {
            log.error("Failed to move output: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrAnalysisResponse.ofError("Failed to save converted file"));
        }

        // Step 5: Run SATB Analysis (music21 via Python)
        try {
            SatbAnalysisService.SatbAnalysisResult analysis = satbAnalysisService.analyze(targetFile);

            log.info("[Analyze] Pipeline complete. Events: {}, Schema: {}",
                    analysis.events().size(), analysis.schemaVersion());

            return ResponseEntity.ok(OmrAnalysisResponse.ofSuccess(
                    analysis.rawMusicXml(),
                    analysis.scoreMetadata(),
                    analysis.events(),
                    analysis.schemaVersion()
            ));

        } catch (SatbAnalysisService.SatbAnalysisException e) {
            log.error("[Analyze] SATB analysis failed: {}", e.getMessage());
            // Fallback: return success with MusicXML but no analysis
            try {
                String rawXml = extractXmlContent(targetFile);
                return ResponseEntity.ok(OmrAnalysisResponse.ofSuccess(
                        rawXml, java.util.Map.of(
                            "structure_type", "UNCERTAIN",
                            "satb_confidence", 0.0,
                            "validation_passed", false
                        ), java.util.List.of(), "1.0"
                ));
            } catch (IOException ioe) {
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrAnalysisResponse.ofError("Analysis failed and could not read MusicXML"));
            }
        }
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
                        return new String(buffer.toByteArray(), java.nio.charset.StandardCharsets.UTF_8);
                    }
                }
            }
        }
        return new String(bytes, java.nio.charset.StandardCharsets.UTF_8);
    }


    private File locateOutputFile(String baseName) {
        String[] possibleExtensions = {".mxl", ".musicxml", ".xml"};
        List<File> searchPaths = new ArrayList<>();

        for (String ext : possibleExtensions) {
            // Check direct file: uploads/<baseName><ext>
            searchPaths.add(new File(uploadsDirPath, baseName + ext));
            // Check nested folder: uploads/<baseName>/<baseName><ext>
            searchPaths.add(new File(new File(uploadsDirPath, baseName), baseName + ext));
        }

        for (File path : searchPaths) {
            log.debug("Checking path for OMR output: {}", path.getAbsolutePath());
            if (path.exists() && path.isFile()) {
                return path;
            }
        }

        return null;
    }

    private String getBaseName(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? filename : filename.substring(0, dotIndex);
    }

    private String getExtension(String filename) {
        int dotIndex = filename.lastIndexOf('.');
        return (dotIndex == -1) ? "" : filename.substring(dotIndex);
    }

    /**
     * Analyzes Audiveris log output and returns a user-friendly error message
     * instead of raw technical details.
     */
    private String analyzeAudiverisLog(String logContent) {
        if (logContent == null || logContent.isEmpty()) {
            return "Something went wrong during conversion. Please try again with a different file.";
        }

        String lowerLog = logContent.toLowerCase();

        // Low resolution / interline detection failure
        if (lowerLog.contains("too low interline") || lowerLog.contains("interline value")) {
            return "The image resolution is too low. Please use a clearer photo or a PDF (300+ DPI recommended).";
        }

        // Sheet flagged as invalid
        if (lowerLog.contains("flagged as invalid")) {
            return "We couldn't detect any sheet music staves in this image. Please make sure the image contains clear, printed sheet music.";
        }

        // Export failed after partial processing
        if (lowerLog.contains("could not export") || lowerLog.contains("error in export")) {
            return "The sheet music was partially read but couldn't be fully converted. Try uploading a higher quality image or a PDF.";
        }

        // No staves found
        if (lowerLog.contains("no staves") || lowerLog.contains("no staff")) {
            return "No music staves were found in this image. Please upload an image that clearly shows printed sheet music with staff lines.";
        }

        // Transcription did not complete
        if (lowerLog.contains("transcription did not complete")) {
            return "The sheet music conversion did not complete successfully. The image may be too blurry or contain non-standard notation.";
        }

        // Default fallback
        return "We couldn't process this sheet music. Please try uploading a clearer image (at least 300 DPI) or a PDF file.";
    }

    /**
     * Invokes musicxml_cleaner.py to merge duplicate/split parts across systems
     * and strip misplaced footer/credit text from note lyrics.
     */
    private void cleanMusicXml(File file) {
        if (file == null || !file.exists()) return;
        try {
            String userDir = System.getProperty("user.dir");
            File script = new File(userDir, "scripts/musicxml_cleaner.py");
            if (!script.exists()) {
                log.warn("[MusicXML Cleaner] Script not found at: {}", script.getAbsolutePath());
                return;
            }
            ProcessBuilder pb = new ProcessBuilder("python", script.getAbsolutePath(), file.getAbsolutePath());
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.info("[MusicXML Cleaner] {}", line);
                }
            }
            p.waitFor(15, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[MusicXML Cleaner] Non-fatal error cleaning MusicXML: {}", e.getMessage());
        }
    }
}
