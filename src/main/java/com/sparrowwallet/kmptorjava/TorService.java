package com.sparrowwallet.kmptorjava;

import io.matthewnelson.kmp.tor.ext.callback.manager.CallbackTorManager;
import io.matthewnelson.kmp.tor.manager.common.event.TorManagerEvent;
import javafx.concurrent.Service;
import javafx.concurrent.Task;
import org.jetbrains.annotations.NotNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;

public class TorService extends Service<Tor> {
    private static final Logger log = LoggerFactory.getLogger(TorService.class);

    private final ReentrantLock startupLock = new ReentrantLock();
    private final Condition startupCondition = startupLock.newCondition();

    @Override
    protected Task<Tor> createTask() {
        return new Task<>() {
            private Exception startupException;

            @Override
            protected Tor call() throws Exception {
                Tor tor = Tor.getDefault();
                if(tor == null) {
                    tor = new Tor();
                    CallbackTorManager callbackTorManager = tor.getTorManager();
                    callbackTorManager.addListener(new TorManagerEvent.Listener() {
                        @Override
                        public void managerEventAddressInfo(@NotNull TorManagerEvent.AddressInfo info) {
                            if(!info.isNull) {
                                try {
                                    startupLock.lock();
                                    startupCondition.signalAll();
                                } finally {
                                    startupLock.unlock();
                                }
                            }
                        }
                    });
                    callbackTorManager.start(throwable -> {
                        if(throwable instanceof Exception exception) {
                            startupException = exception;
                        } else {
                            startupException = new Exception(throwable);
                        }
                        log.error("Error", throwable);
                        try {
                            startupLock.lock();
                            startupCondition.signalAll();
                        } finally {
                            startupLock.unlock();
                        }
                    }, success -> {
                        log.info("Daemon started successfully");
                    });

                    try {
                        startupLock.lock();
                        if(!startupCondition.await(5, TimeUnit.MINUTES)) {
                            throw new TorStartupException("Tor failed to start after 5 minutes, giving up");
                        }

                        if(startupException != null) {
                            throw startupException;
                        }
                    } finally {
                        startupLock.unlock();
                    }
                }

                return tor;
            }
        };
    }
}
