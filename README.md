# Activity Tracker

This is the repository for Activity Tracker, an Android application that utilizes geofencing, location tracking, and activity recognition. It allows users to monitor specific geofenced locations, track real-time location with semantic address display, recognize user activities, and includes a step counter for tracking physical activity.

## Features

- Geofence monitoring for specific locations.
- Real-time location tracking with semantic address display.
- Activity recognition for detecting user activities such as walking, running, and more.
- Step counter to track the number of steps taken.

## Getting Started

To get started with the project, clone this repository:

```bash
git clone https://github.com/argrabowski/cs-528-project-3.git
```

## Permissions

The application requires the following permissions:

- Coarse location access
- Fine location access
- Background location access
- Activity recognition permission

## API Key

The project uses the Google Maps API. To run the application successfully, replace `${MAPS_API_KEY}` in the `AndroidManifest.xml` file with your actual Google Maps API key.

```xml
<meta-data
    android:name="com.google.android.geo.API_KEY"
    android:value="YOUR_GOOGLE_MAPS_API_KEY" />
```

## Build and Run

To build and run the project, open it in Android Studio and run it on an Android emulator or a physical device with developer options enabled.
