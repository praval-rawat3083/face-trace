# FaceTrace

FaceTrace is a Java desktop project that detects faces in photos, converts images into feature numbers, and ranks saved face vectors by similarity score. It provides a public photo-search screen and an admin screen for managing synthetic demo records.

Team IJP-V-2026-T003 - Kartik Rawat, Prawal Rawat, Sahil Kumar, Pratibha Manral.

## Features

- Detects faces with OpenCV's Haar cascade and prepares a 160 x 160 crop of the largest face.

- Creates a 4 x 4 grid of 256-bin LBP histograms, for a 4,096-number vector.

- Ranks cosine-similarity results and displays up to three candidates with scores.

- Stores demo records, face vectors, and search logs in MySQL.

- Provides a public JavaFX photo-search screen and a JavaFX admin screen.

- Protects admin access with BCrypt password hashes and an `ADMIN` role check.

- Lets admins add, edit, and delete synthetic records, with actions written to an audit log.

## Tech stack

| Technology | Version | Use |
| --- | --- | --- |
| Java | 17 | Application code |
| Maven | Project build | Dependencies, tests, and launch commands |
| OpenCV | 4.9.0 | Image reading, grayscale preparation, and face detection |
| MySQL with JDBC | MySQL Connector/J 8.4.0 | Records, vectors, and logs |
| JavaFX, FXML, CSS | 17.0.10 | Desktop screens and layout |
| jBCrypt | 0.4 | Admin password hashing and checks |
| JUnit Jupiter | 5.10.2 | Automated tests |

## Data and ethics

The project does not use real criminal records. Its benchmark uses the public AT&T/ORL face-image dataset; database names, case codes, and notes use synthetic `Subject-###` demo labels. The dataset contains photos of research participants, is ignored by Git, and is not committed to the repository.

## How it works

1. The app reads a photo as grayscale. The detector equalises its grayscale copy to help face detection.

1. A Haar cascade looks for faces and reports whether none, one, or multiple were found.

1. The detector returns the largest face cropped and resized to 160 x 160 for callers that need a normalized face image.

1. The current saved/search vector is computed from the full grayscale photo to stay compatible with the existing gallery. LBP compares each pixel with its neighbours and counts normalized 256-bin patterns in a 4 x 4 grid, producing 4,096 numbers.

1. Cosine similarity compares the probe vector with saved vectors, and the selected threshold filters candidates.

1. FaceTrace sorts candidates by score and shows up to three results.

## Project structure

```
src/main/java/facetrace/  # Main Java application packages
├── app/          # Application, demo, benchmark entry points
├── config/       # Local database connection settings
├── controller/   # JavaFX screen controllers
├── dao/          # SQL data access objects
├── match/        # Face similarity ranking
├── model/        # Application data records
├── service/      # Authentication and record services
└── vision/       # Face detection and LBP
src/main/resources/facetrace/view/  # FXML and CSS screens
src/test/                           # JUnit cases and test images
db/                                 # Database schemas and setup scripts
config/                             # Local database settings example
```

## Setup

1. Install JDK 17, Maven, and MySQL Server 8.0.

1. Clone the repository:

```bash
git clone https://github.com/praval-rawat3083/face-trace.git
cd face-trace
```

1. Create your local database settings and enter your own MySQL username and password:

```
Copy-Item .\config\config.example.properties .\config\database.properties
notepad .\config\database.properties
```

1. Create the databases and tables. Run from the project folder in PowerShell; enter your MySQL administrator username and password when prompted:

```
powershell -ExecutionPolicy Bypass -File .\db\create_database.ps1
```

1. Create the first FaceTrace admin account:

```
powershell -ExecutionPolicy Bypass -File .\db\create_first_admin.ps1
```

1. Download the AT&T/ORL dataset separately. Place folders `s1` through `s40` directly under the ignored `att_faces` folder:

```
New-Item -ItemType Directory -Force .\att_faces | Out-Null
```

## Running

Run these commands from the project folder in Command Prompt.

Run the automated tests:

```
mvn test
```

Start the JavaFX app:

```
mvn -q compile javafx:run
```

Run the benchmark. The dataset must contain `s1` through `s40`, each with `1.pgm` through `10.pgm`:

```
mvn -q compile exec:java -Dexec.mainClass=facetrace.app.Benchmark "-Dexec.args=%CD%\att_faces 0.0 benchmark-results.txt"
```

Phase2Demo enrolls dataset images and searches the specified probe; example: `mvn -q compile exec:java -Dexec.mainClass=facetrace.app.Phase2Demo "-Dexec.args=%CD%\att_faces %CD%\att_faces\s1\1.pgm 0.0"`.

## Results

The available benchmark report records these measured results:

| Measure | Result |
| --- | --- |
| Top-1 accuracy | 96.00% (384/400 ) |
| Top-3 accuracy | 99.50% (398/400) |
| Average search time | 2.2634 ms |
| Probe searches | 400 |
| Similarity threshold | 0.0000 |

This result comes from a small, controlled grayscale dataset of mostly front-facing images. LBP is sensitive to pose, lighting, expression, and alignment, so the benchmark is for classroom study and is not proof of identity.

## Limitations and future work

- With the default threshold of 0.0, the search returns the closest candidates even for an unknown face; it does not reliably reject unknown people.

- The benchmark is small and uses a controlled dataset with known subjects.

- LBP is a simple method and is affected by changes in pose, lighting, expression, and alignment.

- Another embedding method can be added through the existing `FaceEmbedder` interface.

- Admins do not yet have a screen for browsing audit and match history.

## Team contributions

| Area | Main classes | Owner |
| --- | --- | --- |
| Frontend (JavaFX) | `FaceTraceApplication`, `SearchController`, `AdminController`, `LoginController` | Sahil Kumar |
| Image engine | `FaceDetector`, `LbpEmbedder`, `FaceMatcher` | Kartik Rawat |
| Database | `DatabaseConnection`, `CriminalDao`, `FaceSearchDao`, `AdminAuditDao` | Praval Rawat |
| Security and testing | `AdminAuthService`, `RoleChecker`, JUnit tests | Pratibha Manral |
