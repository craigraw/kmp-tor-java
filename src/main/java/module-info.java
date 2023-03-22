open module com.sparrowwallet.kmptorjava {
    requires java.desktop;
    requires javafx.base;
    requires javafx.controls;
    requires javafx.graphics;
    requires jetbrains.annotations;
    requires kotlin.stdlib;
    requires kmp.tor.jvm;
    requires kmp.tor.binary.extract.jvm;
    requires kmp.tor.common.jvm;
    requires kmp.tor.controller.common.jvm;
    requires kmp.tor.manager.jvm;
    requires kmp.tor.manager.common.jvm;
    requires kmp.tor.ext.callback.manager.jvm;
    requires kmp.tor.ext.callback.common.jvm;
    requires kmp.tor.ext.callback.manager.common.jvm;
    requires kmp.tor.ext.callback.controller.common.jvm;
    requires parcelize.jvm;
    requires kotlinx.coroutines.javafx;
    requires org.slf4j;
}