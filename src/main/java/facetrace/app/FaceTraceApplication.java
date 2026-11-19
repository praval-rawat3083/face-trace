package facetrace.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/** Opens the Smart Attendance kiosk window; admin portal remains accessible from the header. */
public final class FaceTraceApplication extends Application {
    @Override
    public void start(Stage stage) throws IOException {
        URL view = FaceTraceApplication.class.getResource("/facetrace/view/SearchView.fxml");
        if (view == null) {
            throw new IOException("The attendance view is missing from application resources.");
        }
        Parent root = FXMLLoader.load(view);
        stage.setTitle("FaceTrace — Smart Attendance System");
        stage.setScene(new Scene(root, 1160, 750));
        stage.setMinWidth(1000);
        stage.setMinHeight(640);
        stage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }
}
