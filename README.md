# ModAutoDownload - Minecraft Mod Downloader

# ===========SubProject 1===========

# ModDownloadClient - Minecraft Mod Downloader(Folder "modownloadclient")

# Introduction
ModownloadClient is a sophisticated Minecraft Forge mod designed for 1.16.5 that provides a user-friendly interface for downloading and managing mods from a centralized server. This mod implements a robust download manager with pause/resume functionality, progress tracking, speed monitoring, and intelligent file management capabilities.

Built with JavaFX for the GUI and leveraging Minecraft Forge's modding framework, ModownloadClient offers server administrators a powerful tool to distribute mod packages while giving players control over which mods they want to install or ignore.

# Features
Core Functionality
Server-based Mod Distribution: Download mods from a configured server endpoint

Progress Tracking: Real-time download progress with percentage completion

Speed Monitoring: Current download speed displayed in KB/s

File Management: Automatic cleanup of non-server files (with ignore list support)

# Advanced Capabilities
Pause/Resume Downloads: Temporarily halt downloads and resume later

Ignore System: Whitelist specific mods to prevent deletion during cleanup

File Validation: Checks for existing files before downloading

Automatic Restart Prompt: Recommends game restart after completing downloads

Thread-safe Operations: Safe multithreading implementation for background downloads

# User Experience
Clean UI: Intuitive interface with clear status indicators

Error Handling: User-friendly error messages for common issues

Modal Dialogs: Separate windows for configuration and status

Responsive Design: UI updates are handled on the JavaFX application thread

# Installation
Requirements
Minecraft 1.16.5

Forge (recommended version: 36.2.39)

Java 8 or higher

Installation Steps
Download the compiled JAR file from the releases section

Place the JAR file in your Minecraft's mods folder

Launch Minecraft with Forge profile

The mod will automatically create necessary directories and configuration files

# Usage
Basic Operation
Upon launching Minecraft with the mod installed, a GUI window will appear

Enter the server URL (typically ending with /getDurlData)

Click "Fetch" to begin the download process

Monitor progress through the progress bar and status labels

Advanced Features
Ignore List: Click "Ignore-mods" to select which mods should be excluded from cleanup

Pause/Resume: Use the pause button to temporarily stop downloads

Cancel: Stops all downloads and closes the application

# Configuration
File Structure
The mod creates the following directory structure:

mods/Data/Ignore.json

Ignore List Management
The ignore list is stored in mods/Data/Ignore.json with the format:

{
  "1": "mod1.jar",
  "2": "mod2.jar"
}

Programmatic Configuration
While most configuration is handled through the GUI, you can manually edit the Ignore.json file to add or remove mods from the ignore list.

# Technical Details
Architecture Overview
ModownloadClient consists of two main components:

GUI Layer (DownloadProgressWindow.java): Handles all user interaction and display

Download Engine (Modownloadclient.java): Manages the actual download operations

Key Classes
DownloadProgressWindow: Main GUI class extending JavaFX Application

Modownloadclient: Core mod class implementing Forge mod lifecycle

Threading Model
JavaFX Application Thread: Handles all UI updates

Background Thread: Manages file downloads and server communication

Synchronization: Uses AtomicBoolean for thread-safe pause/cancel operations

Performance Considerations
Chunked downloads (4KB buffer size)

Speed calculation smoothed over 200ms intervals

Memory-efficient streaming of download content

# API Integration
Server Requirements
The server must implement:

A HEAD-accessible endpoint for URL validation

A GET-accessible endpoint returning the mod list JSON

Expected Responses
HEAD Request: Should return HTTP 200 if server is reachable

GET Request: Should return JSON with mod URLs as described above

Error Handling
The client handles:

Connection timeouts

Invalid JSON responses

File write permissions

Missing directories

# Troubleshooting
Common Issues
Server Not Reachable

Verify the URL is correct

Check server firewall settings

Ensure the endpoint is accessible without authentication

Download Stalls

Check network connectivity

Verify server is not rate limiting

Ensure sufficient disk space is available

GUI Not Appearing

Verify JavaFX is properly installed

Check Minecraft logs for initialization errors

Ensure no other mods are conflicting

Logging
Check Minecraft's latest.log for detailed error messages related to:

Server communication failures

File system operations

JSON parsing errors

# Development
Building from Source
Clone the repository

Set up a Forge 1.16.5 MDK environment

Add the project to your IDE

Build with Gradle

Dependencies
Minecraft Forge 1.16.5

Google Gson (for JSON processing)

JavaFX (for GUI)

Extending Functionality
Potential enhancements:

Add download queue prioritization

Implement checksum verification

Add mod version checking

Support for multiple server endpoints

# Contributing
We welcome contributions! Please follow these guidelines:

Fork the repository

Create a feature branch

Submit pull requests with:

Clear documentation

Appropriate test cases

Backward compatibility

Code Style
Follow Oracle Java Code Conventions

Use descriptive variable names

Include comments for complex logic

Maintain consistent indentation (4 spaces)

Testing
Please test any changes against:

Different network conditions

Various server response types

Edge cases in file handling

# ===========SubProject 2===========

# SimpleDownload - Minecraft Mod Distribution Server(Folder "simpledownload")

# Introduction
SimpleDownload is a robust server-side mod for Minecraft 1.16.5 Forge designed to facilitate secure and efficient mod distribution. This mod implements an HTTP server within the Minecraft environment, providing a RESTful API endpoint for clients to retrieve mod packages while incorporating enterprise-grade security features like rate limiting and IP blocking.

Built on Java's lightweight HTTP server framework, SimpleDownload offers server administrators complete control over mod distribution with real-time monitoring capabilities, making it ideal for modpack distribution, private servers, or mod development teams.

# Features
Core Functionality
Embedded HTTP Server: Runs on port 8000 by default

JSON API Endpoint: /getDurlData provides structured mod information

Dynamic Configuration: Live modification of mod URLs without restart

Automatic Config Management: Persists settings between server sessions

Security Features
Rate Limiting: 100 requests per minute per IP (configurable)

IP Blocking: Automatic blocking of abusive clients (30-minute duration)

Connection Logging: Optional logging of all incoming connections

Ban Logging: Optional logging of blocked IPs

Administrative Tools
In-Game Commands: Full configuration via Minecraft chat

Desktop Integration: Direct config file opening from game

Real-time Monitoring: Connection statistics and security events

# Installation
Requirements
Minecraft Server 1.16.5

Forge (recommended version: 36.2.39)

Java 8 or higher

Installation Steps
Download the compiled JAR file from the releases section

Place the JAR file in your server's mods folder

Start the server to generate default configuration

Configure mod URLs using in-game commands or config file

# Usage
Basic Operation
The mod automatically starts an HTTP server on port 8000

Clients can access http://[server-ip]:8000/getDurlData to retrieve mod list

All configuration is managed through in-game commands or config file

First-Time Setup
After installation, use /durl file to open the configuration

Add mod URLs using /durl add [number] [url]

Verify setup with /durl list

# Configuration
File Structure
The mod creates the following structure:

mods/SDSetup/Settings.json

Configuration File Format

{
  "urls": {
    "1": "http://example.com/mods/mod1.jar",
    "2": "http://example.com/mods/mod2.jar"
  },
  "settings": {
    "logConnections": true,
    "logBans": true
  }
}

Runtime Configuration
All settings can be modified through commands without editing the file directly:

/durl clo Enable|Disable - Toggle connection logging

/durl banlog Enable|Disable - Toggle ban logging

# API Documentation
Endpoint: /getDurlData
Method: GET or HEAD
Response:

{
  "urls": {
    "1": "http://example.com/mods/mod1.jar",
    "2": "http://example.com/mods/mod2.jar"
  }
}

Headers:

Content-Type: application/json

Security:

Rate limited to 100 requests/minute/IP

Excessive requests result in 30-minute IP ban

Error Responses

{
  "error": "IP blocked due to excessive requests"
}

403: IP blocked

500: Server error

# Security Implementation
Rate Limiting Architecture
ConcurrentHashMap tracks IP access counts

AtomicInteger ensures thread-safe counting

Scheduled cleanup resets counters every minute

IP Blocking Mechanism
Block List: ConcurrentHashMap stores blocked IPs with timestamps

Automatic Expiration: 30-minute block duration

Self-cleaning: Expired blocks are automatically removed

Location Tracking
Basic IP geolocation implemented with:

LAN detection for local addresses

TLD-based country guessing

Extensible architecture for future integration with proper geolocation APIs

# Command Reference
Mod URL Management

/durl add [number] [url]	Add mod URL	-> Example : /durl add 1 http://example.com/mod.jar

/durl delete [number]	Remove mod URL	-> Example : /durl delete 1

/durl list	List all mod URLs	-> Example : /durl list

/durl clear	Remove all mod URLs	-> Example : /durl clear

Configuration

/durl file	Open config file	-> Example : /durl file

/durl clo Enable|Disable	Toggle connection logging	-> Example : /durl clo Disable

/durl banlog Enable|Disable	Toggle ban logging	-> Example : /durl banlog Enable

Information

/durl clo	Show connection logging status	-> Example : /durl clo

/durl banlog	Show ban logging status	-> Example : /durl banlog

# Troubleshooting
Common Issues
HTTPServer Not Starting :

Verify port 8000 is available

Check server logs for binding errors

Ensure no firewall is blocking the port

Configuration Not Saving :

Verify write permissions in mods/SDSetup

Check disk space availability

Look for errors in latest.log

Client Connection Issues

Verify server IP and port

Check for IP blocks (excessive requests)

Ensure client has network access to server

# Logging
Important log entries are prefixed with:

[Simpledownload][Event] - Server operations

[Simpledownload][Security] - Security events

[Simpledownload][CMD] - Command execution

# Development
Building from Source
Clone the repository

Set up Forge 1.16.5 MDK environment

Build with Gradle

Dependencies
Minecraft Forge 1.16.5

Google Gson (for JSON processing)

Java's built-in HTTP server

Architecture Overview
Main Class (Simpledownload.java)

Handles mod initialization

Manages HTTP server lifecycle

Processes commands

Request Handler (GetDurlDataHandler.java)

Implements rate limiting

Processes API requests

Manages security features

Extension Points
Enhance IP geolocation with proper API integration

Add authentication for API access

Implement download statistics tracking

Add support for mod version checking

# Contributing
We welcome contributions! Please follow these guidelines:

Fork the repository

Create a feature branch

Submit pull requests with:

Clear documentation

Appropriate test cases

Backward compatibility

Code Style
Follow Oracle Java Code Conventions

Use descriptive variable names

Include comments for complex logic

Maintain consistent indentation (4 spaces)

Testing
Please test any changes against:

Different network conditions

Various server response types

Edge cases in file handling
