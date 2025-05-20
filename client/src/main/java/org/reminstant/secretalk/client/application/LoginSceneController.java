package org.reminstant.secretalk.client.application;

import javafx.application.Platform;
import javafx.fxml.FXML;
import javafx.fxml.Initializable;
import javafx.scene.control.Button;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;
import javafx.scene.input.KeyCode;
import javafx.scene.input.KeyEvent;
import javafx.scene.input.MouseButton;
import javafx.scene.input.MouseEvent;
import net.rgielen.fxweaver.core.FxmlView;
import org.reminstant.concurrent.ConcurrentUtil;
import org.reminstant.secretalk.client.application.control.NotificationLabel;
import org.reminstant.secretalk.client.application.control.SwipableAnchorPane;
import org.reminstant.secretalk.client.util.FxUtil;
import org.reminstant.secretalk.client.util.StatusDescriptionHolder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URL;
import java.util.ResourceBundle;
import java.util.concurrent.CompletableFuture;

@Component
@FxmlView("loginScene.fxml")
public class LoginSceneController implements Initializable {

  private static final Logger log = LoggerFactory.getLogger(LoginSceneController.class);

  private final ApplicationStateManager stateManager;
  private final StatusDescriptionHolder statusDescriptionHolder;

  @FXML private SwipableAnchorPane blockContainer;
  @FXML private TextField loginLoginField;
  @FXML private PasswordField loginPasswordField;
  @FXML private TextField regLoginField;
  @FXML private PasswordField regPasswordField;
  @FXML private NotificationLabel loginNotificationLabel;
  @FXML private NotificationLabel regNotificationLabel;
  @FXML private Button loginButton;
  @FXML private Button regButton;
  @FXML private Button noAccountButton;
  @FXML private Button backToLoginButton;

  public LoginSceneController(ApplicationStateManager sceneManager,
                              StatusDescriptionHolder statusDescriptionHolder) {
    this.stateManager = sceneManager;
    this.statusDescriptionHolder = statusDescriptionHolder;
  }

  // TODO: ограничить кол-во символов в инпутах

  @Override
  public void initialize(URL url, ResourceBundle resourceBundle) {
    noAccountButton.setOnMouseClicked(this::onNoAccountLabelClicked);
    noAccountButton.setOnKeyReleased(this::onNoAccountLabelKeyReleased);
    backToLoginButton.setOnMouseClicked(this::onBackToLoginLabelClicked);
    backToLoginButton.setOnKeyReleased(this::onBackToLoginKeyReleased);

    loginButton.setOnMouseClicked(this::onLoginButtonClicked);
    loginButton.setOnKeyReleased(this::onLoginButtonKeyReleased);
    regButton.setOnMouseClicked(this::onRegButtonClicked);
    regButton.setOnKeyReleased(this::onRegButtonKeyReleased);

    log.info("LoginSceneController is initialised");
  }


  private void clearLoginSide() {
      loginNotificationLabel.collapse();
      loginLoginField.textProperty().set("");
      loginPasswordField.textProperty().set("");
  }

  private void clearRegisterSide() {
    regNotificationLabel.collapse();
    regLoginField.textProperty().set("");
    regPasswordField.textProperty().set("");
  }

  private void processLogin() {
    String login = loginLoginField.getText();
    String password = loginPasswordField.getText();

    loginButton.setDisable(true);
    noAccountButton.setDisable(true);

    stateManager.processLogin(login, password).thenWeaklyConsumeAsync(status -> {
      FxUtil.runOnFxThread(() -> {
        if (status == 200) {
          clearLoginSide();
        } else {
          String desc = statusDescriptionHolder.getDescription(status, "loginStatus");
          loginNotificationLabel.showError(desc);
        }
        loginButton.setDisable(false);
        noAccountButton.setDisable(false);
      });
    });
  }

  private void processReg() {
    String login = regLoginField.getText();
    String password = regPasswordField.getText();

    regButton.setDisable(true);
    backToLoginButton.setDisable(true);

    stateManager.processRegister(login, password).thenWeaklyConsumeAsync(status -> {
      String desc = statusDescriptionHolder.getDescription(status, "registerStatus");
      FxUtil.runOnFxThread(() -> {
        if (status == 200) {
          swipeToLogin();
          loginNotificationLabel.showSuccess(desc);
        } else {
          regNotificationLabel.showError(desc);
        }
        regButton.setDisable(false);
        backToLoginButton.setDisable(false);
      });
    });

    // TODO: добавить иконку загрузки
  }

  private void swipeToReg() {
    blockContainer.setSwiped(true);
    CompletableFuture.runAsync(() -> {
      ConcurrentUtil.sleepSafely(200);
      Platform.runLater(this::clearLoginSide);
    });
  }

  private void swipeToLogin() {
    blockContainer.setSwiped(false);
    CompletableFuture.runAsync(() -> {
      ConcurrentUtil.sleepSafely(200);
      Platform.runLater(this::clearRegisterSide);
    });
  }


  private void onLoginButtonClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      processLogin();
    }
  }

  private void onLoginButtonKeyReleased(KeyEvent e) {
    if (e.getCode().equals(KeyCode.ENTER)) {
      processLogin();
    }
  }

  private void onRegButtonClicked(MouseEvent e) {
    if (e.getButton() == MouseButton.PRIMARY) {
      processReg();
    }
  }

  private void onRegButtonKeyReleased(KeyEvent e) {
    if (e.getCode().equals(KeyCode.ENTER)) {
      processReg();
    }
  }

  private void onNoAccountLabelClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      swipeToReg();
    }
  }

  private void onNoAccountLabelKeyReleased(KeyEvent e) {
    if (e.getCode().equals(KeyCode.ENTER)) {
      swipeToReg();
    }
  }

  private void onBackToLoginLabelClicked(MouseEvent e) {
    if (e.getButton().equals(MouseButton.PRIMARY)) {
      swipeToLogin();
    }
  }

  private void onBackToLoginKeyReleased(KeyEvent e) {
    if (e.getCode().equals(KeyCode.ENTER)) {
      swipeToLogin();
    }
  }
}
