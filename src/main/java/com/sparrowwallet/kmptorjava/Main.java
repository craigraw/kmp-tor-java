package com.sparrowwallet.kmptorjava;

import io.matthewnelson.kmp.tor.controller.common.control.usecase.TorControlSignal;
import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;

public class Main extends Application {
    private static final Logger log = LoggerFactory.getLogger(Main.class);

    private static String onionAddress;

    public static void main(String[] args) {
        if(args.length > 0) {
            onionAddress = args[0];
        }

        launch(args);
    }

    @Override
    public void start(Stage primaryStage) {
        log.info("Starting...");
        primaryStage.setTitle("KMP Tor Java");
        Button btn = new Button();
        btn.setText("Start Tor");
        btn.setOnAction(event -> {
            TorService torService = new TorService();
            torService.setOnSucceeded(event1 -> {
                System.out.println("Tor startup completed successfully");
                Tor.setDefault(torService.getValue());

                if(onionAddress != null)  {
                    try {
                        Socket socket = new Socket(Tor.getDefault().getProxy());
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
                } else {
                    Tor.getDefault().getTorManager().signal(TorControlSignal.Signal.NewNym, throwable -> {
                        System.out.println("Failed to signal newnym");
                    }, successEvent -> {
                        System.out.println("Signalled newnym");
                    });
                }
            });
            torService.setOnFailed(event1 -> {
                System.out.println("Tor failed to start with error " + event1.getSource().getException().getMessage());
            });
            torService.start();
        });

        primaryStage.setOnCloseRequest(event -> {
            if(Tor.getDefault() != null) {
                Tor.getDefault().getTorManager().destroy(true, successEvent -> {
                    javafx.application.Platform.exit();
                });
            }
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

        if(Tor.getDefault() != null) {
            Tor.getDefault().getTorManager().destroy(true, successEvent -> {
                javafx.application.Platform.exit();
            });
        }
    }
}
