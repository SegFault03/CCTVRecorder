# CCTV Recorder

A Java-based application for recording RTSP streams from multiple IP cameras simultaneously using FFmpeg. The application converts RTSP streams to HLS (HTTP Live Streaming) format for efficient storage and playback.

## Features

- **Multi-Camera Support**: Record from multiple IP cameras simultaneously
- **RTSP Stream Recording**: Connect to IP cameras via RTSP protocol
- **HLS Output Format**: Converts streams to HLS format for efficient storage and streaming
- **Configurable Recording**: Set custom chunk sizes and recording schedules for each camera
- **Real-time Configuration**: Hot-reload configuration changes without restarting the application
- **Automatic Directory Management**: Creates organized output directories and manages storage
- **Graceful Shutdown**: Properly terminates all FFmpeg processes on application exit
- **Comprehensive Logging**: Detailed logging with Log4j for monitoring and debugging

## Prerequisites

- **Java 8** or higher
- **Maven 3.6+** for building the project
- **FFmpeg** installed and available in system PATH
- **Network access** to IP cameras via RTSP

## Installation

1. **Clone or download the project**
   ```bash
   git clone <repository-url>
   cd cctv-recorder
   ```

2. **Install FFmpeg**
   - Download FFmpeg from [https://ffmpeg.org/download.html](https://ffmpeg.org/download.html)
   - Add FFmpeg to your system PATH
   - Verify installation: `ffmpeg -version`

3. **Build the project**
   ```bash
   mvn clean compile
   ```

## Configuration

### Camera Configuration

Edit the `src/main/resources/config.json` file to configure your cameras:

```json
{
  "cameras": [
    {
      "id": "1",
      "name": "Front Door Camera",
      "rtspUrl": "rtsp://192.168.1.4:5543/1",
      "chunkSize": 15,
      "startTime": "2024-01-01 8:00:00",
      "endTime": "2024-01-01 18:00:00"
    },
    {
      "id": "2",
      "name": "Parking Lot Camera",
      "rtspUrl": "rtsp://192.168.1.4:5540/1",
      "chunkSize": 15,
      "startTime": "2024-01-01 6:00:00",
      "endTime": "2024-01-01 22:00:00"
    }
  ]
}
```

#### Configuration Parameters

- **id**: Unique identifier for the camera
- **name**: Descriptive name for the camera (used in directory naming)
- **rtspUrl**: RTSP stream URL of the IP camera
- **chunkSize**: Duration of each HLS segment in seconds (recommended: 10-30 seconds)
- **startTime**: Recording start time (format: "YYYY-MM-DD HH:mm:ss")
- **endTime**: Recording end time (format: "YYYY-MM-DD HH:mm:ss")

### Logging Configuration

Logging is configured in `src/main/resources/log4j.properties`. The default configuration:
- Logs to both console and file (`logs/app.log`)
- Rotates log files when they reach 10MB
- Keeps up to 5 backup files

## Usage

### Running the Application

1. **Using Maven**:
   ```bash
   mvn exec:java -Dexec.mainClass="Main"
   ```

2. **Using compiled classes**:
   ```bash
   java -cp "target/classes:target/dependency/*" Main
   ```

3. **Creating a JAR** (optional):
   ```bash
   mvn package
   java -jar target/cctv-recorder-1.0-SNAPSHOT.jar
   ```

### Output Structure

The application creates the following directory structure:

```
cctv_recording_output/
├── CAM_<Camera_Name>_<ID>/
│   ├── logs/
│   │   └── ffmpeg_<Camera_Name>_<ID>.log
│   └── <timestamp>/
│       ├── playlist.m3u8
│       ├── segment_000.ts
│       ├── segment_001.ts
│       └── ...
└── logs/
    └── app.log
```

### Monitoring

- **Application logs**: Check `logs/app.log` for application-level events
- **FFmpeg logs**: Individual camera logs are stored in `cctv_recording_output/CAM_<Camera_Name>_<ID>/logs/`
- **Console output**: Real-time status updates are displayed in the console

## Project Structure

```
cctv-recorder/
├── src/
│   └── main/
│       ├── java/
│       │   ├── Main.java                    # Application entry point
│       │   ├── CCTVRecorderService.java     # Core recording service
│       │   └── com/tcs/ion/livestreaming/model/
│       │       └── CCTVBean.java            # Camera configuration model
│       └── resources/
│           ├── config.json                  # Camera configuration
│           └── log4j.properties            # Logging configuration
├── pom.xml                                 # Maven dependencies
├── logs/                                   # Application logs
├── cctv_recording_output/                  # Recording output directory
└── README.md                               # This file
```

## Dependencies

- **Gson 2.10.1**: JSON parsing and serialization
- **Apache Commons Logging 1.2**: Logging abstraction layer
- **Log4j 1.2.17**: Logging implementation

## Troubleshooting

### Common Issues

1. **FFmpeg not found**
   - Ensure FFmpeg is installed and added to system PATH
   - Test with: `ffmpeg -version`

2. **Cannot connect to camera**
   - Verify RTSP URL is correct
   - Check network connectivity to camera
   - Ensure camera supports RTSP protocol

3. **Recording not starting**
   - Check camera configuration in `config.json`
   - Verify start/end times are in correct format
   - Check application logs for error messages

4. **High CPU/Memory usage**
   - Reduce number of simultaneous recordings
   - Adjust chunk size (larger chunks = less CPU usage)
   - Monitor FFmpeg process logs

### Log Files

- **Application logs**: `logs/app.log`
- **FFmpeg logs**: `cctv_recording_output/CAM_<Camera_Name>_<ID>/logs/ffmpeg_<Camera_Name>_<ID>.log`

## Contributing

1. Fork the repository
2. Create a feature branch (`git checkout -b feature/new-feature`)
3. Commit your changes (`git commit -am 'Add new feature'`)
4. Push to the branch (`git push origin feature/new-feature`)
5. Create a Pull Request

## License

This project is licensed under the MIT License - see the LICENSE file for details.

## Support

For issues and questions:
1. Check the troubleshooting section above
2. Review application and FFmpeg logs
3. Create an issue in the project repository with:
   - System information (OS, Java version, FFmpeg version)
   - Configuration file (with sensitive information removed)
   - Relevant log excerpts
   - Steps to reproduce the issue