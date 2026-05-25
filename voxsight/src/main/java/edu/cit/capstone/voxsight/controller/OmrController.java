package edu.cit.capstone.voxsight.controller;

import edu.cit.capstone.voxsight.dto.OmrResponse;
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

    public OmrController() {
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
            pb.redirectErrorStream(true); // Merge stdout and stderr
            Process process = pb.start();

            // Read output logs in real-time
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
                String line;
                while ((line = reader.readLine()) != null) {
                    log.info("[Audiveris Log] {}", line);
                }
            }

            int exitCode = process.waitFor();
            log.info("Audiveris finished with exit code: {}", exitCode);
            // We ignore non-zero exit codes because Audiveris frequently returns warning codes even on success.

        } catch (Exception e) {
            log.error("Error executing Audiveris: ", e);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrResponse.ofError("Audiveris execution failed: " + e.getMessage()));
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

                String fileUrl = "/outputs/" + baseName + extension;
                return ResponseEntity.ok(OmrResponse.ofSuccess(fileUrl, baseName + extension));
            } catch (IOException e) {
                log.error("Failed to move output file: ", e);
                return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                        .body(OmrResponse.ofError("Failed to manage output file: " + e.getMessage()));
            }
        } else {
            log.error("[Error] Audiveris finished, but we couldn't find the output file for baseName: {}", baseName);
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body(OmrResponse.ofError("Audiveris succeeded, but output file not found."));
        }
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
}
