package facetrace.controller;

import facetrace.service.FaceSearchService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.beans.property.ReadOnlyStringWrapper;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Path;
import java.util.Locale;

/** Connects photo selection and the background face search to the FXML screen. */
public final class SearchController {
    private final FaceSearchService searchService = new FaceSearchService();
    private Path selectedPhoto;

    @FXML private Button choosePhotoButton;
    @FXML private Button searchButton;
    @FXML private Button adminLoginButton;
    @FXML private Label selectedFileLabel;
    @FXML private Label statusLabel;
    @FXML private TextField thresholdField;
    @FXML private ImageView previewImageView;
    @FXML private TableView<FaceSearchService.SearchHit> resultsTable;
    @FXML private TableColumn<FaceSearchService.SearchHit, String> nameColumn;
    @FXML private TableColumn<FaceSearchService.SearchHit, String> caseColumn;
    @FXML private TableColumn<FaceSearchService.SearchHit, String> scoreColumn;

    /** Connects table columns to the search values. */
    @FXML
    private void initialize() {
        nameColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().name()));
        caseColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(cell.getValue().caseCode()));
        scoreColumn.setCellValueFactory(cell -> new ReadOnlyStringWrapper(
                String.format(Locale.ROOT, "%.4f", cell.getValue().score())));
        resultsTable.setPlaceholder(new Label("No search results yet."));
    }

    /** Lets the user choose a local image without copying it into the project. */
    @FXML
    private void choosePhoto() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Choose one face photo");
        chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Image files", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.pgm"));
        File selected = chooser.showOpenDialog(choosePhotoButton.getScene().getWindow());
        if (selected == null) {
            return;
        }
        selectedPhoto = selected.toPath().toAbsolutePath().normalize();
        selectedFileLabel.setText(selected.getName());
        previewImageView.setImage(null);
        resultsTable.getItems().clear();
        statusLabel.setText("Ready to search. Only the selected file name is written to the search log.");
        searchButton.setDisable(false);
    }

    /** Validates the threshold, then runs OpenCV, LBP, and database work off the UI thread. */
    @FXML
    private void search() {
        if (selectedPhoto == null) {
            statusLabel.setText("Choose an image file first.");
            return;
        }
        double threshold;
        try {
            threshold = Double.parseDouble(thresholdField.getText().trim());
        } catch (NumberFormatException invalidNumber) {
            statusLabel.setText("Threshold must be a number from 0 to 1.");
            return;
        }
        if (!Double.isFinite(threshold) || threshold < 0.0 || threshold > 1.0) {
            statusLabel.setText("Threshold must be a number from 0 to 1.");
            return;
        }

        Path photo = selectedPhoto;
        resultsTable.getItems().clear();
        previewImageView.setImage(null);
        statusLabel.setText("Reading photo and comparing saved vectors...");
        searchButton.setDisable(true);
        choosePhotoButton.setDisable(true);

        Task<FaceSearchService.SearchResponse> task = new Task<>() {
            @Override
            protected FaceSearchService.SearchResponse call() throws Exception {
                return searchService.search(photo, threshold);
            }
        };
        task.setOnSucceeded(event -> {
            searchButton.setDisable(false);
            choosePhotoButton.setDisable(false);
            FaceSearchService.SearchResponse response = task.getValue();
            if (response.previewPng() != null) {
                Image preview = new Image(new ByteArrayInputStream(response.previewPng()));
                if (!preview.isError()) {
                    previewImageView.setImage(preview);
                }
            }
            resultsTable.getItems().setAll(response.hits());
            statusLabel.setText(response.message());
        });
        task.setOnFailed(event -> {
            searchButton.setDisable(false);
            choosePhotoButton.setDisable(false);
            statusLabel.setText("Search could not finish. Check the image, Haar cascade, MySQL, and saved LBP vectors.");
        });
        Thread worker = new Thread(task, "facetrace-user-search");
        worker.setDaemon(true);
        worker.start();
    }

    /** Opens the existing administrator login in a separate window. */
    @FXML
    private void openAdminLogin() throws IOException {
        URL view = getClass().getResource("/facetrace/view/LoginView.fxml");
        if (view == null) {
            throw new IOException("The administrator login view is missing");
        }
        Stage stage = new Stage();
        stage.setTitle("FaceTrace - Administrator Login");
        stage.setScene(new Scene(FXMLLoader.load(view), 500, 430));
        stage.initOwner(adminLoginButton.getScene().getWindow());
        stage.show();
    }
}
