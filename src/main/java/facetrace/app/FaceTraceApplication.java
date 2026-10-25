package facetrace.app;

import javafx.application.Application;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.net.URL;

/** Opens the public user-search view; admin sign-in remains available from its header button. */
public final class FaceTraceApplication extends Application {
    /** Loads the search layout and gives the window a plain, usable starting size. */
    @Override
    public void start(Stage stage) throws IOException {
        URL view = FaceTraceApplication.class.getResource("/facetrace/view/SearchView.fxml");
        if (view == null) {
            throw new IOException("The user search view is missing from the application resources.");
        }
        Parent root = FXMLLoader.load(view);
        stage.setTitle("FaceTrace - User Search");
        stage.setScene(new Scene(root, 1000, 680));
        stage.setMinWidth(850);
        stage.setMinHeight(560);
        stage.show();
    }

    /** Starts JavaFX. */
    public static void main(String[] args) {
        launch(args);
    }
}
