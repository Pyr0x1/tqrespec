/*
 * Copyright (C) 2019 Emerson Pinter - All Rights Reserved
 */

/*    This file is part of TQ Respec.

    TQ Respec is free software: you can redistribute it and/or modify
    it under the terms of the GNU General Public License as published by
    the Free Software Foundation, either version 3 of the License, or
    (at your option) any later version.

    TQ Respec is distributed in the hope that it will be useful,
    but WITHOUT ANY WARRANTY; without even the implied warranty of
    MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
    GNU General Public License for more details.

    You should have received a copy of the GNU General Public License
    along with TQ Respec.  If not, see <http://www.gnu.org/licenses/>.
*/

package br.com.pinter.tqrespec.util;

import br.com.pinter.tqrespec.gui.State;
import br.com.pinter.tqrespec.tqdata.GameInfo;
import javafx.application.Platform;
import javafx.concurrent.Task;
import javafx.scene.control.Alert;
import javafx.stage.Modality;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.nio.file.attribute.FileTime;
import java.text.MessageFormat;
import java.util.Objects;
import java.util.ResourceBundle;
import java.util.jar.Attributes;
import java.util.jar.Manifest;

import static java.nio.file.StandardCopyOption.COPY_ATTRIBUTES;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

@SuppressWarnings("ALL")
public class Util {
    public static void log(Object message) {
        System.err.println(message);
    }

    public static void log(String fmt, Object... args) {
        System.err.printf(fmt, args);
    }

    public static String getBuildVersion() {
        String implementationVersion = Util.class.getPackage().getImplementationVersion();
        if (implementationVersion == null) {
            Attributes attr = readManifest();
            if (attr != null) {
                implementationVersion = attr.getValue("Implementation-Version");
            }
        }
        return Objects.requireNonNullElse(implementationVersion, "0.0");
    }

    public static String getBuildTitle() {
        String implementationTitle = Util.class.getPackage().getImplementationTitle();
        if (implementationTitle == null) {
            Attributes attr = readManifest();
            if (attr != null) {
                implementationTitle = attr.getValue("Implementation-Title");
            }
        }
        return Objects.requireNonNullElse(implementationTitle, "Development");
    }

    private static Attributes readManifest() {
        Manifest manifest = null;
        try {
            FileSystem fs = FileSystems.getFileSystem(URI.create("jrt:/"));
            InputStream stream = Files.newInputStream(
                    fs.getPath("modules", Util.class.getModule().getName(), "META-INF/MANIFEST.MF"));
            manifest = new Manifest(stream);
            return manifest.getMainAttributes();
        } catch (IOException e) {
        }
        return null;
    }

    public static void showError(String message, String contentText) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("Error");
        alert.setHeaderText(message);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    private static void showWarning(String message, String contentText) {
        Alert alert = new Alert(Alert.AlertType.WARNING);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("Warning");
        alert.setHeaderText(message);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    public static void showInformation(String message, String contentText) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.initModality(Modality.APPLICATION_MODAL);
        alert.setTitle("Information");
        alert.setHeaderText(message);
        alert.setContentText(contentText);
        alert.showAndWait();
    }

    public static String getUIMessage(String message) {
        ResourceBundle ui = ResourceBundle.getBundle("i18n.UI");
        if (ui.containsKey(message)) {
            return ui.getString(message);
        }
        return message;
    }

    public static String getUIMessage(String message, Object... parameters) {
        ResourceBundle ui = ResourceBundle.getBundle("i18n.UI");
        if (ui.containsKey(message)) {
            return MessageFormat.format(ui.getString(message), parameters);
        }
        return message;
    }

    private static boolean tryToCloseApplication() {
        if (State.get().getSaveInProgress() != null && !State.get().getSaveInProgress()
                || State.get().getSaveInProgress() == null) {
            Platform.exit();
            System.exit(0);
        }
        return false;
    }

    @SuppressWarnings("unchecked")
    public static void copyDirectoryRecurse(Path source, Path target, boolean replace) throws FileAlreadyExistsException {
        boolean DBG = false;
        if (!replace && Files.exists(target)) {
            throw new FileAlreadyExistsException(target.toString() + " already exists");
        }
        FileVisitor fileVisitor = new FileVisitor() {
            @SuppressWarnings("ConstantConditions")
            @Override
            public FileVisitResult preVisitDirectory(Object dir, BasicFileAttributes attrs) {
                Path targetDir = target.resolve(source.relativize((Path) dir));

                if (DBG) System.err.println(String.format("PREDIR: src:'%s' dst:'%s'", dir, targetDir));

                try {
                    Files.copy((Path) dir, targetDir, COPY_ATTRIBUTES);
                } catch (DirectoryNotEmptyException ignored) {
                } catch (IOException e) {
                    //noinspection ConstantConditions
                    if (DBG) System.err.println(String.format("Unable to create directory '%s'", targetDir));
                    return FileVisitResult.TERMINATE;
                }

                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFile(Object file, BasicFileAttributes attrs) {
                Path targetFile = target.resolve(source.relativize((Path) file));
                try {
                    Files.copy((Path) file, targetFile, replace ? new CopyOption[]{COPY_ATTRIBUTES, REPLACE_EXISTING}
                            : new CopyOption[]{COPY_ATTRIBUTES});
                } catch (IOException e) {
                    if (DBG) System.err.println(String.format("Unable to create file '%s'", targetFile));
                    return FileVisitResult.TERMINATE;
                }
                if (DBG) System.err.println(String.format("FILE: src:'%s' dst:'%s'", file, targetFile));
                return FileVisitResult.CONTINUE;
            }

            @Override
            public FileVisitResult visitFileFailed(Object file, IOException exc) {
                if (DBG) System.err.println(String.format("VISITFAIL: %s %s", file, exc));
                return FileVisitResult.TERMINATE;
            }

            @Override
            public FileVisitResult postVisitDirectory(Object dir, IOException exc) {
                Path targetDir = target.resolve(source.relativize((Path) dir));

                if (DBG) System.err.println(String.format("POSTDIR: src:'%s' dst:'%s'", dir, targetDir));

                if (exc == null) {
                    try {
                        FileTime fileTime = Files.getLastModifiedTime((Path) dir);
                        Files.setLastModifiedTime(targetDir, fileTime);
                    } catch (IOException e) {
                        e.printStackTrace();
                        return FileVisitResult.TERMINATE;
                    }
                }
                return FileVisitResult.CONTINUE;
            }
        };

        try {
            Files.walkFileTree(source, fileVisitor);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String normalizeRecordPath(String recordId) {
        if (recordId == null || recordId.isEmpty()) {
            return null;
        }
        return recordId.toUpperCase().replace("/", "\\");
    }

    public static boolean recordPathEquals(String r1, String r2) {
        return Util.normalizeRecordPath(r1).equals(Util.normalizeRecordPath(r2));
    }

    public static void closeApplication() {
        if (!Util.tryToCloseApplication()) {
            Util.showWarning(Util.getUIMessage("alert.saveinprogress_header"), Util.getUIMessage("alert.saveinprogress_content"));
            Task tryAgain = new Task() {
                @Override
                protected Object call() {
                    try {
                        Thread.sleep(2000);
                    } catch (InterruptedException e) {
                        e.printStackTrace();
                    }
                    Util.tryToCloseApplication();
                    return null;
                }
            };
        }
    }

    public static Path playerChr(String playerName, boolean customQuest) {
        String path;

        if (customQuest) {
            path = GameInfo.getInstance().getSaveDataUserPath();
        } else {
            path = GameInfo.getInstance().getSaveDataMainPath();
        }


        return Paths.get(path, "_" + playerName, "Player.chr");
    }
}
