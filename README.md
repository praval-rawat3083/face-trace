# FaceTrace — Phase 2, Step 6

This Java 17 Maven project uses OpenCV, MySQL Connector/J, and JUnit 5. It reads local face images, builds pure-Java LBP vectors, and compares them with cosine similarity. The Phase 2 console demo enrolls local ORL-style folders using synthetic labels and writes search logs. The Step 6 benchmark evaluates the LBP matcher in memory and saves measured metrics as a text file. The ORL pictures are not copied into the project; database case details are clearly marked synthetic and are not real criminal records.

## Set up the local test database

MySQL Server must be running. In PowerShell, from the project folder, run the setup script:

```powershell
cd C:\Users\prati\Desktop\facetrace
powershell -ExecutionPolicy Bypass -File .\db\create_database.ps1
```

Run this command by itself and wait for the success message. At the username prompt, type only your MySQL administrator account name and press Enter; then enter the password at MySQL's hidden password prompt. Do not type the next PowerShell command until the script finishes and returns to the normal prompt. The script creates `facetrace` and `facetrace_test`, then applies `schema.sql` to both databases.

Edit the local, Git-ignored settings if needed:

```powershell
notepad .\config\database.properties
```

Use an account that can access both project databases. Tests use only `db.testName=facetrace_test`; the Phase 2 demo uses `db.name=facetrace`. The benchmark does not use the database.

## Run tests

```powershell
mvn test
```

The tests use generated fictional images and synthetic database rows. The PGM-reader test makes a tiny image in a temporary test folder and removes it afterward.

## GUI Phase - Sub-step A: Admin login

Sub-step A adds the admin login window and role check. Sub-step B adds public photo search. Sub-step C adds protected record management; audit and match history remain for Sub-step D. Database and image work runs in JavaFX background tasks so the window stays responsive.

From Command Prompt, in the project folder, run:

```cmd
cd /d C:\Users\prati\Desktop\facetrace
powershell -ExecutionPolicy Bypass -File .\db\create_database.ps1
powershell -ExecutionPolicy Bypass -File .\db\create_first_admin.ps1
mvn -q compile javafx:run
```

The database script reapplies the idempotent schema to `facetrace` and `facetrace_test`. The first-admin script asks for a username and a password twice; password entry is hidden, and the password must be at least 12 characters. It refuses to run if an admin already exists. The password is hashed with BCrypt and only the hash is stored in MySQL; no password is put in source code, command arguments, or a committed file. Make sure `config/database.properties` has the correct local database login first.

The login screen accepts only the `ADMIN` role and opens the protected records screen after successful sign-in. Successful sign-ins and record add, edit, and delete actions are written to `audit_log`; Sub-step D will add a screen for reading those logs.

If the first admin already exists and you need to choose a new password, do not rerun the first-admin script. Run this instead:

```cmd
powershell -ExecutionPolicy Bypass -File .\db\reset_admin_password.ps1
```

The reset command changes only the password hash for an existing `ADMIN` account. Password text is visible while you type, as requested, so use it only when nobody else can see your screen. The login window also has a `Show password` checkbox; uncheck it or submit the form to hide and clear the entry.

### Sub-step B: User search

The app now opens the search screen without requiring login. It reads the chosen photo in place, draws boxes around detected faces for the preview, and searches the `facetrace` database using the existing LBP vectors and cosine matcher. To stay compatible with Step 5, it calculates the probe vector from the full grayscale photo, as the gallery enrollment did. It shows at most three results with the saved name, case code, and measured score. The threshold field accepts a value from 0 to 1 and starts at `0.0`.

Run tests and then open the app from Command Prompt:

```cmd
cd /d C:\Users\prati\Desktop\facetrace
mvn test
mvn -q compile javafx:run
```

Searching does not need an admin login, but MySQL and `config/database.properties` must be ready. The normal `facetrace` database needs saved `LBP-4x4-256-v1` embeddings; the Phase2Demo enrollment you already ran supplies them. A bad image and a no-face image get clear messages. If several faces are found, the preview boxes them all and the full photo is compared; choose a single-face image for a clearer comparison. Only a completed face search is logged; the log stores the chosen file name, real best score, and any threshold-qualified best record id, not the image itself. The original photo is never changed or copied.

Use generated fictional test photos for synthetic-only testing, or other images you are permitted to process; do not use real criminal records. The earlier ORL benchmark images, if still enrolled in your local database, are real research-participant photos with synthetic `Subject-###` labels and synthetic case details. They are not included or copied by this search screen.

### Sub-step C: Admin record management

After signing in with the admin account created in Sub-step A, the screen loads records from MySQL. Add requires a demo case code (`DEMO-###` or `ORL-DEMO-###`), a synthetic name (`Subject-###`), a fictional note, a generated synthetic photo containing exactly one detectable face, and confirmation that the details and photo are synthetic. The app stores the photo's local path, calculates the real LBP vector from the full grayscale photo, and saves the record, vector, and audit entry in one database transaction. It does not copy the photo into the project.

Select a table row to edit its synthetic details; the existing photo and vector stay linked. Delete asks for confirmation, removes the record and its linked vector, and keeps the audit entry. Successful sign-ins, record-list loads, and Add, Update, and Delete actions are logged; selecting a row is read-only. Use generated fictional photos, not real people or celebrities. Existing ORL images are research-participant photos and should not be used as new synthetic admin uploads.

Run tests and then start the app from Command Prompt or PowerShell:

```cmd
cd /d C:\Users\prati\Desktop\facetrace
mvn test
mvn -q compile javafx:run
```

MySQL must be running, `config/database.properties` must connect to the `facetrace` database, and a first admin must already exist. The Add image check and all database work run on background tasks, so the window remains responsive. If Maven fails because a plugin cannot be downloaded, connect to the internet and rerun the same command.

#### Manual Sub-step C test

1. Sign in with your existing admin account. Confirm that the table shows records already saved in MySQL.
2. Add a test row using the generated photo `src\test\resources\facetrace\synthetic-single.jpg`. If `DEMO-041` already exists, choose another unused three-digit subject/case pair. Example test-only values: case `DEMO-041`, name `Subject-041`, age `25`, note `Synthetic UI test`; tick the synthetic confirmation box and click **Add**.
3. In MySQL Workbench, verify that the app computed and stored one embedding for that row:

```sql
USE facetrace;
SELECT r.id, r.case_code, COUNT(e.id) AS vector_count, MAX(e.embedder_name) AS embedder
FROM criminal_records r
LEFT JOIN face_embeddings e ON e.record_id = r.id
WHERE r.case_code = 'DEMO-041'
GROUP BY r.id, r.case_code;
```

The query should show the row actually created and its stored vector; do not expect a particular database id. Select it in the app, change the fictional note, confirm synthetic data, and click **Update**. Then check the audit entries:

```sql
SELECT action, entity_type, entity_id, details, created_at
FROM audit_log
WHERE action IN ('ADMIN_LOGIN', 'RECORDS_VIEW') OR details LIKE '%DEMO-041%'
ORDER BY id DESC
LIMIT 20;
```

4. Click **Delete selected**, cancel once and confirm the row remains; repeat and confirm the deletion. Finally run `SELECT COUNT(*) FROM criminal_records WHERE case_code = 'DEMO-041';` and confirm the remaining count is zero. The `RECORD_DELETE` audit entry should still exist. This test adds then removes only the fictional test row; use no real person's photo or details.

## Run the Phase 2 demo

The dataset folder should contain subject folders named `s1`, `s2`, and so on, with `.pgm` images inside. The probe image is passed separately. From Command Prompt, replace the example paths with your actual locations:

```cmd
cd /d C:\Users\prati\Desktop\facetrace
mvn -q compile exec:java -Dexec.mainClass=facetrace.app.Phase2Demo "-Dexec.args=C:\Users\prati\Desktop\facetrace\att_faces C:\Users\prati\Desktop\facetrace\att_faces\s1\1.pgm 0.0"
```

The optional last argument is the similarity threshold from 0 to 1. A probe inside the dataset is excluded from enrollment so it cannot match itself. The program writes marked-synthetic `Subject-###` records, embeddings, and search logs to MySQL. It does not copy original photos into the project.

## Run the Step 6 benchmark

The benchmark needs all 40 folders (`s1` through `s40`) with images `1.pgm` through `10.pgm` in each folder. Run this from Command Prompt; the last argument is the threshold used, and the report path is optional:

```cmd
cd /d C:\Users\prati\Desktop\facetrace
mvn -q compile exec:java -Dexec.mainClass=facetrace.app.Benchmark "-Dexec.args=C:\Users\prati\Desktop\facetrace\att_faces 0.0 benchmark-results.txt"
```

The benchmark runs ten rounds. In each round it holds out the same-numbered image from every subject and enrolls the other nine. That produces 400 probe searches in total. It works in memory and does not change MySQL. `benchmark-results.txt` contains the measured threshold, top-1 accuracy, top-3 accuracy, average search time, and plain-language notes; the file is recreated each run.

Top-1 accuracy is the share of probes whose correct subject ranked first. Top-3 accuracy is the share where the correct subject appeared in the first three results. A probe with no result above the threshold counts as incorrect. Average milliseconds cover probe LBP calculation and ranking; they do not include loading images or preparing the gallery.

This is a small, controlled grayscale dataset with only known subjects in the gallery. LBP is a simple texture method and can be affected by pose, lighting, expression, and alignment. These numbers are a classroom benchmark, not proof of identity or a guarantee for new people.

## Files

- `pom.xml` — Java 17, JavaFX/FXML, BCrypt, OpenCV, MySQL Connector/J, JUnit 5, and the Maven launch plugins.
- `.gitignore` — excludes build output, personal images, the downloaded `att_faces` data, and local database credentials.
- `db/create_database.sql` — creates separate `facetrace` and `facetrace_test` databases.
- `db/create_database.ps1` — prompts for MySQL admin login and applies the SQL setup files.
- `db/schema.sql` — creates `criminal_records`, `face_embeddings`, `match_logs`, `admins`, and `audit_log` tables.
- `db/create_first_admin.ps1` — starts the hidden first-admin password prompt without putting a password in the command.
- `db/reset_admin_password.ps1` — starts the requested password reset for an existing ADMIN account.
- `config/config.example.properties` — shows the local database settings without real credentials.
- `config/database.properties` — local ignored host, database, user, and password settings.
- `src/main/java/facetrace/config/DatabaseConnection.java` — reads local settings and opens normal or test connections.
- `src/main/java/facetrace/model/CriminalRecord.java` — holds one row's id, case code, synthetic name, age, label, and photo path.
- `src/main/java/facetrace/model/AdminSession.java` — holds only the signed-in admin's id, username, and role.
- `src/main/java/facetrace/dao/CriminalDao.java` — performs add, lookup, list, update, and delete with prepared statements.
- `src/test/java/facetrace/CriminalDaoTest.java` — tests all five DAO methods using synthetic rows in `facetrace_test`.
- `src/main/java/facetrace/app/FaceTraceApplication.java` — starts the public search screen; admin sign-in remains available.
- `src/main/java/facetrace/app/CreateFirstAdmin.java` — asks for the first password, makes a BCrypt hash, and inserts one admin.
- `src/main/java/facetrace/app/ResetAdminPassword.java` — replaces an existing ADMIN password with a BCrypt hash.
- `src/main/java/facetrace/controller/LoginController.java` — checks the role, logs successful sign-in, and opens the protected admin records view.
- `src/main/java/facetrace/controller/AdminController.java` — connects the admin records FXML buttons to small screen helpers.
- `src/main/java/facetrace/controller/AdminRecordActions.java` — handles add, edit, delete confirmation, and photo selection.
- `src/main/java/facetrace/controller/AdminRecordTableSupport.java` — loads and displays saved records without blocking the window.
- `src/main/java/facetrace/controller/AdminTaskRunner.java` — runs write tasks on background threads and restores the controls.
- `src/main/java/facetrace/controller/SearchController.java` — chooses photos, runs background searches, and updates the preview and table.
- `src/main/java/facetrace/dao/FaceSearchDao.java` — reads saved LBP vectors and logs one actual result per completed search.
- `src/main/java/facetrace/service/FaceSearchService.java` — reuses face detection, LBP, and cosine ranking for the GUI.
- `src/main/java/facetrace/service/AdminAuthService.java` — checks BCrypt credentials from the admins table.
- `src/main/java/facetrace/service/AdminRecordService.java` — checks admin access and saves record changes with audit entries.
- `src/main/java/facetrace/service/AdminPhotoEnrollment.java` — requires one detected face and calculates its compatible LBP vector.
- `src/main/java/facetrace/service/AdminSqlWork.java` — defines the small database task used inside service transactions.
- `src/main/java/facetrace/dao/AdminAuditDao.java` — writes successful admin actions using prepared SQL.
- `src/main/java/facetrace/dao/AdminEmbeddingDao.java` — stores a measured LBP vector in the same format as Phase2Demo.
- `src/main/java/facetrace/service/RoleChecker.java` — allows only an `ADMIN` session to enter admin-only areas.
- `src/main/resources/facetrace/view/LoginView.fxml` — lays out the admin login fields and header.
- `src/main/resources/facetrace/view/SearchView.fxml` — lays out the public photo search, boxed preview, and results table.
- `src/main/resources/facetrace/view/AdminView.fxml` — lays out the synthetic-record form and saved-record table.
- `src/main/resources/facetrace/view/admin.css` — styles the admin screen in the same plain navy, grey, white, and blue theme.
- `src/main/resources/facetrace/view/search.css` — gives the search screen the same plain navy, grey, white, and blue style.
- `src/main/resources/facetrace/view/login.css` — sets the requested plain navy, grey, white, and blue style.
- `src/test/java/facetrace/service/RoleCheckerTest.java` — checks that ADMIN is accepted and other or missing sessions are blocked.
- `src/main/java/facetrace/vision/FaceDetector.java` — finds faces, reports results, and prepares a 160 by 160 crop.
- `src/main/java/facetrace/vision/FaceDetectorRunner.java` — runs detection from the console and saves a boxed copy.
- `src/main/java/facetrace/vision/FaceEmbedder.java` — defines the common interface that returns float features for an image.
- `src/main/java/facetrace/vision/LbpEmbedder.java` — counts simple LBP patterns in a 4 by 4 grid and returns 4096 floats.
- `src/main/java/facetrace/match/MatchResult.java` — holds a candidate case code and its calculated similarity.
- `src/main/java/facetrace/match/FaceMatcher.java` — calculates cosine scores, filters by threshold, and returns top-K.
- `src/main/java/facetrace/app/Phase2Demo.java` — enrolls PGM embeddings, searches a probe, prints scores, and logs the search.
- `src/main/java/facetrace/app/Benchmark.java` — runs ten-fold leave-one-out, calculates top-1/top-3 and timing, and saves a report.
- `benchmark-results.txt` — measured output generated by running the benchmark command.
- `src/main/resources/haarcascade_frontalface_default.xml` — OpenCV's frontal-face detection pattern file.
- `src/test/java/facetrace/FaceDetectorTest.java` — checks no-face, one-face, and multiple-face images.
- `src/test/java/facetrace/FaceMatcherTest.java` — tests same-image, different-image, threshold, and top-K behavior.
- `src/test/java/facetrace/app/Phase2DemoTest.java` — tests PGM reading with a tiny generated file.
- `src/test/resources/facetrace/synthetic-single.jpg` — generated fictional one-face test image.
- `src/test/resources/facetrace/synthetic-pair.jpg` — generated fictional two-face test image.
- `src/test/java/facetrace/OpenCvImageLoadTest.java` — checks that OpenCV can load a generated image.
