package facetrace.controller;

import facetrace.model.Student;
import facetrace.service.FaceSearchService;
import facetrace.service.StudentEnrollmentService;
import facetrace.vision.WebcamService;
import javafx.application.Platform;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TabPane;
import javafx.scene.control.TextField;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;
import org.opencv.core.Mat;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;

/** Controller for the Smart Attendance Kiosk supporting check-in and student self-registration. */
public final class SearchController {
    private final FaceSearchService searchService = new FaceSearchService();
    private final StudentEnrollmentService enrollmentService = new StudentEnrollmentService();
    private final WebcamService webcamService = new WebcamService();

    // Tab pane
    @FXML private TabPane mainTabPane;

    // Check-in tab controls
    @FXML private Button choosePhotoButton;
    @FXML private Button webcamToggleButton;
    @FXML private Button searchButton;
    @FXML private Button adminLoginButton;
    @FXML private Label selectedFileLabel;
    @FXML private Label statusLabel;
    @FXML private TextField thresholdField;
    @FXML private ComboBox<String> subjectComboBox;
    @FXML private ImageView previewImageView;
    @FXML private TableView<FaceSearchService.SearchHit> resultsTable;
    @FXML private TableColumn<FaceSearchService.SearchHit, String> nameColumn;
    @FXML private TableColumn<FaceSearchService.SearchHit, String> caseColumn;
    @FXML private TableColumn<FaceSearchService.SearchHit, String> scoreColumn;

    // Self-registration tab controls
    @FXML private Button regWebcamToggleButton;
    @FXML private Button regSnapButton;
    @FXML private Button regChoosePhotoButton;
    @FXML private TextField regStudentIdField;
    @FXML private TextField regFullNameField;
    @FXML private TextField regAgeField;
    @FXML private TextField regDeptField;
    @FXML private Label regPhotoStatusLabel;
    @FXML private Label regStatusLabel;
    @FXML private CheckBox regConsentCheckbox;
    @FXML private ImageView regPreviewImageView;
    @FXML private Button registerStudentButton;

    private Path selectedCheckInPhoto;
    private Path selectedRegPhoto;
    private Mat capturedRegFrame;

    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        caseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().caseCode()));
        scoreColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().percentage()));
        resultsTable.setPlaceholder(new Label("No verification results yet. Start webcam or select a photo."));

        // Initialize Lecture / Subject list with authentic BCA curriculum
        subjectComboBox.setItems(FXCollections.observableArrayList(
                "Java Programming (BCA-301)",
                "Data Structures & Algorithms (BCA-302)",
                "Database Management Systems (BCA-303)",
                "Web Technologies (BCA-304)",
                "Computer Networks (BCA-305)",
                "Operating Systems (BCA-306)",
                "Software Engineering (BCA-307)",
                "Python Programming (BCA-308)",
                "General Attendance"
        ));
        subjectComboBox.getSelectionModel().selectFirst();

        // Stop camera cleanly when switching between tabs
        if (mainTabPane != null) {
            mainTabPane.getSelectionModel().selectedIndexProperty().addListener((obs, oldIdx, newIdx) -> {
                if (webcamService.isRunning()) {
                    webcamService.stopCamera();
                    webcamToggleButton.setText("📷 Start Live Webcam");
                    webcamToggleButton.setStyle("-fx-background-color: #3b82f6;");
                    regWebcamToggleButton.setText("📷 Start Camera");
                    regWebcamToggleButton.setStyle("-fx-background-color: #3b82f6;");
                    regPhotoStatusLabel.setText("Camera stopped. Click 'Start Camera' to preview.");
                }
            });
        }

        // Safely stop webcam on window close
        Platform.runLater(() -> {
            if (previewImageView.getScene() != null && previewImageView.getScene().getWindow() != null) {
                previewImageView.getScene().getWindow().setOnCloseRequest(e -> {
                    webcamService.stopCamera();
                    if (capturedRegFrame != null) {
                        capturedRegFrame.release();
                    }
                });
            }
        });
    }

    /** Toggles the live camera stream on/off. */
    @FXML
    private void toggleWebcam() {
        if (webcamService.isRunning()) {
            webcamService.stopCamera();
            webcamToggleButton.setText("📷 Start Live Webcam");
            webcamToggleButton.setStyle("-fx-background-color: #3b82f6;");
            statusLabel.setText("Webcam stopped. You can select a photo file or restart webcam.");
        } else {
            selectedCheckInPhoto = null;
            selectedFileLabel.setText("Live Webcam Active");
            statusLabel.setText("Starting live camera feed with real-time face tracking...");
            boolean started = webcamService.startCamera(
                    img -> previewImageView.setImage(img),
                    err -> statusLabel.setText(err)
            );
            if (started) {
                webcamToggleButton.setText("⏹ Stop Webcam");
                webcamToggleButton.setStyle("-fx-background-color: #ef4444;");
                searchButton.setText("⚡ Snap & Mark Attendance");
                statusLabel.setText("Webcam is live! Look into the camera and click 'Snap & Mark Attendance'.");
            }
        }
    }

    /** Lets user choose a photo file as an alternative to webcam. */
    @FXML
    private void choosePhoto() {
        if (webcamService.isRunning()) {
            webcamService.stopCamera();
            webcamToggleButton.setText("📷 Start Live Webcam");
            webcamToggleButton.setStyle("-fx-background-color: #3b82f6;");
        }
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose photo for attendance check-in");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.pgm"));
        File selected = chooser.showOpenDialog(choosePhotoButton.getScene().getWindow());
        if (selected == null) {
            return;
        }
        selectedCheckInPhoto = selected.toPath().toAbsolutePath().normalize();
        selectedFileLabel.setText(selected.getName());
        previewImageView.setImage(null);
        resultsTable.getItems().clear();
        searchButton.setText("✔ Mark Attendance");
        statusLabel.setText("Photo selected. Click 'Mark Attendance' to verify.");
        searchButton.setDisable(false);
    }

    /** Triggers biometric verification using either live webcam frame or selected photo. */
    @FXML
    private void search() {
        double threshold;
        try {
            threshold = Double.parseDouble(thresholdField.getText().trim());
        } catch (NumberFormatException invalidNumber) {
            statusLabel.setText("Threshold must be a number from 0 to 1 (recommended: 0.70).");
            return;
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            statusLabel.setText("Threshold must be a valid number between 0 and 1.");
            return;
        }

        String subject = subjectComboBox.getValue();
        if (subject == null || subject.isBlank()) subject = "General";

        if (webcamService.isRunning()) {
            Mat snapshot = webcamService.getLastFrameCopy();
            if (snapshot == null || snapshot.empty()) {
                statusLabel.setText("Waiting for camera frame. Ensure webcam is connected.");
                return;
            }
            processSnapshot(snapshot, threshold, subject);
        } else {
            if (selectedCheckInPhoto == null) {
                statusLabel.setText("Please either start the live webcam or select a photo first.");
                return;
            }
            processFile(selectedCheckInPhoto, threshold, subject);
        }
    }

    private void processSnapshot(Mat snapshot, double threshold, String subject) {
        statusLabel.setText("Verifying face texture, checking liveness, and recording attendance...");
        searchButton.setDisable(true);
        resultsTable.getItems().clear();

        Task<FaceSearchService.SearchResponse> task = new Task<>() {
            @Override
            protected FaceSearchService.SearchResponse call() throws Exception {
                try {
                    return searchService.searchMat(snapshot, threshold, "LIVE_WEBCAM_CAPTURE", subject);
                } finally {
                    snapshot.release();
                }
            }
        };
        task.setOnSucceeded(event -> {
            searchButton.setDisable(false);
            FaceSearchService.SearchResponse response = task.getValue();
            if (response.previewPng() != null) {
                previewImageView.setImage(new Image(new ByteArrayInputStream(response.previewPng())));
            }
            resultsTable.getItems().setAll(response.hits());
            statusLabel.setText(response.message());
        });
        task.setOnFailed(event -> {
            searchButton.setDisable(false);
            statusLabel.setText("Verification could not finish. Check MySQL/SQLite connection.");
        });
        Thread worker = new Thread(task, "webcam-verification-worker");
        worker.setDaemon(true);
        worker.start();
    }

    private void processFile(Path photo, double threshold, String subject) {
        statusLabel.setText("Analyzing photo and checking liveness...");
        searchButton.setDisable(true);
        resultsTable.getItems().clear();

        Task<FaceSearchService.SearchResponse> task = new Task<>() {
            @Override
            protected FaceSearchService.SearchResponse call() throws Exception {
                return searchService.search(photo, threshold, subject);
            }
        };
        task.setOnSucceeded(event -> {
            searchButton.setDisable(false);
            FaceSearchService.SearchResponse response = task.getValue();
            if (response.previewPng() != null) {
                previewImageView.setImage(new Image(new ByteArrayInputStream(response.previewPng())));
            }
            resultsTable.getItems().setAll(response.hits());
            statusLabel.setText(response.message());
        });
        task.setOnFailed(event -> {
            searchButton.setDisable(false);
            statusLabel.setText("Verification error. Check database.");
        });
        Thread worker = new Thread(task, "file-verification-worker");
        worker.setDaemon(true);
        worker.start();
    }

    // --- STUDENT SELF-REGISTRATION METHODS ---

    @FXML
    private void toggleRegWebcam() {
        if (regWebcamToggleButton.getText().contains("Retake")) {
            if (!webcamService.isRunning()) {
                webcamService.startCamera(
                        img -> regPreviewImageView.setImage(img),
                        err -> regStatusLabel.setText(err)
                );
            } else {
                webcamService.setFrameCallback(img -> regPreviewImageView.setImage(img));
            }
            regWebcamToggleButton.setText("⏹ Stop Camera");
            regWebcamToggleButton.setStyle("-fx-background-color: #ef4444;");
            regPhotoStatusLabel.setText("Live camera streaming. Position face and click '⚡ Snap Photo'.");
            regStatusLabel.setText("Ready for retake! Click '⚡ Snap Photo' when ready.");
            return;
        }

        if (webcamService.isRunning()) {
            webcamService.stopCamera();
            regWebcamToggleButton.setText("📷 Start Camera");
            regWebcamToggleButton.setStyle("-fx-background-color: #3b82f6;");
            regPhotoStatusLabel.setText("Camera stopped.");
            regStatusLabel.setText("Camera stopped. Click 'Start Camera' to resume live preview.");
        } else {
            selectedRegPhoto = null;
            regStatusLabel.setText("Starting live camera feed for registration...");
            boolean started = webcamService.startCamera(
                    img -> regPreviewImageView.setImage(img),
                    err -> regStatusLabel.setText(err)
            );
            if (started) {
                regWebcamToggleButton.setText("⏹ Stop Camera");
                regWebcamToggleButton.setStyle("-fx-background-color: #ef4444;");
                regPhotoStatusLabel.setText("Live camera streaming. Look at the camera and click '⚡ Snap Photo'.");
                regStatusLabel.setText("Position your face within the frame, then click '⚡ Snap Photo'.");
            }
        }
    }

    @FXML
    private void snapForRegistration() {
        if (!webcamService.isRunning()) {
            boolean started = webcamService.startCamera(
                    img -> regPreviewImageView.setImage(img),
                    err -> regStatusLabel.setText(err)
            );
            if (started) {
                regWebcamToggleButton.setText("⏹ Stop Camera");
                regWebcamToggleButton.setStyle("-fx-background-color: #ef4444;");
                regPhotoStatusLabel.setText("Live camera started! Look at the camera and click '⚡ Snap Photo' now.");
                regStatusLabel.setText("Camera warming up... click '⚡ Snap Photo' once your face is framed.");
            }
            return;
        }

        Mat snapshot = webcamService.getLastFrameCopy();
        if (snapshot == null || snapshot.empty()) {
            regStatusLabel.setText("Waiting for clear frame... please click '⚡ Snap Photo' again.");
            return;
        }

        // Pause live feed to freeze snapshot
        webcamService.setFrameCallback(null);

        if (capturedRegFrame != null) {
            capturedRegFrame.release();
        }
        capturedRegFrame = snapshot;
        selectedRegPhoto = null;

        Image img = WebcamService.matToImage(snapshot);
        if (img != null) {
            regPreviewImageView.setImage(img);
        }
        regPhotoStatusLabel.setText("✔ Face snapshot captured! Ready to enroll.");
        regStatusLabel.setText("Snapshot ready! Now verify your details and click 'Register & Enroll Profile'.");
        regWebcamToggleButton.setText("📷 Retake Photo");
        regWebcamToggleButton.setStyle("-fx-background-color: #3b82f6;");
    }

    @FXML
    private void choosePhotoForRegistration() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose photo for student self-registration");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.pgm"));
        File selected = chooser.showOpenDialog(choosePhotoButton.getScene().getWindow());
        if (selected == null) return;

        selectedRegPhoto = selected.toPath().toAbsolutePath().normalize();
        if (capturedRegFrame != null) {
            capturedRegFrame.release();
            capturedRegFrame = null;
        }

        try (FileInputStream fis = new FileInputStream(selected)) {
            regPreviewImageView.setImage(new Image(fis));
            regPhotoStatusLabel.setText("Selected file: " + selected.getName());
            regStatusLabel.setText("Photo loaded. Fill in details and click 'Register & Enroll'.");
        } catch (Exception ex) {
            regStatusLabel.setText("Could not load image: " + ex.getMessage());
        }
    }

    @FXML
    private void registerStudent() {
        String studentId = regStudentIdField.getText();
        String fullName = regFullNameField.getText();
        String ageText = regAgeField.getText();
        String dept = regDeptField.getText();

        if (studentId == null || studentId.isBlank()) {
            regStatusLabel.setText("Enter a valid Student ID / Roll Number.");
            return;
        }
        if (fullName == null || fullName.isBlank()) {
            regStatusLabel.setText("Enter full student name.");
            return;
        }
        int age;
        try {
            age = Integer.parseInt(ageText.trim());
        } catch (Exception ex) {
            regStatusLabel.setText("Age/Semester must be a valid number.");
            return;
        }
        if (dept == null || dept.isBlank()) {
            regStatusLabel.setText("Enter department or branch.");
            return;
        }
        if (!regConsentCheckbox.isSelected()) {
            regStatusLabel.setText("Please check the biometric consent checkbox.");
            return;
        }
        if (capturedRegFrame == null && selectedRegPhoto == null) {
            regStatusLabel.setText("Please capture a photo from webcam or choose an image file.");
            return;
        }

        regStatusLabel.setText("Extracting 4,096-dim LBP face vector and saving profile...");
        registerStudentButton.setDisable(true);

        Task<Student> task = new Task<>() {
            @Override
            protected Student call() throws Exception {
                if (capturedRegFrame != null) {
                    return enrollmentService.enrollFromCameraFrame(studentId, fullName, age, dept, capturedRegFrame);
                } else {
                    return enrollmentService.enroll(studentId, fullName, age, dept, selectedRegPhoto);
                }
            }
        };

        task.setOnSucceeded(event -> {
            registerStudentButton.setDisable(false);
            Student enrolled = task.getValue();
            regStatusLabel.setText("🎉 Enrolled successfully: " + enrolled.getFullName() + " (" + enrolled.getStudentId() + ")!");

            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Registration Successful");
            alert.setHeaderText("Welcome, " + enrolled.getFullName() + "!");
            alert.setContentText("Your biometric face profile is registered successfully under Roll No: "
                    + enrolled.getStudentId() + " (" + enrolled.getDepartment() + ").\nYou can now mark attendance!");
            alert.showAndWait();

            // Clear registration form
            regStudentIdField.clear();
            regFullNameField.clear();
            regAgeField.clear();
            regDeptField.clear();
            regConsentCheckbox.setSelected(false);
            regPhotoStatusLabel.setText("No photo captured yet");
        });

        task.setOnFailed(event -> {
            registerStudentButton.setDisable(false);
            Throwable ex = task.getException();
            regStatusLabel.setText("Registration error: " + (ex != null ? ex.getMessage() : "Unknown error"));
        });

        Thread t = new Thread(task, "student-self-reg-worker");
        t.setDaemon(true);
        t.start();
    }

    @FXML
    private void openAdminLogin() throws IOException {
        URL view = getClass().getResource("/facetrace/view/LoginView.fxml");
        if (view == null) {
            throw new IOException("The administrator login view is missing");
        }
        Stage stage = new Stage();
        stage.setTitle("FaceTrace - Student Administration Login");
        stage.setScene(new Scene(FXMLLoader.load(view), 500, 430));
        stage.initOwner(adminLoginButton.getScene().getWindow());
        stage.show();
    }
}
