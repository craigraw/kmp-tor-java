package com.sparrowwallet.kmptorjava;

import io.matthewnelson.kmp.tor.KmpTorLoaderJvm;
import io.matthewnelson.kmp.tor.PlatformInstaller;
import io.matthewnelson.kmp.tor.TorConfigProviderJvm;
import io.matthewnelson.kmp.tor.common.address.PortProxy;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig;
import io.matthewnelson.kmp.tor.controller.common.events.TorEvent;
import io.matthewnelson.kmp.tor.controller.common.file.Path;
import io.matthewnelson.kmp.tor.manager.TorManager;
import io.matthewnelson.kmp.tor.manager.common.TorOperationManager;
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import kotlin.Unit;
import kotlin.jvm.internal.Intrinsics;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class Main extends Application {
    private final Path path = Path.invoke(System.getProperty("java.io.tmpdir")).builder().addSegment(".kmp-tor-java").build();

    private TorManager torManager;

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("KMP Tor Java");
        Button btn = new Button();
        btn.setText("Start Tor");
        btn.setOnAction(event -> {
            PlatformInstaller installer = PlatformInstaller.macosArm64(PlatformInstaller.InstallOption.CleanInstallIfMissing);
            TorConfigProviderJvm torConfigProviderJvm = new TorConfigProviderJvm() {
                @NotNull
                @Override
                public Path getWorkDir() {
                    return path.builder().addSegment("work").build();
                }

                @NotNull
                @Override
                public Path getCacheDir() {
                    return path.builder().addSegment("cache").build();
                }

                @NotNull
                @Override
                protected TorConfig provide() {
                    TorConfig.Builder builder = new TorConfig.Builder();
                    TorConfig.Setting.Ports.Socks socks = new TorConfig.Setting.Ports.Socks();
                    socks.set(TorConfig.Option.AorDorPort.Value.invoke(PortProxy.invoke(9050)));
                    builder.put(socks);

                    TorConfig.Setting.Ports.Control control = new TorConfig.Setting.Ports.Control();
                    control.set(TorConfig.Option.AorDorPort.Value.invoke(PortProxy.invoke(9051)));
                    builder.put(control);

                    TorConfig.Setting.DormantCanceledByStartup dormantCanceledByStartup = new TorConfig.Setting.DormantCanceledByStartup();
                    dormantCanceledByStartup.set(TorConfig.Option.AorTorF.getTrue());
                    builder.put(dormantCanceledByStartup);

                    return builder.build();
                }
            };

            KmpTorLoaderJvm jvmLoader = new KmpTorLoaderJvm(installer, torConfigProviderJvm);
            torManager = TorManager.newInstance(jvmLoader);

            torManager.debug(true);
            torManager.addListener(new TorManagerEvent.SealedListener() {
                @Override
                public void onEvent(@NotNull TorManagerEvent torManagerEvent) {
                    System.out.println(torManagerEvent.toString());
                }

                @Override
                public void onEvent(@NotNull TorEvent.Type.SingleLineEvent singleLineEvent, @NotNull String s) {
                    System.out.println(singleLineEvent.toString() + " - " + s);
                }

                @Override
                public void onEvent(@NotNull TorEvent.Type.MultiLineEvent multiLineEvent, @NotNull List<String> list) {
                    System.out.println(multiLineEvent.toString() + " - " + list.toString());
                }
            });

            torManager.startQuietly();

            primaryStage.setOnCloseRequest(event1 -> {
                torManager.destroy(true, () -> {
                    Platform.exit();
                    return Unit.INSTANCE;
                });
                event.consume();
            });
        });

        StackPane root = new StackPane();
        root.getChildren().add(btn);
        primaryStage.setScene(new Scene(root, 300, 250));
        primaryStage.show();
    }

    @Override
    public void stop() throws Exception {
        super.stop();
        if(torManager != null) {
            torManager.destroy(true, () -> {
                Platform.exit();
                return Unit.INSTANCE;
            });
        }
    }
}
