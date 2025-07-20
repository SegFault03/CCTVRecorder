package com.tcs.ion.livestreaming;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.tcs.ion.livestreaming.model.CCTVBean;
import com.google.gson.Gson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;

import java.io.File;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.*;
import java.util.stream.Stream;

/**
 * Service class responsible for managing CCTV recording processes using FFmpeg.
 * Handles starting, stopping, and monitoring of FFmpeg processes for multiple CCTV cameras.
 * <p>
 * This service implements a Singleton pattern to ensure only one instance manages all recording processes.
 * It uses FFmpeg for video capture and processing from RTSP streams provided by IP cameras.
 * <p>
 * Key responsibilities:
 * - Reads camera configurations from a JSON file (config.json)
 * - Spawns and manages FFmpegProcessHandler threads for each camera
 * - Provides methods to start and stop all recording processes
 * - Watches for configuration file changes and reloads configuration as needed
 * - Cleans up resources and output directories as required
 * - Handles HLS (HTTP Live Streaming) segment creation for recorded video
 * <p>
 * The service creates organized directory structures for each camera's recordings and logs.
 */
public class CCTVRecorderService {
    private static CCTVRecorderService cctvRecorderService;
    private static final Log logger = LogFactory.getLog(CCTVRecorderService.class);
    private static final String LOG_FILE_PATH = "logs/app.log";
    private static final String OUTPUT_PATH = "cctv_recording_output";
    private static String configFilePath = "";
    private static final Gson gson = new Gson();
    private static ConcurrentHashMap<Integer, CCTVBean> currentlyRecordingCctvBeanMap;
    private static ConcurrentHashMap<Integer, FfmpegProcessBean> cctvIdToFfmpegProcessMap;
    private static ScheduledExecutorService ffmpegTaskScheduler;


    /**
     * Inner class that holds the components needed to manage a single FFmpeg recording process.
     * Acts as a container for the process, its handler, and the thread running the handler.
     */
    private static class FfmpegProcessBean {
        private Process ffmpegProcess;       // The actual FFmpeg process running the recording
        private FfmpegProcessHandler handler; // Handler that manages the FFmpeg process lifecycle
        private Thread thread;               // Thread running the handler
        private ScheduledFuture<?> futureFFmpegTask;

        public ScheduledFuture<?> getFutureFFmpegTask() {
            return futureFFmpegTask;
        }

        public void setFutureFFmpegTask(ScheduledFuture<?> futureFFmpegTask) {
            this.futureFFmpegTask = futureFFmpegTask;
        }

        /**
         * @return The FFmpeg process instance
         */
        public Process getFfmpegProcess() {
            return ffmpegProcess;
        }

        /**
         * @param ffmpegProcess The FFmpeg process to set
         */
        public void setFfmpegProcess(Process ffmpegProcess) {
            this.ffmpegProcess = ffmpegProcess;
        }

        /**
         * @return The handler managing the FFmpeg process
         */
        public FfmpegProcessHandler getHandler() {
            return handler;
        }

        /**
         * @param handler The handler to set
         */
        public void setHandler(FfmpegProcessHandler handler) {
            this.handler = handler;
        }

        /**
         * @return The thread running the handler
         */
        public Thread getThread() {
            return thread;
        }

        /**
         * @param thread The thread to set
         */
        public void setThread(Thread thread) {
            this.thread = thread;
        }
    }

    /**
     * Inner class responsible for handling the lifecycle of a single FFmpeg process.
     * Manages starting, monitoring, and stopping the FFmpeg process for a specific camera.
     * Implements Runnable to allow execution in a separate thread.
     */
    private static class FfmpegProcessHandler implements Runnable {
        private final CCTVBean cctvBean;    // Camera configuration bean
        private Process ffmpegProcess;       // The FFmpeg process for this camera

        /**
         * Constructs a new FFmpeg process handler for the given camera.
         *
         * @param cctvBean The camera configuration bean containing RTSP URL and other settings
         */
        FfmpegProcessHandler(CCTVBean cctvBean) {
            this.cctvBean = cctvBean;
        }

        /**
         * Starts and manages the FFmpeg process for recording camera feed.
         * Creates necessary directories, builds the FFmpeg command, and starts the process.
         */
        @Override
        public void run() {
            logger.info("Starting Thread - " + Thread.currentThread().getName());
            String outputDirectoryPath = "cctv_recording_output/CAM_" + cctvBean.getName() + "_" + cctvBean.getId() + "/recording/";
            String ffmpegLogDirectory = "cctv_recording_output/CAM_" + cctvBean.getName() + "_" + cctvBean.getId() + "/logs/";
            createOutputDirectory(outputDirectoryPath);
            createOutputDirectory(ffmpegLogDirectory);
            List<String> ffmpegArgs = getFfmpegArgs(outputDirectoryPath);
            ProcessBuilder ffmpegProcessBuilder = new ProcessBuilder(ffmpegArgs);
            try {
                String ffmpegLogFile = ffmpegLogDirectory + "ffmpeg_" + cctvBean.getName() + "_" + cctvBean.getId() + ".log";
                ProcessBuilder.Redirect fileRedirect = ProcessBuilder.Redirect.appendTo(new File(ffmpegLogFile));
                ffmpegProcessBuilder.redirectError(fileRedirect);
                logger.info("Executing ffmpeg task: " + String.join(" ",ffmpegArgs) + " for camera: " + cctvBean.getName());
                ffmpegProcess = ffmpegProcessBuilder.start();
                FfmpegProcessBean ffmpegProcessBean = cctvIdToFfmpegProcessMap.get(cctvBean.getId());
                ffmpegProcessBean.setFfmpegProcess(ffmpegProcess);
                logger.info("FFmpeg Process for camera " + cctvBean.getName() + " started successfully");
            } catch (IOException e) {
                logger.error("Failed to start ffmpeg Process for: " + cctvBean.getName(), e);
            }
        }

        /**
         * Builds the FFmpeg command line arguments for recording from RTSP stream to HLS format.
         *
         * @param outputDirectoryPath The directory path where HLS segments will be stored
         * @return A list of command line arguments for the FFmpeg process
         */
        public List<String> getFfmpegArgs(String outputDirectoryPath) {
            List<String> ffmpegArgs = new ArrayList<>();
            ffmpegArgs.add("ffmpeg");             // FFmpeg command
            ffmpegArgs.add("-i");                // Input flag
            ffmpegArgs.add(cctvBean.getRtspUrl()); // RTSP URL from camera config
            ffmpegArgs.add("-c:v");              // Video codec flag
            ffmpegArgs.add("copy");              // Copy video stream without re-encoding
            ffmpegArgs.add("-c:a");              // Audio codec flag
            ffmpegArgs.add("copy");              // Copy audio stream without re-encoding
            ffmpegArgs.add("-hls_time");         // HLS segment duration flag
            ffmpegArgs.add(String.valueOf(cctvBean.getChunkSize())); // Segment duration in seconds
            ffmpegArgs.add("-hls_list_size");    // HLS playlist size flag
            ffmpegArgs.add("0");                 // 0 means keep all segments in playlist
            ffmpegArgs.add(outputDirectoryPath+"output.m3u8"); // Output HLS playlist file
            return ffmpegArgs;
        }

        /**
         * Creates the output directory structure for storing HLS segments and logs.
         *
         * @param outputDirectoryPath The directory path to create
         */
        private void createOutputDirectory(String outputDirectoryPath) {
            logger.info("Creating " + outputDirectoryPath + " subfolder for " + cctvBean.getName() +" inside " + OUTPUT_PATH);
        Path outputPath = Paths.get(outputDirectoryPath);
            try {
            Files.createDirectories(outputPath);
        } catch (IOException e) {
            logger.error("Failed creating output directory: " + outputDirectoryPath, e);
        }
    }

    /**
     * Stops the FFmpeg process gracefully, attempting forceful termination if necessary.
     * First attempts a normal termination with a 15-second timeout, then forces termination if needed.
     * Logs the process status throughout the termination attempt.
     */
    public void stop() {
        logger.info("Attempting to stop ffmpeg process for cam " + cctvBean.getName());
        if(ffmpegProcess == null || !ffmpegProcess.isAlive()) {
            logger.warn("No alive ffmpeg process found for cam " + cctvBean.getName() +" that can be killed!");
            return;
        }
        // Attempt graceful termination first
        ffmpegProcess.destroy();
        try {
            // Wait up to 15 seconds for process to terminate
            boolean hasFinished = ffmpegProcess.waitFor(15, TimeUnit.SECONDS);
            if(!hasFinished) {
                logger.warn("Failed to gracefully destroy ffmpeg process for " + cctvBean.getName() + ", attempting to destroy it forcefully");
                // Force termination if graceful approach failed
                ffmpegProcess.destroyForcibly();
            }
        } catch (InterruptedException e) {
            logger.error("Thread: " + Thread.currentThread().getName() + " interrupted!");
        }
        logger.info("Ffmpeg process for cam " + cctvBean.getName() + " stopped successfully");
    }
}

/**
 * Private constructor for singleton pattern implementation.
 * Prevents external instantiation of the service.
 */
private CCTVRecorderService(){
}

/**
 * Returns the singleton instance of CCTVRecorderService, creating it if it doesn't exist.
 * Thread-safe singleton implementation using lazy initialization.
 *
 * @param configFilePathProvided Path to the configuration JSON file
 * @return The singleton instance of CCTVRecorderService
 */
public static CCTVRecorderService getNewInstance(String configFilePathProvided){
    if(cctvRecorderService == null) {
        cctvRecorderService = new CCTVRecorderService();
        configFilePath = configFilePathProvided;
    }
    return cctvRecorderService;
}

/**
 * Clears the application log file and writes an initial startup entry.
 * Opens the log file in truncate mode to clear previous content, then adds a timestamp entry.
 * Uses the LOG_FILE_PATH constant to determine the log file location.
 */
private void clearLogFile() {
    try (FileOutputStream writer = new FileOutputStream(LOG_FILE_PATH, false)) {
        // Opening the file with 'false' parameter truncates it
        logger.info("Application Started at: " + formatAndGetCurrentDateTime());
    } catch (IOException e) {
        logger.error("Failed to truncate log file for",e);
    }
}

/**
 * Sets up a watch service to monitor changes to the configuration file.
 * Creates a dedicated daemon thread that watches for file modifications and
 * automatically reloads the configuration when changes are detected.
 * <p>
 * This provides dynamic reconfiguration without requiring application restart.
 */
private void setUpConfigFileWatchService() {
    String configFileParentFolder = configFilePath.substring(0,configFilePath.lastIndexOf("/"));
    Runnable configFileWatcherTask = () -> {
        logger.info("Starting " + Thread.currentThread().getName());
        try(WatchService configFileWatchService = FileSystems.getDefault().newWatchService()) {
            logger.info("Config file WatchService successfully configured");
            Path path = Paths.get(configFileParentFolder);
            path.register(
                    configFileWatchService,
                    StandardWatchEventKinds.ENTRY_MODIFY);
            while (true) {
                WatchKey key;
                try {
                    key = configFileWatchService.take(); // Waits for a key to be signaled
                } catch (InterruptedException e) {
                    logger.error(e);
                    return; // Exit if interrupted
                }

                for (WatchEvent<?> event : key.pollEvents()) {
                    WatchEvent.Kind<?> kind = event.kind();

                    if(kind == StandardWatchEventKinds.ENTRY_MODIFY) {
                        logger.info("config.json modified at " + formatAndGetCurrentDateTime());
                        startFfmpegTaskScheduler(readConfigFile());
                    } else {
                        logger.info("Invalid action (DELETION, INSERTION) performed on config.json at " + formatAndGetCurrentDateTime());
                    }
                }

                boolean valid = key.reset(); // Reset the key to receive further events
                if (!valid) {
                    break; // Directory is no longer accessible or registered
                }
            }
        } catch (IOException e) {
            logger.error("Failed to configure watch service on config.json",e);
        }

    };
    Thread configfileWatcherThread = new Thread(configFileWatcherTask, "configFileWatcherThread");
    configfileWatcherThread.setDaemon(false);
    configfileWatcherThread.start();
}

/**
 * Returns the current date and time formatted as a string.
 * Uses the pattern "yyyy-MM-dd HH:mm:ss" for consistent timestamp formatting
 * throughout the application.
 *
 * @return Formatted current date and time string
 */
private String formatAndGetCurrentDateTime() {
    LocalDateTime currentTime = LocalDateTime.now();
    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    return dateTimeFormatter.format(currentTime);
}

/**
 * Reads and parses the camera configuration from the JSON config file.
 * Uses Gson to parse the JSON structure and create CCTVBean objects for each camera.
 * Stores the cameras in a thread-safe ConcurrentHashMap indexed by camera ID.
 *
 * @return A map of camera IDs to their corresponding CCTVBean configuration objects
 */
private ConcurrentHashMap<Integer, CCTVBean> readConfigFile() {
    ConcurrentHashMap<Integer, CCTVBean> configMap = new ConcurrentHashMap<>();
    try (FileReader reader = new FileReader(configFilePath)) {
        JsonObject configJson =  gson.fromJson(reader, JsonObject.class);
        JsonArray cameras = configJson.getAsJsonArray("cameras");
        for(JsonElement camera: cameras){
            if(camera.isJsonObject()){
                CCTVBean cctvBean = gson.fromJson(camera, CCTVBean.class);
                configMap.put(cctvBean.getId(), cctvBean);
            }
        }
    } catch (Exception e) {
        logger.error("Failed to read config file",e);
    }
    logger.info("Config file read successfully");
    System.out.println(configMap);
    return configMap;
}

/**
 * Cleans the output directory by removing all files and subdirectories.
 * This is typically called during service startup to ensure a clean recording environment.
 * Preserves the root output directory while removing all its contents.
 * Uses a reverse-order walk to ensure child files are deleted before their parent directories.
 */
public void cleanDirectory() {
    // Convert the string to a Path object
    Path directoryPath = Paths.get(OUTPUT_PATH);

    // Check if the path is actually a directory.
    // Files.isDirectory() returns false if the path doesn't exist or is not a directory.
    if (!Files.isDirectory(directoryPath)) {
        logger.info(directoryPath + " does not exist - No files/folders to clean");
        return;
    }
    logger.info(directoryPath + " found. Cleaning old files/folders");
    // Use a try-with-resources block to automatically close the stream
    try (Stream<Path> walk = Files.walk(directoryPath)) {
        walk.sorted(Comparator.reverseOrder()) // Sort in reverse to delete contents first
                .filter(path -> !path.equals(directoryPath)) // Don't delete the root folder itself
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (IOException e) {
                        // You can log this error or handle it as needed
                        logger.error("Failed to delete " + path + ": " + e.getMessage(), e);
                    }
                });
    } catch (IOException e) {
        // Handle error during the directory walk itself
        logger.error("Error processing directory " + directoryPath + ": " + e.getMessage(), e);
    }
}

/**
 * Stops all running FFmpegProcessHandler threads and associated processes.
 * Iterates over all managed process beans, calls stop() on the handler, and interrupts the thread.
 * Clears the internal process map after stopping all.
 */
public void stopAll() {
    if(cctvIdToFfmpegProcessMap.isEmpty()) {
        logger.info("No thread or process exist that needs to be stopped");
        return;
    }
    logger.info("Stopping all FfmpegProcessHandler threads and processes");
    for (Integer id : cctvIdToFfmpegProcessMap.keySet()) {
        try {
            FfmpegProcessBean bean = cctvIdToFfmpegProcessMap.get(id);
            if (bean != null && bean.getHandler() != null) {
                bean.getHandler().stop(); // Stop the handler (FFmpeg process)
            }
            Thread t = bean != null ? bean.getThread() : null;
            if (t != null && t.isAlive()) {
                logger.info("Stopping Thread: " + t.getName());
                t.interrupt(); // Interrupt the thread
            }
        } catch (Exception e) {
            logger.error("Error stopping handler/thread for camera id " + id, e);
        }
    }
    cctvIdToFfmpegProcessMap.clear();
}


synchronized private void startFfmpegTaskScheduler(Map<Integer, CCTVBean> updatedMap) {
    for(int res_id: updatedMap.keySet()) {
        if(!currentlyRecordingCctvBeanMap.containsKey(res_id)) {
            currentlyRecordingCctvBeanMap.put(res_id, updatedMap.get(res_id));
        }
    }
    for(int res_id: currentlyRecordingCctvBeanMap.keySet()){
        FfmpegProcessBean processBean = new FfmpegProcessBean();
        FfmpegProcessHandler handler = new FfmpegProcessHandler(currentlyRecordingCctvBeanMap.get(res_id));
        ScheduledFuture<?> future = ffmpegTaskScheduler.schedule(handler,5,TimeUnit.SECONDS);
        processBean.setHandler(handler);
        processBean.setFutureFFmpegTask(future);
        cctvIdToFfmpegProcessMap.put(res_id, processBean);
    }
}


/**
 * Starts the CCTVRecorderService with the following initialization steps:
 * 1. Clears the log file to start with a fresh log
 * 2. Sets up a watch service to monitor config file changes
 * 3. Cleans the output directory to remove any previous recordings
 * 4. Initializes the process map and starts recording threads
 * <p>
 * Note: The current implementation contains test code that starts a single
 * recording process. In production, this should be replaced with code that
 * starts processes for all cameras defined in the configuration file.
 */
public void start() {
    clearLogFile();
    setUpConfigFileWatchService();
    cleanDirectory();
    // Initialize the process map
    cctvIdToFfmpegProcessMap = new ConcurrentHashMap<>();
    currentlyRecordingCctvBeanMap = new ConcurrentHashMap<>();
    ConcurrentHashMap<Integer, CCTVBean> initialCctvBeanMap = readConfigFile();
    ffmpegTaskScheduler = Executors.newScheduledThreadPool(10);
    startFfmpegTaskScheduler(initialCctvBeanMap);

    // TODO: Replace this test code with production code that starts all cameras
    // For testing only - starts a single camera recording process
//    CCTVBean bean = readConfigFile().get(2);
//    FfmpegProcessBean processBean = new FfmpegProcessBean();
//    FfmpegProcessHandler handler = new FfmpegProcessHandler(bean);
//    Thread t = new Thread(handler);
//    t.setName("FFMPEG-Thread-1");
//    processBean.setHandler(handler);
//    processBean.setThread(t);
//    cctvIdToFfmpegProcessMap.put(2, processBean);
//    t.start();
}
}
