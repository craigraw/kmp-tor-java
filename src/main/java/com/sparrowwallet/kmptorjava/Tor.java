package com.sparrowwallet.kmptorjava;

import io.matthewnelson.kmp.tor.KmpTorLoaderJvm;
import io.matthewnelson.kmp.tor.PlatformInstaller;
import io.matthewnelson.kmp.tor.TorConfigProviderJvm;
import io.matthewnelson.kmp.tor.binary.extract.TorBinaryResource;
import io.matthewnelson.kmp.tor.common.address.Port;
import io.matthewnelson.kmp.tor.common.address.PortProxy;
import io.matthewnelson.kmp.tor.common.address.ProxyAddress;
import io.matthewnelson.kmp.tor.controller.common.config.TorConfig;
import io.matthewnelson.kmp.tor.controller.common.file.Path;
import io.matthewnelson.kmp.tor.ext.callback.manager.CallbackTorManager;
import io.matthewnelson.kmp.tor.manager.TorManager;
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent;
import io.matthewnelson.kmp.tor.manager.util.PortUtil;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

public class Tor {
    private static final Logger log = LoggerFactory.getLogger(Tor.class);

    private static Tor tor;

    private final Path path = Path.invoke(System.getProperty("user.home")).builder().addSegment(".kmp-tor-java").build();
    private final CallbackTorManager instance;
    private Proxy proxy;

    public Tor() {
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
        TorManager torManager = TorManager.newInstance(jvmLoader);

        torManager.debug(true);
        torManager.addListener(new TorManagerEvent.Listener() {
            @Override
            public void managerEventWarn(@NotNull String message) {
                log.warn(message);
            }

            @Override
            public void managerEventInfo(@NotNull String message) {
                log.info(message);
            }

            @Override
            public void managerEventDebug(@NotNull String message) {
                log.debug(message);
            }

            @Override
            public void managerEventAddressInfo(@NotNull TorManagerEvent.AddressInfo info) {
                if (info.isNull) {
                    proxy = null;
                } else {
                    ProxyAddress socks = info.socksInfoToProxyAddress().iterator().next();
                    proxy = new Proxy(Proxy.Type.SOCKS, new InetSocketAddress(socks.address.getValue(), socks.port.getValue()));
                }
            }
        });

        this.instance = new CallbackTorManager(torManager, uncaughtException -> {
            log.error("Uncaught exception from CallbackTorManager", uncaughtException);
        });
    }

    public static Tor getDefault() {
        return Tor.tor;
    }

    public static void setDefault(Tor tor) {
        Tor.tor = tor;
    }

    public CallbackTorManager getTorManager() {
        return instance;
    }

    public Proxy getProxy() {
        return proxy;
    }

    public static boolean isRunningExternally() {
        return !PortUtil.isTcpPortAvailable(Port.invoke(9050));
    }
}
