package com.sparrowwallet.kmptorjava;

import io.matthewnelson.kmp.tor.KmpTorLoaderJvm;
import io.matthewnelson.kmp.tor.PlatformInstaller;
import io.matthewnelson.kmp.tor.TorConfigProviderJvm;
import io.matthewnelson.kmp.tor.binary.extract.TorBinaryResource;
import io.matthewnelson.kmp.tor.common.address.PortProxy;
import io.matthewnelson.kmp.tor.common.address.ProxyAddress;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig;
import io.matthewnelson.kmp.tor.controller.common.file.Path;
import io.matthewnelson.kmp.tor.manager.TorManager;
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import kotlin.Unit;
import org.jetbrains.annotations.NotNull;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.List;

public class Main extends Application {
    private final Path path = Path.invoke(System.getProperty("user.home")).builder().addSegment(".kmp-tor-java").build();

    private TorManager torManager;
    private static String onionAddress;

    public static void main(String[] args) {
        if(args.length > 0) {
            onionAddress = args[0];
        }

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        Platform platform = Platform.getCurrent();
        String arch = System.getProperty("os.arch");
        PlatformInstaller installer;
        PlatformInstaller.InstallOption installOption = PlatformInstaller.InstallOption.CleanInstallIfMissing;

        if(platform == Platform.OSX) {
            if(arch.equals("aarch64")) {
                installer = PlatformInstaller.macosArm64(installOption);
            } else {
                installer = PlatformInstaller.macosX64(installOption);
            }
        } else if(platform == Platform.WINDOWS) {
            installer = PlatformInstaller.mingwX64(installOption);
        } else if(platform == Platform.UNIX) {
            if(arch.equals("aarch64")) {
                TorBinaryResource linuxArm64 = TorBinaryResource.from(TorBinaryResource.OS.Linux, "arm64",
                        "588496f3164d52b91f17e4db3372d8dfefa6366a8df265eebd4a28d4128992aa",
                        List.of("libevent-2.1.so.7.gz", "libstdc++.so.6.gz", "libcrypto.so.1.1.gz", "tor.gz", "libssl.so.1.1.gz"));
                installer = PlatformInstaller.custom(installOption, linuxArm64);
            } else {
                installer = PlatformInstaller.linuxX64(installOption);
            }
        } else {
            throw new UnsupportedOperationException("Tor is not supported on " + platform + " " + arch);
        }

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
        torManager.addListener(new TorManagerEvent.Listener() {
            @Override
            public void managerEventError(Throwable t) {
                System.out.println("Error: " + t.getMessage());
            }

            @Override
            public void managerEventWarn(@NotNull String message) {
                System.out.println("Warn: " + message);
            }

            @Override
            public void managerEventInfo(@NotNull String message) {
                System.out.println("Info: " + message);
            }

            @Override
            public void managerEventDebug(@NotNull String message) {
                System.out.println("Debug: " + message);
            }

            @Override
            public void managerEventAddressInfo(TorManagerEvent.AddressInfo info) {
                if (info.isNull) {
                    // Tear down HttpClient
                } else if(onionAddress != null)  {
                    try {
                        ProxyAddress socks = info.socksInfoToProxyAddress().iterator().next();
                        Socket socket = new Socket(new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socks.address.getValue(), socks.port.getValue())));
                        socket.connect(new InetSocketAddress(onionAddress, 50001));

                        PrintWriter out = new PrintWriter(new BufferedWriter(new OutputStreamWriter(socket.getOutputStream())));
                        out.println("{\"jsonrpc\":\"2.0\",\"method\":\"server.version\",\"params\":[\"Sparrow\",\"1.4\"],\"id\":1}");
                        out.flush();

                        BufferedReader in = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                        String response = in.readLine();
                        System.out.println(response);

                        socket.close();
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                }
            }
        });

        primaryStage.setTitle("KMP Tor Java");
        Button btn = new Button();
        btn.setText("Start Tor");
        btn.setOnAction(event -> {
            torManager.startQuietly();
        });

        primaryStage.setOnCloseRequest(event -> {
            torManager.destroy(true, () -> {
                javafx.application.Platform.exit();
                return Unit.INSTANCE;
            });
            event.consume();
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
            // This is only a stop gap here if the close intercept fails or something
            // By passing `false`, it will not "stopCleanly" and do an immediate disconnect
            // of the TorController, resulting in Tor stopping b/c it's control port owner has
            // cut the connection.
            torManager.destroy(false, () -> {
                javafx.application.Platform.exit();
                return Unit.INSTANCE;
            });
        }
    }
}
