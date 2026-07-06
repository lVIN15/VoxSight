# VoxSight

VoxSight is a mobile application designed to optimize independent sight-reading practice for amateur choir members. It allows users to upload a piece of music in MusicXML format, and the app will process it and provide feedback on their performance.

## Tech Stack
- Frontend: Kotlin (Android)
- Backend: Java Spring Boot (API)
- Music Processing: Python (Music21)
- Optical Music Recognition (OMR) Software used: Audiveris

## Prerequisites
- Android Studio installed (or any IDE that can run Kotlin projects)
- Java 17 installed
- Python installed
- Gradle installed (Android Studio has its own Gradle)
- Audiveris software installed and configured to run on your machine and stored on 'C:\\Program Files\\Audiveris'. 

## How to run the project
- Clone the repository using 'git clone https://github.com/lVIN15/VoxSight', if you haven't already. 
- Open the project in Android Studio or any IDE that can run Kotlin projects.
- Before running the app, examine 'mobile\\app\\src\\main\\java\\com\\kaido\\voxsight\\network\\ApiClient.kt', and replace the 'Base_URL' with your own IP address in the network (usually the same as the one used to run your java backend locally) 
- Run the app on a emulator or a connected device. 

## Dependencies Added (build.gradle.kts):
- com.squareup.retrofit2:retrofit:2.11.0
- com.squareup.retrofit2:converter-gson:2.11.0
- org.jetbrains.kotlinx:kotlinx-coroutines-android:1.7.3

## Added permissions
- android.permission.INTERNET

## Typical errors and solutions
"Image quality is too low. Please capture a clearer photo."
- Upload a clearer photo. Try capturing on a brighter surroundings, adjust lighting and angles, or using a different music sheet
"Network error {IP Address}"
- Check ApiClient.kt again and ensure the IP address is correct.
