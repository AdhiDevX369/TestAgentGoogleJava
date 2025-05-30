# Google ADK Multi-Tool Agent

A command-line agent built with Google ADK that provides real-time information about time zones and weather conditions for cities around the world.

## Features

- Get current time for any city or time zone
- Get current weather conditions including temperature, feels-like temperature, and humidity
- Interactive command-line interface

## Requirements

- Java 21 or higher
- Maven
- Google API Key
- OpenWeatherMap API Key (for weather functionality)

## Setup

1. Clone the repository

2. Set up environment variables:

```
# Required for Google ADK
GOOGLE_API_KEY=your_google_api_key
GOOGLE_GENAI_USE_VERTEXAI=FALSE

# Required for weather functionality
OPENWEATHERMAP_API_KEY=your_openweathermap_api_key
```

3. Build the project:

```
mvn clean compile
```

## Running the Application

You can run the application using Maven:

```
mvn exec:java
```

Or alternatively:

```
mvn compile exec:java -Dexec.mainClass=agents.Main
```

## Usage Examples

After starting the application, you can interact with the agent through the command line:

```
Agent initialized. Type 'quit' to exit.

You > What time is it in New York?
Agent > The current time in New York is 12:34.

You > What's the weather in Tokyo?
Agent > The weather in Tokyo: clear sky, with a temperature of 22.5°C (72.5°F). Feels like 21.8°C. Humidity is 65%.

You > quit
Session ended.
```

## Troubleshooting

- If you encounter issues with the weather functionality, ensure your OpenWeatherMap API key is set correctly
- If you see "Weather service is not configured (API key missing)" messages, check your environment variables
- For Maven-related issues, ensure you're using the correct command format for your shell