package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.OmrAnalysisResponse;
import edu.cit.capstone.voxsight.dto.OmrResponse;
import edu.cit.capstone.voxsight.service.SatbAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class OmrController {

    private static final Logger log = LoggerFactory.getLogger(OmrController.class);

    // Concurrency lock: guarantees only 1 Audiveris conversion runs at a time to stay safely within 512MB RAM
    private static final Semaphore OMR_SEMAPHORE = new Semaphore(1, true);

    // In-memory cache for instant responses (0.05s) on repeated or shared song uploads
    private static final Map<String, OmrAnalysisResponse> ANALYSIS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, OmrResponse> CONVERT_CACHE = new ConcurrentHashMap<>();

    @Value("${audiveris.path:C:\\Program Files\\Audiveris\\Audiveris.exe}")
    private String audiverisPath;

    @Value("${python.path:python}")
    private String pythonPath;

    @Value("${tessdata.prefix:}")
    private String tessdataPrefix;

    @Value("${voxsight.storage.uploads-dir:}")
    private String configuredUploadsDir;

    @Value("${voxsight.storage.outputs-dir:}")
    private String configuredOutputsDir;

    private final SatbAnalysisService satbAnalysisService;

    public OmrController(SatbAnalysisService satbAnalysisService) {
        this.satbAnalysisService = satbAnalysisService;
    }

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return String.valueOf(java.util.Arrays.hashCode(data));
        }
    }

    private File getUploadsDir() {
        File dir;
        if (configuredUploadsDir != null && !configuredUploadsDir.isBlank()) {
            dir = new File(configuredUploadsDir);
        } else {
            String userDir = System.getProperty("user.dir");
            dir = new File(userDir, "uploads");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private File getOutputsDir() {
        File dir;
        if (configuredOutputsDir != null && !configuredOutputsDir.isBlank()) {
            dir = new File(configuredOutputsDir);
        } else {
            String userDir = System.getProperty("user.dir");
            dir = new File(userDir, "outputs");
        }
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return dir;
    }

    private String getResolvedAudiverisPath() {
        if (audiverisPath != null && !audiverisPath.isBlank()) {
            File specified = new File(audiverisPath);
            if (specified.exists()) {
                return specified.getAbsolutePath();
            }
            if (specified.getParentFile() != null) {
                File lowercaseFallback = new File(specified.getParentFile(), "audiveris");
                if (lowercaseFallback.exists()) {
                    return lowercaseFallback.getAbsolutePath();
                }
                File uppercaseFallback = new File(specified.getParentFile(), "Audiveris");
                if (uppercaseFallback.exists()) {
                    return uppercaseFallback.getAbsolutePath();
                }
            }
        }
        String[] candidates = {
            "/usr/bin/audiveris",
            "/usr/local/bin/audiveris",
            "/opt/audiveris/bin/Audiveris",
            "/opt/audiveris/bin/audiveris",
            "/opt/Audiveris/bin/Audiveris",
            "/opt/Audiveris/bin/audiveris"
        };
        for (String candidate : candidates) {
            File f = new File(candidate);
            if (f.exists()) {
                return f.getAbsolutePath();
            }
        }
        return (audiverisPath != null && !audiverisPath.isBlank()) ? audiverisPath : "audiveris";
    }

    private void configureTessdataEnvironment(ProcessBuilder pb) {
        pb.environment().put("JAVA_TOOL_OPTIONS", "-Djava.awt.headless=true -Xms64m -Xmx220m -XX:+UseSerialGC");
        pb.environment().put("JAVA_OPTS", "-Djava.awt.headless=true -Xms64m -Xmx220m -XX:+UseSerialGC");
        String resolvedTessdata = tessdataPrefix;
        if (resolvedTessdata == null || resolvedTessdata.isBlank() || !new File(resolvedTessdata).exists()) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                resolvedTessdata = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "AudiverisLtd" + File.separator + "audiveris" + File.separator + "config" + File.separator + "tessdata";
            } else {
                String[] linuxCandidates = {
                    "/usr/share/tesseract-ocr/5/tessdata",
                    "/usr/share/tesseract-ocr/4.00/tessdata",
                    "/usr/share/tesseract-ocr/tessdata",
                    "/usr/share/tessdata"
                };
                for (String candidate : linuxCandidates) {
                    if (new File(candidate).exists()) {
                        resolvedTessdata = candidate;
                        break;
                    }
                }
            }
        }
        if (resolvedTessdata != null && new File(resolvedTessdata).exists()) {
            pb.environment().put("TESSDATA_PREFIX", resolvedTessdata);
            log.info("[OMR Tessdata] Set TESSDATA_PREFIX to: {}", resolvedTessdata);
        }
    }

    /**
     * Sanitizes input filename to prevent path traversal attacks.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "uploaded_score";
        String cleanName = new File(filename).getName(); // Remove path prefixes
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_"); // Strip unsafe chars
        return cleanName.isBlank() ? "uploaded_score" : cleanName;
    }

    /**
     * Pre-processes uploaded PDFs into high-resolution (300 DPI) images
     * to guarantee crystal-sharp staff recognition and eliminate blurry errors.
     */
    private File prepareScoreForOmr(File inputFile, File uploadsDir) {
        String name = inputFile.getName().toLowerCase();
        if (name.endsWith(".pdf")) {
            String base = getBaseName(inputFile.getName());
            File outPrefix = new File(uploadsDir, base + "-page");
            try {
                ProcessBuilder pb = new ProcessBuilder(
                    "pdftoppm", "-png", "-r", "300", "-f", "1", "-l", "1",
                    inputFile.getAbsolutePath(), outPrefix.getAbsolutePath()
                );
                pb.redirectErrorStream(true);
                Process p = pb.start();
                int exit = p.waitFor();
                if (exit == 0) {
                    File candidate1 = new File(uploadsDir, base + "-page-1.png");
                    File candidate2 = new File(uploadsDir, base + "-page-01.png");
                    File candidate3 = new File(uploadsDir, base + "-page.png");
                    if (candidate1.exists()) {
                        log.info("[PDF Preprocessor] Successfully rendered PDF to 300 DPI PNG: {}", candidate1.getName());
                        return candidate1;
                    }
                    if (candidate2.exists()) {
                        log.info("[PDF Preprocessor] Successfully rendered PDF to 300 DPI PNG: {}", candidate2.getName());
                        return candidate2;
                    }
                    if (candidate3.exists()) {
                        log.info("[PDF Preprocessor] Successfully rendered PDF to 300 DPI PNG: {}", candidate3.getName());
                        return candidate3;
                    }
                }
            } catch (Exception e) {
                log.warn("[PDF Preprocessor] pdftoppm not available: {}. Falling back to Ghostscript / original PDF.", e.getMessage());
            }
        }
        return inputFile;
    }

    @PostMapping("/convert")
    public ResponseEntity<OmrResponse> convert(@RequestParam("musicFile") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(OmrResponse.ofError("File is empty"));
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            return ResponseEntity.badRequest().body(OmrResponse.ofError("Filename is missing"));
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrResponse.ofError("Failed to read uploaded file"));
        }

        // Check SHA-256 Cache for instant response (0.05s)
        String fileHash = calculateSha256(fileBytes);
        OmrResponse cached = CONVERT_CACHE.get(fileHash);
        if (cached != null) {
            log.info("[OMR Cache HIT] Returning cached conversion for: {} (hash: {})", rawFilename, fileHash);
            return ResponseEntity.ok(cached);
        }

        // Acquire Concurrency Lock (Ensures max 1 Audiveris conversion at a time to prevent RAM exhaustion)
        boolean acquired = false;
        try {
            acquired = OMR_SEMAPHORE.tryAcquire(120, TimeUnit.SECONDS);
            if (!acquired) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(OmrResponse.ofError("The server is currently processing other scores. Please try again in a moment."));
            }

            String originalFilename = sanitizeFilename(rawFilename);
            log.info("[Job Started] Processing {} with Audiveris (Lock Acquired)...", originalFilename);

            File uploadsDir = getUploadsDir();
            File outputsDir = getOutputsDir();

            String uniqueName = System.currentTimeMillis() + "-" + originalFilename;
            File uploadedFile = new File(uploadsDir, uniqueName);
            try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                fos.write(fileBytes);
            } catch (IOException e) {
                log.error("Failed to save uploaded file: ", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrResponse.ofError("Failed to save uploaded file: " + e.getMessage()));
            }

            String resolvedExecutable = getResolvedAudiverisPath();
            File execFile = new File(resolvedExecutable);
            log.info("[Audiveris Diagnostics] Executable path: '{}', exists: {}, canExecute: {}",
                    resolvedExecutable, execFile.exists(), execFile.canExecute());

            if (!execFile.exists() && resolvedExecutable.contains(File.separator)) {
                log.error("[Audiveris Error] Binary executable not found at specified path: {}", resolvedExecutable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrResponse.ofError("OMR processing service is temporarily unavailable. Missing executable component."));
            }

            File scoreToProcess = prepareScoreForOmr(uploadedFile, uploadsDir);

            StringBuilder audiverisLog = new StringBuilder();
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        resolvedExecutable,
                        "-batch",
                        "-export",
                        "-output",
                        uploadsDir.getAbsolutePath(),
                        scoreToProcess.getAbsolutePath()
                );
                configureTessdataEnvironment(pb);
                pb.redirectErrorStream(true);
                Process process = pb.start();

                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Exit forced. Failure")) {
                            continue;
                        }
                        log.info("[Audiveris Log] {}", line);
                        audiverisLog.append(line).append("\n");
                    }
                }

                int exitCode = process.waitFor();
                log.info("Audiveris finished with exit code: {}", exitCode);

            } catch (Exception e) {
                log.error("Error executing Audiveris process: ", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrResponse.ofError("Something went wrong while processing your file. Please try again."));
            }

            // Search for output files
            String baseName = getBaseName(uniqueName);
            File resultFile = locateOutputFile(baseName, uploadsDir);
            if (resultFile == null && !scoreToProcess.equals(uploadedFile)) {
                resultFile = locateOutputFile(getBaseName(scoreToProcess.getName()), uploadsDir);
            }

            if (resultFile != null && resultFile.exists()) {
                String extension = getExtension(resultFile.getName());
                File targetFile = new File(outputsDir, baseName + extension);

                try {
                    Files.move(resultFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("[Success] Moved {} to {}", resultFile.getName(), targetFile.getAbsolutePath());

                    cleanMusicXml(targetFile);

                    String fileUrl = "/outputs/" + baseName + extension;
                    OmrResponse response = OmrResponse.ofSuccess(fileUrl, baseName + extension);
                    CONVERT_CACHE.put(fileHash, response);
                    return ResponseEntity.ok(response);
                } catch (IOException e) {
                    log.error("Failed to move output file: ", e);
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(OmrResponse.ofError("The file was converted but couldn't be saved. Please try again."));
                }
            } else {
                String friendlyError = analyzeAudiverisLog(audiverisLog.toString());
                log.error("[Error] Audiveris finished but output not found for baseName: {}. Friendly error: {}", baseName, friendlyError);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrResponse.ofError(friendlyError));
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(OmrResponse.ofError("Server request was interrupted."));
        } finally {
            if (acquired) {
                OMR_SEMAPHORE.release();
            }
        }
    }

    /**
     * Enhanced endpoint: Audiveris OMR → SATB Analysis Pipeline with Concurrency Lock & Caching.
     */
    @PostMapping("/analyze")
    public ResponseEntity<OmrAnalysisResponse> analyze(@RequestParam("musicFile") MultipartFile file) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(OmrAnalysisResponse.ofError("File is empty"));
        }

        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            return ResponseEntity.badRequest().body(OmrAnalysisResponse.ofError("Filename is missing"));
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrAnalysisResponse.ofError("Failed to read uploaded file"));
        }

        // Check SHA-256 Cache for instant response (0.05s)
        String fileHash = calculateSha256(fileBytes);
        OmrAnalysisResponse cached = ANALYSIS_CACHE.get(fileHash);
        if (cached != null) {
            log.info("[OMR Cache HIT] Returning cached SATB analysis for: {} (hash: {})", rawFilename, fileHash);
            return ResponseEntity.ok(cached);
        }

        // Acquire Concurrency Lock (Ensures max 1 Audiveris conversion at a time to stay safely within 512MB RAM)
        boolean acquired = false;
        try {
            acquired = OMR_SEMAPHORE.tryAcquire(120, TimeUnit.SECONDS);
            if (!acquired) {
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(OmrAnalysisResponse.ofError("The server is currently processing other scores. Please try again in a moment."));
            }

            String originalFilename = sanitizeFilename(rawFilename);
            log.info("[Analyze] Starting full pipeline for: {} (Lock Acquired)", originalFilename);

            File uploadsDir = getUploadsDir();
            File outputsDir = getOutputsDir();

            // Step 1: Save uploaded file
            String uniqueName = System.currentTimeMillis() + "-" + originalFilename;
            File uploadedFile = new File(uploadsDir, uniqueName);
            try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                fos.write(fileBytes);
            } catch (IOException e) {
                log.error("Failed to save uploaded file: ", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrAnalysisResponse.ofError("Failed to save uploaded file"));
            }

            // Step 2: Run Audiveris
            String resolvedExecutable = getResolvedAudiverisPath();
            File execFile = new File(resolvedExecutable);
            log.info("[Analyze Audiveris Diagnostics] Executable path: '{}', exists: {}, canExecute: {}",
                    resolvedExecutable, execFile.exists(), execFile.canExecute());

            if (!execFile.exists() && resolvedExecutable.contains(File.separator)) {
                log.error("[Analyze Audiveris Error] Binary executable not found at specified path: {}", resolvedExecutable);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrAnalysisResponse.ofError("OMR processing service is temporarily unavailable. Missing executable component."));
            }

            File scoreToProcess = prepareScoreForOmr(uploadedFile, uploadsDir);

            StringBuilder audiverisLog = new StringBuilder();
            try {
                ProcessBuilder pb = new ProcessBuilder(
                        resolvedExecutable, "-batch", "-export",
                        "-output", uploadsDir.getAbsolutePath(), scoreToProcess.getAbsolutePath()
                );
                configureTessdataEnvironment(pb);
                pb.redirectErrorStream(true);
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Exit forced. Failure")) {
                            continue;
                        }
                        log.info("[Audiveris] {}", line);
                        audiverisLog.append(line).append("\n");
                    }
                }
                int exitCode = process.waitFor();
                log.info("[Analyze Audiveris Exit Code] {}", exitCode);
            } catch (Exception e) {
                log.error("Error executing Audiveris: ", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrAnalysisResponse.ofError("Audiveris processing failed"));
            }

            // Step 3: Locate MusicXML output
            String baseName = getBaseName(uniqueName);
            File resultFile = locateOutputFile(baseName, uploadsDir);
            if (resultFile == null && !scoreToProcess.equals(uploadedFile)) {
                resultFile = locateOutputFile(getBaseName(scoreToProcess.getName()), uploadsDir);
            }

            if (resultFile == null || !resultFile.exists()) {
                String friendlyError = analyzeAudiverisLog(audiverisLog.toString());
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrAnalysisResponse.ofError(friendlyError));
            }

            // Step 4: Move to outputs folder
            String extension = getExtension(resultFile.getName());
            File targetFile = new File(outputsDir, baseName + extension);
            try {
                Files.move(resultFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
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

                OmrAnalysisResponse response = OmrAnalysisResponse.ofSuccess(
                        analysis.rawMusicXml(),
                        analysis.scoreMetadata(),
                        analysis.events(),
                        analysis.schemaVersion()
                );
                ANALYSIS_CACHE.put(fileHash, response);
                return ResponseEntity.ok(response);

            } catch (SatbAnalysisService.SatbAnalysisException e) {
                log.error("[Analyze] SATB analysis failed: {}", e.getMessage());
                try {
                    String rawXml = extractXmlContent(targetFile);
                    OmrAnalysisResponse response = OmrAnalysisResponse.ofSuccess(
                            rawXml, java.util.Map.of(
                                "structure_type", "UNCERTAIN",
                                "satb_confidence", 0.0,
                                "validation_passed", false
                            ), java.util.List.of(), "1.0"
                    );
                    ANALYSIS_CACHE.put(fileHash, response);
                    return ResponseEntity.ok(response);
                } catch (IOException ioe) {
                    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                            .body(OmrAnalysisResponse.ofError("Analysis failed and could not read MusicXML"));
                }
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(OmrAnalysisResponse.ofError("Server request was interrupted."));
        } finally {
            if (acquired) {
                OMR_SEMAPHORE.release();
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

    private File locateOutputFile(String baseName, File uploadsDir) {
        String[] possibleExtensions = {".mxl", ".musicxml", ".xml"};
        List<File> searchPaths = new ArrayList<>();

        for (String ext : possibleExtensions) {
            // Check direct file: uploads/<baseName><ext>
            searchPaths.add(new File(uploadsDir, baseName + ext));
            // Check nested folder: uploads/<baseName>/<baseName><ext>
            searchPaths.add(new File(new File(uploadsDir, baseName), baseName + ext));
        }

        for (File path : searchPaths) {
            log.debug("Checking path for OMR output: {}", path.getAbsolutePath());
            if (path.exists() && path.isFile()) {
                return path;
            }
        }

        // Also check if Audiveris created a folder matching baseName and wrote ANY .mxl or .xml file
        File subFolder = new File(uploadsDir, baseName);
        if (subFolder.exists() && subFolder.isDirectory()) {
            File[] files = subFolder.listFiles();
            if (files != null) {
                for (File f : files) {
                    String name = f.getName().toLowerCase();
                    if (name.endsWith(".mxl") || name.endsWith(".musicxml") || name.endsWith(".xml")) {
                        log.info("Found OMR output inside subfolder: {}", f.getAbsolutePath());
                        return f;
                    }
                }
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

    private String analyzeAudiverisLog(String logContent) {
        if (logContent == null || logContent.isEmpty()) {
            return "Something went wrong during conversion. Please try again with a different file.";
        }

        String lowerLog = logContent.toLowerCase();

        if (lowerLog.contains("too low interline") || lowerLog.contains("interline value")) {
            return "The image resolution is too low. Please use a clearer photo or a PDF (300+ DPI recommended).";
        }
        if (lowerLog.contains("flagged as invalid")) {
            return "We couldn't detect any sheet music staves in this image. Please make sure the image contains clear, printed sheet music.";
        }
        if (lowerLog.contains("could not export") || lowerLog.contains("error in export")) {
            return "The sheet music was partially read but couldn't be fully converted. Try uploading a higher quality image or a PDF.";
        }
        if (lowerLog.contains("no staves") || lowerLog.contains("no staff")) {
            return "No music staves were found in this image. Please upload an image that clearly shows printed sheet music with staff lines.";
        }
        if (lowerLog.contains("transcription did not complete")) {
            return "The sheet music conversion did not complete successfully. The image may be too blurry or contain non-standard notation.";
        }

        return "We couldn't process this sheet music. Please try uploading a clearer image (at least 300 DPI) or a PDF file.";
    }

    private void cleanMusicXml(File file) {
        if (file == null || !file.exists()) return;
        try {
            String userDir = System.getProperty("user.dir");
            File script = new File(userDir, "scripts/musicxml_cleaner.py");
            if (!script.exists()) {
                log.warn("[MusicXML Cleaner] Script not found at: {}", script.getAbsolutePath());
                return;
            }
            ProcessBuilder pb = new ProcessBuilder(pythonPath, script.getAbsolutePath(), file.getAbsolutePath());
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

