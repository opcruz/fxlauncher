package fxlauncher;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.TextArea;
import javafx.scene.layout.StackPane;
import javafx.scene.web.WebView;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

public class LauncherProcess extends Application {
  private static final Logger log = Logger.getLogger("LauncherProcess");

  private Stage primaryStage;
  private Stage stage;
  private UIProvider uiProvider;
  private StackPane root;

  private final AbstractLauncher<Application> superLauncher =
      new AbstractLauncher<>() {
        @Override
        protected Parameters getParameters() {
          return LauncherProcess.this.getParameters();
        }

        @Override
        protected void updateProgress(double progress, String fileProgress) {
          Platform.runLater(() -> uiProvider.updateProgress(progress, fileProgress));
        }

        @Override
        protected void createApplication(Class<Application> appClass) { }

        @Override
        protected void reportError(String title, Throwable error) {
          log.log(Level.WARNING, title, error);

          Platform.runLater(
              () -> {
                Alert alert = new Alert(Alert.AlertType.ERROR);
                alert.setTitle(title);
                alert.setHeaderText(
                    String.format(
                        "%s\ncheck the logfile 'fxlauncher.log, usually in the %s directory",
                        title, System.getProperty("java.io.tmpdir")));
                alert.getDialogPane().setPrefWidth(600);

                ByteArrayOutputStream out = new ByteArrayOutputStream();
                PrintWriter writer = new PrintWriter(out);
                error.printStackTrace(writer);
                writer.close();
                TextArea text = new TextArea(out.toString());
                alert.getDialogPane().setContent(text);

                alert.showAndWait();
                Platform.exit();
              });
        }

        @Override
        protected void setupClassLoader(ClassLoader classLoader) { }
      };

  public void init() {
    uiProvider = new DefaultUIProvider();
  }

  public void start(Stage primaryStage) throws Exception {
    this.primaryStage = primaryStage;
    stage = new Stage(StageStyle.UNDECORATED);
//    stage.initStyle(StageStyle.TRANSPARENT);
    root = new StackPane();
//    root.setStyle("-fx-padding: 3px; -fx-background-color: transparent;");
    Scene scene = new Scene(root);
//    scene.setFill(Color.TRANSPARENT);
    stage.setScene(scene);

    superLauncher.setupLogFile();
    superLauncher.checkSSLIgnoreflag();
    this.uiProvider.init(stage);
    root.getChildren().add(uiProvider.createLoader());

    stage.show();

    new Thread(
            () -> {
              boolean filesUpdated = false;
              Thread.currentThread().setName("FXLauncher-Thread");
              try {
                superLauncher.updateManifest();
                createUpdateWrapper();
                filesUpdated = superLauncher.syncFiles();
              } catch (Exception ex) {
                log.log(
                    Level.WARNING,
                    String.format("Error during %s phase", superLauncher.getPhase()),
                    ex);
                if (superLauncher.checkIgnoreUpdateErrorSetting()) {
                  superLauncher.reportError(
                      String.format("Error during %s phase", superLauncher.getPhase()), ex);
                  System.exit(1);
                }
              }

              try {
                launchAppFromManifest(filesUpdated);
              } catch (Exception ex) {
                superLauncher.reportError(
                    String.format("Error during %s phase", superLauncher.getPhase()), ex);
              }
            })
        .start();
  }

  private void launchAppFromManifest(boolean showWhatsnew) {
    superLauncher.setPhase("Application Environment Prepare");
    log.info("Show whats new dialog? " + showWhatsnew);

    runAndWait(
        () -> {
          try {
            if (showWhatsnew && superLauncher.getManifest().whatsNewPage != null) {
              showWhatsNewDialog(superLauncher.getManifest().whatsNewPage);
            }
            // Lingering update screen will close when primary stage is shown
            if (superLauncher.getManifest().lingeringUpdateScreen) {
              primaryStage
                  .showingProperty()
                  .addListener(
                      observable -> {
                        if (stage.isShowing()) stage.close();
                      });
            } else {
              stage.close();
            }

            startApplication();
            Platform.exit();
          } catch (Throwable ex) {
            superLauncher.reportError("Failed to start application", ex);
          }
        });
  }

  private void showWhatsNewDialog(String whatsNewURL) {
    WebView view = new WebView();
    view.getEngine().load(whatsNewURL);
    Alert alert = new Alert(Alert.AlertType.INFORMATION);
    alert.setTitle("What's new");
    alert.setHeaderText("New in this update");
    alert.getDialogPane().setContent(view);
    alert.showAndWait();
  }

  public static void main(String[] args) {
    launch(args);
  }

  private void createUpdateWrapper() {
    superLauncher.setPhase("Update Wrapper Creation");

    Platform.runLater(
        () -> {
          Parent updater = uiProvider.createUpdater(superLauncher.getManifest());
          root.getChildren().clear();
          root.getChildren().add(updater);
        });
  }

  private void startApplication() throws Exception {
    FXManifest manifest = superLauncher.getManifest();
    Path cacheDir = manifest.resolveCacheDir(getParameters().getNamed());

    String javaBin = System.getProperty("java.home")
            + File.separator + "bin" + File.separator + "java";

    String classPath = manifest.files.stream()
            .map(value -> cacheDir.resolve(value.file).toAbsolutePath().toString())
            .filter(value -> value.endsWith(".jar"))
            .collect(Collectors.joining(File.pathSeparator));

    if (classPath.isEmpty()) {
      throw new IllegalStateException("No JAR files found for classpath.");
    }
    if (manifest.launchClass == null) {
      throw new IllegalStateException("Main class not defined in manifest.");
    }

    List<String> command = new ArrayList<>();
    command.add(javaBin);
    command.addAll(ManagementFactory.getRuntimeMXBean().getInputArguments());
    command.add("-cp");
    command.add(classPath);
    command.add(manifest.launchClass);
    command.addAll(getParameters().getUnnamed());

    new ProcessBuilder(command).start();
  }

  private void runAndWait(Runnable action) {
    if (action == null) throw new NullPointerException("action");

    // run synchronously on JavaFX thread
    if (Platform.isFxApplicationThread()) {
      action.run();
      return;
    }

    // queue on JavaFX thread and wait for completion
    final CountDownLatch doneLatch = new CountDownLatch(1);
    Platform.runLater(
        () -> {
          try {
            action.run();
          } finally {
            doneLatch.countDown();
          }
        });

    try {
      doneLatch.await();
    } catch (InterruptedException e) {
      // ignore exception
    }
  }
}
