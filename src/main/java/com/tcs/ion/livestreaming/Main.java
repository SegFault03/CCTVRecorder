package com.tcs.ion.livestreaming;

/**
 * Main application class for the CCTV Recorder system.
 * <p>
 * This class serves as the entry point for the application and is responsible for:
 * 1. Initializing the CCTVRecorderService singleton with the correct configuration path
 * 2. Starting the service to begin camera recording processes
 * 3. Setting up a shutdown hook to ensure graceful termination of all FFmpeg processes
 *    when the application is terminated
 * <p>
 * The application uses FFmpeg to record RTSP streams from IP cameras and store them
 * in HLS format for later viewing or archiving.
 */
public class Main {
    /**
     * Application entry point. Initializes the service and sets up shutdown handling.
     *
     * @param args Command line arguments (not currently used)
     */
    public static void main(String[] args) {
        // Set the config file path (can be passed as an argument or set here)
        String configFilePath = "src/main/resources/config.json"; // Updated to correct path

        // Get the singleton service instance
        CCTVRecorderService service = CCTVRecorderService.getNewInstance(configFilePath);

        // Start the service (initializes logging, config watching, and recording processes)
        service.start();

        // Add shutdown hook to ensure all FFmpeg processes and threads are stopped on exit
        // This ensures resources are properly released when the application terminates
        Runtime.getRuntime().addShutdownHook(new Thread(service::stopAll, "ShutdownHook-StopAll"));
    }
}
