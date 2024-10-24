package fxlauncher;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

public class DefaultUIProvider implements UIProvider {
  private ProgressBar progressBar;
  private Label mainLabel;
  private Label progressLabel;

  public Parent createLoader() {
    ProgressIndicator pi = new ProgressIndicator();
    pi.setStyle("-fx-progress-color: #2f2f2f;");
    pi.setPrefSize(10, 10);
    StackPane root = new StackPane(pi);
    root.setPrefSize(380, 90);
    root.setPadding(new Insets(5));
//    root.setStyle(
//        "-fx-border-color: transparent; -fx-background-color: #F5F5F5; -fx-background-radius: 3px; -fx-border-radius: 3px;");
    return root;
  }

  public Parent createUpdater(FXManifest manifest) {
    progressBar = new ProgressBar();
    progressBar.setStyle(manifest.progressBarStyle);

    mainLabel = new Label(manifest.updateText);
    mainLabel.setStyle(manifest.updateLabelStyle);

    progressLabel = new Label("");
    progressLabel.setStyle(manifest.progressLabelStyle);

    VBox loading = new VBox(progressBar);
    loading.setAlignment(Pos.BOTTOM_CENTER);
    VBox.setVgrow(loading, Priority.ALWAYS);

    VBox wrapper = new VBox(mainLabel, progressLabel, loading);
    wrapper.setStyle(manifest.wrapperStyle);
    return wrapper;
  }

  public void updateProgress(double progress, String fileProgress) {
    progressBar.setProgress(progress);
    progressLabel.setText(fileProgress);
  }

  public void init(Stage stage) {}
}
