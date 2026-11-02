package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.OmrAnalysisResponse;
import edu.cit.capstone.voxsight.dto.OmrJobStatusResponse;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Consumer;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import edu.cit.capstone.voxsight.dto.OmrResponse;
import edu.cit.capstone.voxsight.service.SatbAnalysisService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.annotation.PostConstruct;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

@RestController
@RequestMapping("/api")
public class OmrController {

    private static final Logger log = LoggerFactory.getLogger(OmrController.class);

    @Value("${omr.max-concurrent-jobs:2}")
    private int maxConcurrentJobs = 2;

    // Concurrency semaphore: Defaults to 2 concurrent conversions (safely fits in ~7GB on 8GB RAM host with zero OOM risk)
    private static Semaphore omrSemaphore = new Semaphore(2, true);

    // In-Flight Task Deduplication: Allows concurrent uploads of identical scores (e.g. 13 choir members) to share 1 conversion job
    private static final Map<String, CompletableFuture<ResponseEntity<OmrAnalysisResponse>>> IN_FLIGHT_ANALYSIS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<ResponseEntity<OmrResponse>>> IN_FLIGHT_CONVERT = new ConcurrentHashMap<>();

    // In-memory cache for instant responses (0.05s) on repeated or shared song uploads
    private static final Map<String, OmrAnalysisResponse> ANALYSIS_CACHE = new ConcurrentHashMap<>();
    private static final Map<String, OmrResponse> CONVERT_CACHE = new ConcurrentHashMap<>();

    // Asynchronous background job tracker for long-running / multi-page scores
    public static class OmrJobState {
        private final String jobId;
        private volatile String status; // "PROCESSING", "COMPLETED", "FAILED"
        private volatile int progressPercent;
        private volatile String phaseText;
        private volatile OmrAnalysisResponse result;
        private volatile String error;
        private final long createdAt;

        public OmrJobState(String jobId, int progressPercent, String phaseText) {
            this.jobId = jobId;
            this.status = "PROCESSING";
            this.progressPercent = progressPercent;
            this.phaseText = phaseText;
            this.createdAt = System.currentTimeMillis();
        }

        public synchronized void update(int progressPercent, String phaseText) {
            this.progressPercent = progressPercent;
            this.phaseText = phaseText;
        }

        public synchronized void complete(OmrAnalysisResponse result) {
            this.status = "COMPLETED";
            this.progressPercent = 100;
            this.phaseText = "Score ready!";
            this.result = result;
        }

        public synchronized void fail(String error) {
            this.status = "FAILED";
            this.error = error;
            this.phaseText = "Processing failed: " + error;
        }

        public OmrJobStatusResponse toResponse() {
            return new OmrJobStatusResponse(jobId, status, progressPercent, phaseText, result, error);
        }

        public long getCreatedAt() {
            return createdAt;
        }
    }

    private static final Map<String, OmrJobState> ACTIVE_ASYNC_JOBS = new ConcurrentHashMap<>();
    private final ExecutorService asyncJobExecutor = Executors.newFixedThreadPool(4);

    public record ProgressUpdate(int percent, String phaseText) {}


    private static volatile String lastOmrDiagnostics = "No OMR job executed yet.";

    @PostConstruct
    public void initSemaphore() {
        if (maxConcurrentJobs > 0 && maxConcurrentJobs != omrSemaphore.availablePermits()) {
            omrSemaphore = new Semaphore(maxConcurrentJobs, true);
            log.info("[OMR Concurrency] Configured OMR Semaphore with {} permits (fair FIFO)", maxConcurrentJobs);
        }
    }

    @GetMapping("/diagnostics/last-omr-log")
    public ResponseEntity<String> getLastOmrLog() {
        String queueStatus = "=== CONCURRENCY & QUEUE STATUS ===\n"
                + "Max Concurrent Permits: " + maxConcurrentJobs + "\n"
                + "Available Permits: " + omrSemaphore.availablePermits() + "\n"
                + "Queued Threads Waiting: " + omrSemaphore.getQueueLength() + "\n"
                + "In-Flight Deduplicated Analysis Jobs: " + IN_FLIGHT_ANALYSIS.size() + "\n"
                + "In-Flight Deduplicated Convert Jobs: " + IN_FLIGHT_CONVERT.size() + "\n"
                + "Cached SATB Analysis Entries: " + ANALYSIS_CACHE.size() + "\n"
                + "Cached Convert Entries: " + CONVERT_CACHE.size() + "\n\n";
        return ResponseEntity.ok(queueStatus + lastOmrDiagnostics);
    }

    @RequestMapping(value = "/clear-cache", method = {RequestMethod.GET, RequestMethod.POST})
    public ResponseEntity<Map<String, Object>> clearCache() {
        int analysisCount = ANALYSIS_CACHE.size();
        int convertCount = CONVERT_CACHE.size();
        ANALYSIS_CACHE.clear();
        CONVERT_CACHE.clear();
        IN_FLIGHT_ANALYSIS.clear();
        IN_FLIGHT_CONVERT.clear();
        log.info("[Cache Cleared] Cleared {} analysis entries and {} convert entries", analysisCount, convertCount);
        return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "OMR and SATB analysis caches cleared successfully",
                "cleared_analysis_entries", analysisCount,
                "cleared_convert_entries", convertCount
        ));
    }

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

    private static final String CACHE_VERSION_SALT = "v6.2-stave-cutaway-divisi-clef-fidelity";

    private String calculateSha256(byte[] data) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            digest.update(CACHE_VERSION_SALT.getBytes(java.nio.charset.StandardCharsets.UTF_8));
            byte[] hash = digest.digest(data);
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (Exception e) {
            return CACHE_VERSION_SALT + "_" + java.util.Arrays.hashCode(data);
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
        // Production-grade JVM settings for Audiveris: 2GB heap with G1GC (safely allows 2 concurrent jobs on 8GB host)
        String audiverisJvmOpts = "-Djava.awt.headless=true -Xms384m -Xmx2048m -XX:MaxMetaspaceSize=192m -XX:ReservedCodeCacheSize=96m -Xss1m -XX:+UseG1GC";
        pb.environment().put("JAVA_TOOL_OPTIONS", audiverisJvmOpts);
        pb.environment().put("JAVA_OPTS", audiverisJvmOpts);
        String resolvedTessdata = tessdataPrefix;
        if (resolvedTessdata == null || resolvedTessdata.isBlank() || !new File(resolvedTessdata).exists()) {
            String os = System.getProperty("os.name").toLowerCase();
            if (os.contains("win")) {
                resolvedTessdata = System.getProperty("user.home") + File.separator + "AppData" + File.separator + "Roaming" + File.separator + "AudiverisLtd" + File.separator + "audiveris" + File.separator + "config" + File.separator + "tessdata";
            } else {
                String[] linuxCandidates = {
                    "/usr/share/tessdata",
                    "/usr/share/tesseract-ocr/5/tessdata",
                    "/usr/share/tesseract-ocr/4.00/tessdata",
                    "/usr/share/tesseract-ocr/tessdata"
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
     * Ensures the filename always has a valid extension for format detection.
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) return "uploaded_score.jpg";
        String cleanName = new File(filename).getName(); // Remove path prefixes
        cleanName = cleanName.replaceAll("[^a-zA-Z0-9._-]", "_"); // Strip unsafe chars
        if (cleanName.isBlank()) return "uploaded_score.jpg";
        // Ensure the filename has a recognizable extension
        String lower = cleanName.toLowerCase();
        if (!lower.endsWith(".jpg") && !lower.endsWith(".jpeg") && !lower.endsWith(".png")
                && !lower.endsWith(".pdf") && !lower.endsWith(".mxl")
                && !lower.endsWith(".musicxml") && !lower.endsWith(".xml")) {
            cleanName = cleanName + ".jpg";
        }
        return cleanName;
    }

    /**
     * Pre-processes uploaded scores for Audiveris OMR.
     * Runs score_normalizer.py which:
     * - Trims dark borders (desk/screen edges)
     * - Applies adaptive background normalization (shadow & lighting removal)
     * - Deskews tilted staff lines
     * - Scales to safe Audiveris bounds (≤18MP, ≤3300px)
     */
    private File prepareScoreForOmr(File inputFile, File uploadsDir) {
        if (inputFile == null || !inputFile.exists()) return inputFile;
        try {
            String userDir = System.getProperty("user.dir");
            File script = new File(userDir, "scripts/score_normalizer.py");
            if (!script.exists()) {
                log.warn("[Score Normalizer] Script not found at: {}", script.getAbsolutePath());
                return inputFile;
            }
            String baseName = getBaseName(inputFile.getName());
            String ext = getExtension(inputFile.getName());
            if (ext.isEmpty()) {
                ext = ".jpg"; // Default to JPEG if no extension
            }
            File normalizedFile = new File(uploadsDir, "norm_" + baseName + ext);

            ProcessBuilder pb = new ProcessBuilder(
                    pythonPath,
                    script.getAbsolutePath(),
                    inputFile.getAbsolutePath(),
                    normalizedFile.getAbsolutePath()
            );
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = r.readLine()) != null) {
                    log.info("[Score Normalizer] {}", line);
                }
            }
            boolean finished = p.waitFor(90, TimeUnit.SECONDS);
            if (finished && p.exitValue() == 0 && normalizedFile.exists() && normalizedFile.length() > 0) {
                log.info("[Score Normalizer] Using normalized score: {} ({} bytes)", normalizedFile.getName(), normalizedFile.length());
                return normalizedFile;
            }
        } catch (Exception e) {
            log.warn("[Score Normalizer] Error during score normalization, falling back to original: {}", e.getMessage());
        }
        return inputFile;
    }

    @PostMapping("/convert")
    public ResponseEntity<OmrResponse> convert(
            @RequestParam("musicFile") MultipartFile file,
            @RequestParam(value = "bypassCache", required = false, defaultValue = "false") boolean bypassCache
    ) {
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

        // Check SHA-256 Cache for instant response (0.05s) unless bypassCache is requested
        String fileHash = calculateSha256(fileBytes);
        if (bypassCache) {
            log.info("[OMR Cache BYPASS] Bypassing convert cache for: {} (hash: {})", rawFilename, fileHash);
            CONVERT_CACHE.remove(fileHash);
        } else {
            OmrResponse cached = CONVERT_CACHE.get(fileHash);
            if (cached != null) {
                log.info("[OMR Cache HIT] Returning cached conversion for: {} (hash: {})", rawFilename, fileHash);
                return ResponseEntity.ok(cached);
            }
        }

        // In-flight deduplication: If another thread is actively converting this score, attach to it
        CompletableFuture<ResponseEntity<OmrResponse>> future = new CompletableFuture<>();
        CompletableFuture<ResponseEntity<OmrResponse>> existingFuture = IN_FLIGHT_CONVERT.putIfAbsent(fileHash, future);
        if (existingFuture != null) {
            log.info("[OMR In-Flight Deduplication] Score {} (hash: {}) is already being converted. Attaching to existing task...", rawFilename, fileHash);
            try {
                return existingFuture.get(600, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("[OMR In-Flight Deduplication] Error or timeout waiting for existing conversion: {}", e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(OmrResponse.ofError("In-flight score conversion timed out or failed. Please try again."));
            }
        }

        try {
            ResponseEntity<OmrResponse> response = executeConvert(rawFilename, fileBytes, fileHash, bypassCache);
            future.complete(response);
            return response;
        } catch (Exception e) {
            ResponseEntity<OmrResponse> errResponse = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrResponse.ofError("Conversion failed: " + e.getMessage()));
            future.complete(errResponse);
            return errResponse;
        } finally {
            IN_FLIGHT_CONVERT.remove(fileHash);
        }
    }

    private ResponseEntity<OmrResponse> executeConvert(String rawFilename, byte[] fileBytes, String fileHash, boolean bypassCache) {
        boolean acquired = false;
        try {
            log.info("[OMR Queue] Request for {} waiting for permit (Available: {}/{}, Queue depth: {})...",
                    rawFilename, omrSemaphore.availablePermits(), maxConcurrentJobs, omrSemaphore.getQueueLength());

            acquired = omrSemaphore.tryAcquire(600, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[OMR Queue Timeout] Score conversion timed out waiting for permit (600s): {}", rawFilename);
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(OmrResponse.ofError("The server is currently processing other scores. Please try again in a moment."));
            }

            // Post-Lock Double-Check Cache: In case another thread finished the same file while we queued
            if (!bypassCache) {
                OmrResponse postLockCached = CONVERT_CACHE.get(fileHash);
                if (postLockCached != null) {
                    log.info("[OMR Cache HIT Post-Lock] Returning cached conversion for: {} (hash: {})", rawFilename, fileHash);
                    return ResponseEntity.ok(postLockCached);
                }
            }

            String originalFilename = sanitizeFilename(rawFilename);
            log.info("[Job Started] Processing {} with Audiveris (Lock Acquired, Remaining permits: {})...",
                    originalFilename, omrSemaphore.availablePermits());

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
                        "-transcribe",
                        "-export",
                        "-output",
                        uploadsDir.getAbsolutePath(),
                        "--",
                        scoreToProcess.getAbsolutePath()
                );
                configureTessdataEnvironment(pb);
                pb.redirectErrorStream(true);
                System.gc(); // Free memory before starting heavy OMR engine
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

            // Search for output files (with automatic invalid-sheet recovery if a cover/back page failed)
            String baseName = getBaseName(uniqueName);
            File resultFile = locateOutputFile(baseName, uploadsDir);
            if (resultFile == null && !scoreToProcess.equals(uploadedFile)) {
                resultFile = locateOutputFile(getBaseName(scoreToProcess.getName()), uploadsDir);
            }
            if (resultFile == null) {
                resultFile = retryExcludingInvalidSheets(resolvedExecutable, scoreToProcess, uploadsDir, baseName, audiverisLog, progress -> {});
            }

            if (resultFile != null && resultFile.exists()) {
                String extension = getExtension(resultFile.getName());
                File targetFile = new File(outputsDir, baseName + extension);

                try {
                    Files.move(resultFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                    log.info("[Success] Moved {} to {}", resultFile.getName(), targetFile.getAbsolutePath());

                    cleanMusicXml(targetFile);


                    // Pre-analysis check / cache warm-up
                    try {
                        satbAnalysisService.analyze(targetFile);
                    } catch (SatbAnalysisService.SatbAnalysisException e) {
                        log.warn("[Convert] SATB pre-analysis advisory: {}", e.getMessage());
                    }

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
                omrSemaphore.release();
                log.info("[OMR Lock Released] Released permit for {} (Available: {}/{}, Queue depth: {})",
                        rawFilename, omrSemaphore.availablePermits(), maxConcurrentJobs, omrSemaphore.getQueueLength());
            }
        }
    }

    /**
     * Enhanced endpoint: Audiveris OMR → SATB Analysis Pipeline with In-Flight Deduplication & Concurrency Lock.
     */
    @PostMapping("/analyze")
    public ResponseEntity<OmrAnalysisResponse> analyze(
            @RequestParam("musicFile") MultipartFile file,
            @RequestParam(value = "bypassCache", required = false, defaultValue = "false") boolean bypassCache
    ) {
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

        // Check SHA-256 Cache for instant response (0.05s) unless bypassCache is requested
        String fileHash = calculateSha256(fileBytes);
        if (bypassCache) {
            log.info("[OMR Cache BYPASS] Bypassing analyze cache for: {} (hash: {})", rawFilename, fileHash);
            ANALYSIS_CACHE.remove(fileHash);
        } else {
            OmrAnalysisResponse cached = ANALYSIS_CACHE.get(fileHash);
            if (cached != null) {
                log.info("[OMR Cache HIT] Returning cached SATB analysis for: {} (hash: {})", rawFilename, fileHash);
                return ResponseEntity.ok(cached);
            }
        }

        // In-flight deduplication: If another thread is actively analyzing this score, attach to it
        CompletableFuture<ResponseEntity<OmrAnalysisResponse>> future = new CompletableFuture<>();
        CompletableFuture<ResponseEntity<OmrAnalysisResponse>> existingFuture = IN_FLIGHT_ANALYSIS.putIfAbsent(fileHash, future);
        if (existingFuture != null) {
            log.info("[OMR In-Flight Deduplication] SATB Analysis for {} (hash: {}) is already running. Attaching to existing task...", rawFilename, fileHash);
            try {
                return existingFuture.get(600, TimeUnit.SECONDS);
            } catch (Exception e) {
                log.error("[OMR In-Flight Deduplication] Error or timeout waiting for existing analysis of {}: {}", rawFilename, e.getMessage());
                return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                        .body(OmrAnalysisResponse.ofError("In-flight score analysis timed out or failed. Please try again."));
            }
        }

        try {
            ResponseEntity<OmrAnalysisResponse> response = executeAnalyze(rawFilename, fileBytes, fileHash, bypassCache);
            future.complete(response);
            return response;
        } catch (Exception e) {
            ResponseEntity<OmrAnalysisResponse> errResponse = ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrAnalysisResponse.ofError("Analysis failed: " + e.getMessage()));
            future.complete(errResponse);
            return errResponse;
        } finally {
            IN_FLIGHT_ANALYSIS.remove(fileHash);
        }
    }

    @PostMapping("/jobs/submit")
    public ResponseEntity<OmrJobStatusResponse> submitJob(
            @RequestParam("musicFile") MultipartFile file,
            @RequestParam(value = "bypassCache", required = false, defaultValue = "false") boolean bypassCache
    ) {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().body(OmrJobStatusResponse.failed(null, "File is empty"));
        }
        String rawFilename = file.getOriginalFilename();
        if (rawFilename == null || rawFilename.isBlank()) {
            return ResponseEntity.badRequest().body(OmrJobStatusResponse.failed(null, "Filename is missing"));
        }

        byte[] fileBytes;
        try {
            fileBytes = file.getBytes();
        } catch (IOException e) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrJobStatusResponse.failed(null, "Failed to read uploaded file"));
        }

        String fileHash = calculateSha256(fileBytes);
        if (!bypassCache) {
            OmrAnalysisResponse cached = ANALYSIS_CACHE.get(fileHash);
            if (cached != null) {
                log.info("[OMR Async Job Cache HIT] Returning cached conversion for: {}", rawFilename);
                return ResponseEntity.ok(OmrJobStatusResponse.completed("cached-" + fileHash, cached));
            }
        }

        String jobId = UUID.randomUUID().toString();
        OmrJobState jobState = new OmrJobState(jobId, 5, "Score uploaded, starting background conversion...");
        ACTIVE_ASYNC_JOBS.put(jobId, jobState);

        // Prune stale jobs older than 1 hour
        long oneHourAgo = System.currentTimeMillis() - 3600000L;
        ACTIVE_ASYNC_JOBS.entrySet().removeIf(e -> e.getValue().getCreatedAt() < oneHourAgo);

        asyncJobExecutor.submit(() -> {
            try {
                executeAsyncAnalyze(jobState, rawFilename, fileBytes, fileHash, bypassCache);
            } catch (Exception e) {
                log.error("[OMR Async Job Error] Unhandled exception in job {}: {}", jobId, e.getMessage(), e);
                jobState.fail("Conversion failed: " + e.getMessage());
            }
        });

        return ResponseEntity.ok(jobState.toResponse());
    }

    @GetMapping("/jobs/{jobId}/status")
    public ResponseEntity<OmrJobStatusResponse> getJobStatus(@PathVariable("jobId") String jobId) {
        OmrJobState jobState = ACTIVE_ASYNC_JOBS.get(jobId);
        if (jobState == null) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND)
                    .body(OmrJobStatusResponse.failed(jobId, "Job not found or expired"));
        }
        return ResponseEntity.ok(jobState.toResponse());
    }

    private void executeAsyncAnalyze(OmrJobState jobState, String rawFilename, byte[] fileBytes, String fileHash, boolean bypassCache) {
        try {
            OmrAnalysisResponse resp = runPipeline(rawFilename, fileBytes, fileHash, bypassCache, update -> {
                jobState.update(update.percent(), update.phaseText());
            });
            jobState.complete(resp);
        } catch (Exception e) {
            log.error("[Async Job Error] Job {} failed: {}", jobState.jobId, e.getMessage(), e);
            jobState.fail(e.getMessage());
        }
    }

    private ResponseEntity<OmrAnalysisResponse> executeAnalyze(String rawFilename, byte[] fileBytes, String fileHash, boolean bypassCache) {
        try {
            OmrAnalysisResponse resp = runPipeline(rawFilename, fileBytes, fileHash, bypassCache, update -> {});
            return ResponseEntity.ok(resp);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(OmrAnalysisResponse.ofError("Server request was interrupted."));
        } catch (Exception e) {
            log.error("[Analyze Error] {}", e.getMessage(), e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrAnalysisResponse.ofError(e.getMessage()));
        }
    }

    private OmrAnalysisResponse runPipeline(
            String rawFilename,
            byte[] fileBytes,
            String fileHash,
            boolean bypassCache,
            Consumer<ProgressUpdate> progressConsumer
    ) throws Exception {
        boolean acquired = false;
        try {
            log.info("[OMR Queue] Request for {} waiting for permit (Available: {}/{}, Queue depth: {})...",
                    rawFilename, omrSemaphore.availablePermits(), maxConcurrentJobs, omrSemaphore.getQueueLength());

            acquired = omrSemaphore.tryAcquire(600, TimeUnit.SECONDS);
            if (!acquired) {
                log.warn("[OMR Queue Timeout] SATB analysis timed out waiting for permit (600s): {}", rawFilename);
                throw new RuntimeException("The server is currently processing other scores. Please try again in a moment.");
            }

            // Post-Lock Double-Check Cache
            if (!bypassCache) {
                OmrAnalysisResponse postLockCached = ANALYSIS_CACHE.get(fileHash);
                if (postLockCached != null) {
                    log.info("[OMR Cache HIT Post-Lock] Returning cached SATB analysis for: {} (hash: {})", rawFilename, fileHash);
                    progressConsumer.accept(new ProgressUpdate(100, "Loaded from cache"));
                    return postLockCached;
                }
            }

            String originalFilename = sanitizeFilename(rawFilename);
            log.info("[Analyze] Starting full pipeline for: {} (Lock Acquired, Remaining permits: {})",
                    originalFilename, omrSemaphore.availablePermits());

            File uploadsDir = getUploadsDir();
            File outputsDir = getOutputsDir();

            progressConsumer.accept(new ProgressUpdate(10, "Uploading score and preparing workspace..."));

            // Step 1: Save uploaded file
            String uniqueName = System.currentTimeMillis() + "-" + originalFilename;
            File uploadedFile = new File(uploadsDir, uniqueName);
            try (FileOutputStream fos = new FileOutputStream(uploadedFile)) {
                fos.write(fileBytes);
            } catch (IOException e) {
                log.error("Failed to save uploaded file: ", e);
                throw new RuntimeException("Failed to save uploaded file: " + e.getMessage());
            }

            String lowerFilename = originalFilename.toLowerCase(java.util.Locale.ROOT);
            if (lowerFilename.endsWith(".musicxml") || lowerFilename.endsWith(".mxl") || lowerFilename.endsWith(".xml")) {
                progressConsumer.accept(new ProgressUpdate(85, "Analyzing imported MusicXML..."));
                SatbAnalysisService.SatbAnalysisResult analysis = satbAnalysisService.analyze(uploadedFile);
                OmrAnalysisResponse response = OmrAnalysisResponse.ofSuccess(
                        analysis.rawMusicXml(),
                        analysis.scoreMetadata(),
                        analysis.events(),
                        analysis.schemaVersion()
                );
                ANALYSIS_CACHE.put(fileHash, response);
                progressConsumer.accept(new ProgressUpdate(100, "Score ready!"));
                return response;
            }

            // Step 2: Run Audiveris
            String resolvedExecutable = getResolvedAudiverisPath();
            File execFile = new File(resolvedExecutable);
            log.info("[Analyze Audiveris Diagnostics] Executable path: '{}', exists: {}, canExecute: {}",
                    resolvedExecutable, execFile.exists(), execFile.canExecute());

            if (!execFile.exists() && resolvedExecutable.contains(File.separator)) {
                log.error("[Analyze Audiveris Error] Binary executable not found at specified path: {}", resolvedExecutable);
                throw new RuntimeException("OMR processing service is temporarily unavailable. Missing executable component.");
            }

            progressConsumer.accept(new ProgressUpdate(15, "Optimizing score resolution & trimming margins..."));
            File scoreToProcess = prepareScoreForOmr(uploadedFile, uploadsDir);

            progressConsumer.accept(new ProgressUpdate(20, "Starting notation transcription with Audiveris..."));

            StringBuilder audiverisLog = new StringBuilder();
            int exitCode = -1;
            Pattern sheetsPattern = Pattern.compile("(\\d+)\\s+sheets\\s+in");
            Pattern sheetNumPattern = Pattern.compile("\\[.*?#(\\d+)\\]");
            int[] totalSheetsHolder = new int[]{1};

            try {
                ProcessBuilder pb = new ProcessBuilder(
                        resolvedExecutable, "-batch", "-transcribe", "-export",
                        "-output", uploadsDir.getAbsolutePath(), "--", scoreToProcess.getAbsolutePath()
                );
                configureTessdataEnvironment(pb);
                pb.redirectErrorStream(true);
                System.gc(); // Free memory before starting heavy OMR engine
                Process process = pb.start();
                try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                    String line;
                    while ((line = reader.readLine()) != null) {
                        if (line.contains("Exit forced. Failure")) {
                            continue;
                        }
                        log.info("[Audiveris] {}", line);
                        audiverisLog.append(line).append("\n");

                        Matcher sm = sheetsPattern.matcher(line);
                        if (sm.find()) {
                            try {
                                totalSheetsHolder[0] = Math.max(1, Integer.parseInt(sm.group(1)));
                            } catch (Exception ignored) {}
                        }
                        Matcher snm = sheetNumPattern.matcher(line);
                        if (snm.find()) {
                            try {
                                int currentSheet = Integer.parseInt(snm.group(1));
                                totalSheetsHolder[0] = Math.max(totalSheetsHolder[0], currentSheet);
                                int pct = Math.min(80, 20 + (int) ((currentSheet / (double) totalSheetsHolder[0]) * 60));
                                progressConsumer.accept(new ProgressUpdate(pct, "Digitizing page " + currentSheet + " of " + totalSheetsHolder[0] + " (" + pct + "%)"));
                            } catch (Exception ignored) {}
                        }
                    }
                }
                exitCode = process.waitFor();
                log.info("[Analyze Audiveris Exit Code] {}", exitCode);
            } catch (Exception e) {
                log.error("Error executing Audiveris: ", e);
                throw new RuntimeException("Audiveris processing failed: " + e.getMessage());
            }

            // Step 3: Locate MusicXML output (with automatic invalid-sheet recovery matching Desktop Audiveris)
            String baseName = getBaseName(uniqueName);
            File resultFile = locateOutputFile(baseName, uploadsDir);
            if (resultFile == null && !scoreToProcess.equals(uploadedFile)) {
                resultFile = locateOutputFile(getBaseName(scoreToProcess.getName()), uploadsDir);
            }
            if (resultFile == null) {
                resultFile = retryExcludingInvalidSheets(resolvedExecutable, scoreToProcess, uploadsDir, baseName, audiverisLog, progressConsumer);
            }

            lastOmrDiagnostics = "=== LAST OMR JOB ===\n"
                    + "Timestamp: " + new java.util.Date() + "\n"
                    + "Filename: " + rawFilename + "\n"
                    + "Processed Path: " + scoreToProcess.getAbsolutePath() + "\n"
                    + "Exit Code: " + exitCode + "\n"
                    + "Found Output: " + (resultFile != null ? resultFile.getAbsolutePath() : "NOT FOUND") + "\n\n"
                    + "--- Audiveris Full Logs ---\n" + audiverisLog;

            if (resultFile == null || !resultFile.exists()) {
                String friendlyError = analyzeAudiverisLog(audiverisLog.toString());
                log.error("[Analyze Error] Audiveris output not found for baseName: {}. Friendly error: {}. Full Audiveris Logs:\n{}", baseName, friendlyError, audiverisLog);
                throw new RuntimeException(friendlyError);
            }

            progressConsumer.accept(new ProgressUpdate(85, "Preserving MusicXML and preparing analysis..."));

            // Step 4: Move to outputs folder
            String extension = getExtension(resultFile.getName());
            File targetFile = new File(outputsDir, baseName + extension);
            try {
                Files.move(resultFile.toPath(), targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException e) {
                log.error("Failed to move output: ", e);
                throw new RuntimeException("Failed to save converted file: " + e.getMessage());
            }

            cleanMusicXml(targetFile);

            progressConsumer.accept(new ProgressUpdate(90, "Analyzing SATB vocal lines..."));

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
                progressConsumer.accept(new ProgressUpdate(100, "Score ready!"));
                return response;

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
                    progressConsumer.accept(new ProgressUpdate(100, "Score ready!"));
                    return response;
                } catch (IOException ioe) {
                    throw new RuntimeException("Analysis failed and could not read MusicXML: " + ioe.getMessage());
                }
            }
        } finally {
            if (acquired) {
                omrSemaphore.release();
                log.info("[OMR Lock Released] Released permit for {} (Available: {}/{}, Queue depth: {})",
                        rawFilename, omrSemaphore.availablePermits(), maxConcurrentJobs, omrSemaphore.getQueueLength());
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

    /**
     * When a multi-page PDF contains a non-music cover, lyric, or blank appendix page,
     * Audiveris CLI (-batch -transcribe -export) aborts the export step even after
     * successfully transcribing the valid music pages (unlike Desktop Audiveris GUI).
     * This helper detects which sheet indices failed and retries transcription/export
     * restricted to the valid music sheets via Audiveris's built-in -sheets option.
     */
    private File retryExcludingInvalidSheets(
            String resolvedExecutable,
            File scoreToProcess,
            File uploadsDir,
            String baseName,
            StringBuilder audiverisLog,
            Consumer<ProgressUpdate> progressConsumer
    ) {
        String logs = audiverisLog.toString();
        int totalSheets = 0;
        Matcher totalMatcher = Pattern.compile("(\\d+)\\s+sheets\\s+in").matcher(logs);
        while (totalMatcher.find()) {
            try {
                totalSheets = Math.max(totalSheets, Integer.parseInt(totalMatcher.group(1)));
            } catch (Exception ignored) {}
        }
        if (totalSheets <= 1) {
            return null;
        }

        java.util.Set<Integer> invalidSheets = new java.util.TreeSet<>();
        Pattern invalidPattern = Pattern.compile(
                "\\[[^\\]]*?#(\\d+)\\].*?(?:No regularly spaced lines found|flagged as invalid|Sheet removed|Too large image|Too low interline|Error processing stub)"
        );
        for (String line : logs.split("\\R")) {
            Matcher m = invalidPattern.matcher(line);
            if (m.find()) {
                try {
                    invalidSheets.add(Integer.parseInt(m.group(1)));
                } catch (Exception ignored) {}
            }
        }

        if (invalidSheets.isEmpty() || invalidSheets.size() >= totalSheets) {
            return null;
        }

        List<String> validSheetNums = new ArrayList<>();
        for (int s = 1; s <= totalSheets; s++) {
            if (!invalidSheets.contains(s)) {
                validSheetNums.add(String.valueOf(s));
            }
        }
        if (validSheetNums.isEmpty()) {
            return null;
        }

        String procBaseName = getBaseName(scoreToProcess.getName());
        File staleOmr = new File(uploadsDir, procBaseName + ".omr");
        if (staleOmr.exists()) {
            staleOmr.delete();
        }

        log.info("[Audiveris Sheet Recovery] Retrying {} valid sheet(s) {} (excluding non-music/invalid sheet(s) {})...",
                validSheetNums.size(), validSheetNums, invalidSheets);
        progressConsumer.accept(new ProgressUpdate(70, "Skipping non-music page(s) and exporting valid sheets..."));

        try {
            List<String> cmd = new ArrayList<>();
            cmd.add(resolvedExecutable);
            cmd.add("-batch");
            cmd.add("-sheets");
            cmd.addAll(validSheetNums);
            cmd.add("-transcribe");
            cmd.add("-export");
            cmd.add("-output");
            cmd.add(uploadsDir.getAbsolutePath());
            cmd.add("--");
            cmd.add(scoreToProcess.getAbsolutePath());

            ProcessBuilder pb = new ProcessBuilder(cmd);
            configureTessdataEnvironment(pb);
            pb.redirectErrorStream(true);
            Process p = pb.start();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(p.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    if (line.contains("Exit forced. Failure")) continue;
                    log.info("[Audiveris Recovery] {}", line);
                    audiverisLog.append(line).append("\n");
                }
            }
            int retryExit = p.waitFor();
            log.info("[Audiveris Sheet Recovery] Finished with exit code: {}", retryExit);
        } catch (Exception e) {
            log.warn("[Audiveris Sheet Recovery] Recovery run failed: {}", e.getMessage());
            return null;
        }

        File recovered = locateOutputFile(baseName, uploadsDir);
        if (recovered == null && !procBaseName.equals(baseName)) {
            recovered = locateOutputFile(procBaseName, uploadsDir);
        }
        return recovered;
    }

    private File locateOutputFile(String baseName, File uploadsDir) {
        String[] possibleExtensions = {".mxl", ".musicxml", ".xml"};
        List<File> searchPaths = new ArrayList<>();

        for (String ext : possibleExtensions) {
            searchPaths.add(new File(uploadsDir, baseName + ext));
            searchPaths.add(new File(new File(uploadsDir, baseName), baseName + ext));
            searchPaths.add(new File(new File(uploadsDir, baseName + "-page-1"), baseName + "-page-1" + ext));
            searchPaths.add(new File(new File(uploadsDir, baseName + "-page-01"), baseName + "-page-01" + ext));
            searchPaths.add(new File(new File(uploadsDir, baseName + "-page"), baseName + "-page" + ext));
        }

        for (File path : searchPaths) {
            log.debug("Checking path for OMR output: {}", path.getAbsolutePath());
            if (path.exists() && path.isFile()) {
                return path;
            }
        }

        // Direct movement files in uploadsDir matching baseName (e.g. baseName.mvt1.mxl, baseName.mvt2.mxl)
        File[] allEntries = uploadsDir.listFiles();
        List<File> matchingMovementFiles = new ArrayList<>();
        if (allEntries != null) {
            for (File item : allEntries) {
                if (item.isFile()) {
                    String name = item.getName();
                    String lower = name.toLowerCase();
                    boolean isXmlOrMxl = lower.endsWith(".mxl") || lower.endsWith(".musicxml") || lower.endsWith(".xml");
                    if (isXmlOrMxl && (name.startsWith(baseName + ".") || name.startsWith(baseName + "-"))) {
                        matchingMovementFiles.add(item);
                    }
                }
            }
        }
        if (!matchingMovementFiles.isEmpty()) {
            if (matchingMovementFiles.size() > 1) {
                File merged = mergeMovementFilesIfPossible(baseName, uploadsDir, matchingMovementFiles);
                if (merged != null && merged.exists()) {
                    return merged;
                }
            }
            matchingMovementFiles.sort((a, b) -> Long.compare(b.length(), a.length()));
            log.info("Found OMR movement output matching baseName: {}", matchingMovementFiles.get(0).getAbsolutePath());
            return matchingMovementFiles.get(0);
        }

        // Check any folder inside uploadsDir containing baseName
        if (allEntries != null) {
            for (File d : allEntries) {
                if (d.isDirectory() && (d.getName().contains(baseName) || baseName.contains(d.getName()))) {
                    File[] subFiles = d.listFiles();
                    if (subFiles != null) {
                        for (File f : subFiles) {
                            String name = f.getName().toLowerCase();
                            if (name.endsWith(".mxl") || name.endsWith(".musicxml") || name.endsWith(".xml")) {
                                log.info("Found OMR output in subfolder matching baseName: {}", f.getAbsolutePath());
                                return f;
                            }
                        }
                    }
                }
            }
        }

        return null;
    }

    private File mergeMovementFilesIfPossible(String baseName, File uploadsDir, List<File> movementFiles) {
        // Sort movement files in natural movement order (mvt1, mvt2, ...)
        List<File> sorted = new ArrayList<>(movementFiles);
        sorted.sort(java.util.Comparator.comparing(File::getName));
        // If all movements have the same part count, return the largest or combined; if one movement dominates, use largest
        // Let's check if Python merger helper or largest movement applies:
        log.info("[Multi-Movement Output] Audiveris exported {} movement files for {}: {}",
                sorted.size(), baseName, sorted.stream().map(File::getName).toList());
        sorted.sort((a, b) -> Long.compare(b.length(), a.length()));
        return sorted.get(0);
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

        if (lowerLog.contains("too large image")) {
            return "The sheet music image dimensions are too large for processing. Please upload a standard Letter/A4 sized PDF or image.";
        }
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
            p.waitFor(30, java.util.concurrent.TimeUnit.SECONDS);
        } catch (Exception e) {
            log.warn("[MusicXML Cleaner] Non-fatal error cleaning MusicXML: {}", e.getMessage());
        }
    }

}

