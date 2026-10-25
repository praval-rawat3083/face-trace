package facetrace.controller;

import facetrace.model.AdminSession;
import facetrace.service.AdminAuthService;
import facetrace.service.AdminRecordService;
import facetrace.service.RoleChecker;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.Arrays;
import java.util.Optional;

/** Connects the FXML login fields to database authentication without blocking the window. */
public final class LoginController {
    private final AdminAuthService authService = new AdminAuthService();
    private final AdminRecordService recordService = new AdminRecordService();
    private final RoleChecker roleChecker = new RoleChecker();

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private TextField visiblePasswordField;
    @FXML private CheckBox showPasswordCheckBox;
    @FXML private Button loginButton;
    @FXML private Label statusLabel;

    /** Copies text between the masked and visible fields when the checkbox changes. */
    @FXML
    private void togglePasswordVisibility() {
        if (showPasswordCheckBox.isSelected()) {
            visiblePasswordField.setText(passwordField.getText());
            passwordField.clear();
            passwordField.setVisible(false);
            passwordField.setManaged(false);
            visiblePasswordField.setVisible(true);
            visiblePasswordField.setManaged(true);
            visiblePasswordField.requestFocus();
            visiblePasswordField.positionCaret(visiblePasswordField.getText().length());
        } else {
            passwordField.setText(visiblePasswordField.getText());
            visiblePasswordField.clear();
            visiblePasswordField.setVisible(false);
            visiblePasswordField.setManaged(false);
            passwordField.setVisible(true);
            passwordField.setManaged(true);
            passwordField.requestFocus();
            passwordField.positionCaret(passwordField.getText().length());
        }
    }

    /** Starts an off-screen worker for the database and BCrypt work. */
    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String typedText = showPasswordCheckBox.isSelected()
                ? visiblePasswordField.getText() : passwordField.getText();
        char[] password = typedText.toCharArray();
        clearPasswordFields();
        if (username.isBlank() || password.length == 0) {
            Arrays.fill(password, '\0');
            statusLabel.setText("Enter both your username and password.");
            return;
        }

        statusLabel.setText("Checking account...");
        loginButton.setDisable(true);
        Task<Optional<AdminSession>> loginTask = new Task<>() {
            @Override
            protected Optional<AdminSession> call() throws Exception {
                Optional<AdminSession> result = authService.authenticate(username, password);
                if (result.isPresent()) {
                    AdminSession session = result.get();
                    try {
                        roleChecker.requireAdmin(session);
                    } catch (SecurityException notAdmin) {
                        return Optional.empty();
                    }
                    recordService.logAdminLogin(session);
                }
                return result;
            }
        };
        loginTask.setOnSucceeded(event -> {
            loginButton.setDisable(false);
            AdminSession session = loginTask.getValue().orElse(null);
            try {
                roleChecker.requireAdmin(session);
                openAdminRecords(session);
            } catch (SecurityException notAdmin) {
                statusLabel.setText("Sign-in failed. Check the username, password, and admin role.");
            } catch (IOException | RuntimeException loadFailure) {
                statusLabel.setText("Sign-in succeeded, but the admin screen could not open.");
            }
        });
        loginTask.setOnFailed(event -> {
            loginButton.setDisable(false);
            statusLabel.setText("Could not sign in. Check MySQL and config/database.properties.");
        });

        Thread worker = new Thread(loginTask, "facetrace-admin-login");
        worker.setDaemon(true);
        worker.start();
    }

    /** Replaces the login view with the protected admin records screen. */
    private void openAdminRecords(AdminSession session) throws IOException {
        var view = getClass().getResource("/facetrace/view/AdminView.fxml");
        if (view == null) {
            throw new IOException("AdminView.fxml is missing.");
        }
        FXMLLoader loader = new FXMLLoader(view);
        Parent root = loader.load();
        AdminController adminController = loader.getController();
        adminController.setSession(session);
        Stage stage = (Stage) loginButton.getScene().getWindow();
        stage.setTitle("FaceTrace - Admin Records");
        stage.setScene(new Scene(root, 1120, 720));
    }

    /** Hides and clears both fields after the user submits the form. */
    private void clearPasswordFields() {
        passwordField.clear();
        visiblePasswordField.clear();
        showPasswordCheckBox.setSelected(false);
        passwordField.setVisible(true);
        passwordField.setManaged(true);
        visiblePasswordField.setVisible(false);
        visiblePasswordField.setManaged(false);
    }
}
