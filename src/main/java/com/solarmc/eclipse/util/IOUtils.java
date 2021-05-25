package com.solarmc.eclipse.util;

import com.google.common.io.Files;
import com.solarmc.eclipse.bytecode.BytecodeFixer;
import com.solarmc.eclipse.util.lunar.PatchInfo;
import org.apache.commons.io.FileUtils;
import org.gradle.api.logging.Logger;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;
import org.zeroturnaround.zip.ByteSource;
import org.zeroturnaround.zip.ZipEntrySource;
import org.zeroturnaround.zip.ZipUtil;

import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.channels.Channels;
import java.nio.channels.ReadableByteChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.Map;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;
import java.util.stream.Collectors;

/**
 * Taken From Fabric Loom.
 * Long Have we awaited
 * MIT Activated.
 *
 * @author Chocohead
 */
public class IOUtils {
    public static boolean refreshDeps = false;

    /**
     * Download from the given {@link URL} to the given {@link File} so long as there are differences between them.
     *
     * @param from   The URL of the file to be downloaded
     * @param to     The destination to be saved to, and compared against if it exists
     * @param logger The logger to print everything to, typically from {@link org.gradle.api.Project#getLogger()}
     * @throws IOException If an exception occurs during the process
     */
    public static void downloadIfChanged(URL from, File to, Logger logger) throws IOException {
        downloadIfChanged(from, to, logger, false);
    }

    /**
     * Download from the given {@link URL} to the given {@link File} so long as there are differences between them.
     *
     * @param from   The URL of the file to be downloaded
     * @param to     The destination to be saved to, and compared against if it exists
     * @param logger The logger to print information to, typically from {@link org.gradle.api.Project#getLogger()}
     * @param quiet  Whether to only print warnings (when <code>true</code>) or everything
     * @throws IOException If an exception occurs during the process
     */
    public static void downloadIfChanged(URL from, File to, Logger logger, boolean quiet) throws IOException {
        HttpURLConnection connection = (HttpURLConnection) from.openConnection();

        if (refreshDeps) {
            getETagFile(to).delete();
            to.delete();
        }

        // If the output already exists we'll use it's last modified time
        if (to.exists()) {
            connection.setIfModifiedSince(to.lastModified());
        }

        //Try use the ETag if there's one for the file we're downloading
        String etag = loadETag(to, logger);

        if (etag != null) {
            connection.setRequestProperty("If-None-Match", etag);
        }

        // We want to download gzip compressed stuff
        connection.setRequestProperty("Accept-Encoding", "gzip");

        // Try make the connection, it will hang here if the connection is bad
        connection.connect();

        int code = connection.getResponseCode();

        if ((code < 200 || code > 299) && code != HttpURLConnection.HTTP_NOT_MODIFIED) {
            //Didn't get what we expected
            throw new IOException(connection.getResponseMessage() + " for " + from);
        }

        long modifyTime = connection.getHeaderFieldDate("Last-Modified", -1);

        if (to.exists() && (code == HttpURLConnection.HTTP_NOT_MODIFIED || modifyTime > 0 && to.lastModified() >= modifyTime)) {
            if (!quiet) {
                logger.info("'{}' Not Modified, skipping.", to);
            }

            return; //What we've got is already fine
        }

        long contentLength = connection.getContentLengthLong();

        if (!quiet && contentLength >= 0) {
            logger.info("'{}' Changed, downloading {}", to, toNiceSize(contentLength));
        }

        try { // Try download to the output
            FileUtils.copyInputStreamToFile(connection.getInputStream(), to);
        } catch (IOException e) {
            to.delete(); // Probably isn't good if it fails to copy/save
            throw e;
        }

        //Set the modify time to match the server's (if we know it)
        if (modifyTime > 0) {
            to.setLastModified(modifyTime);
        }

        //Save the ETag (if we know it)
        String eTag = connection.getHeaderField("ETag");

        if (eTag != null) {
            //Log if we get a weak ETag and we're not on quiet
            if (!quiet && eTag.startsWith("W/")) {
                logger.warn("Weak ETag found.");
            }

            saveETag(to, eTag, logger);
        }
    }

    /**
     * Creates a new file in the same directory as the given file with <code>.etag</code> on the end of the name.
     *
     * @param file The file to produce the ETag for
     * @return The (uncreated) ETag file for the given file
     */
    private static File getETagFile(File file) {
        return new File(file.getAbsoluteFile().getParentFile(), file.getName() + ".etag");
    }

    /**
     * Attempt to load an ETag for the given file, if it exists.
     *
     * @param to     The file to load an ETag for
     * @param logger The logger to print errors to if it goes wrong
     * @return The ETag for the given file, or <code>null</code> if it doesn't exist
     */
    private static String loadETag(File to, Logger logger) {
        File eTagFile = getETagFile(to);

        if (!eTagFile.exists()) {
            return null;
        }

        try {
            return Files.asCharSource(eTagFile, StandardCharsets.UTF_8).read();
        } catch (IOException e) {
            logger.warn("Error reading ETag file '{}'.", eTagFile);
            return null;
        }
    }

    /**
     * Saves the given ETag for the given file, replacing it if it already exists.
     *
     * @param to     The file to save the ETag for
     * @param eTag   The ETag to be saved
     * @param logger The logger to print errors to if it goes wrong
     */
    private static void saveETag(File to, String eTag, Logger logger) {
        File eTagFile = getETagFile(to);

        try {
            if (!eTagFile.exists()) {
                eTagFile.createNewFile();
            }

            Files.asCharSink(eTagFile, StandardCharsets.UTF_8).write(eTag);
        } catch (IOException e) {
            logger.warn("Error saving ETag file '{}'.", eTagFile, e);
        }
    }

    /**
     * Format the given number of bytes as a more human readable string.
     *
     * @param bytes The number of bytes
     * @return The given number of bytes formatted to kilobytes, megabytes or gigabytes if appropriate
     */
    private static String toNiceSize(long bytes) {
        if (bytes < 1024) {
            return bytes + " B";
        } else if (bytes < 1024 * 1024) {
            return bytes / 1024 + " KB";
        } else if (bytes < 1024 * 1024 * 1024) {
            return String.format("%.2f MB", bytes / (1024.0 * 1024.0));
        } else {
            return String.format("%.2f GB", bytes / (1024.0 * 1024.0 * 1024.0));
        }
    }

    /**
     * Delete the file along with the corresponding ETag, if it exists.
     *
     * @param file The file to delete.
     */
    public static void delete(File file) {
        if (file.exists()) {
            file.delete();
        }

        File etagFile = getETagFile(file);

        if (etagFile.exists()) {
            etagFile.delete();
        }
    }

    public static void downloadFile(URL url, File file) throws IOException {
        try (InputStream in = url.openStream();
             ReadableByteChannel byteChannel = Channels.newChannel(in);
             FileOutputStream out = new FileOutputStream(file)) {
            out.getChannel().transferFrom(byteChannel, 0, Long.MAX_VALUE);
        }
    }

    @Nullable
    public static InputStream getResourceAsStream(JarFile jar, String resource) {
        JarEntry result = jar.stream()
                .filter(jarEntry -> jarEntry.getRealName().equals(resource))
                .collect(Collectors.toList()).get(0);
        try {
            return jar.getInputStream(result);
        } catch (IOException e) {
            return null;
        }
    }

    public static byte[] readFully(InputStream inputStream) throws IOException {
        if (inputStream == null) {
            return null;
        }

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
        if (inputStream.transferTo(outputStream) == 0L) {
            throw new IOException("Failed to transfer InputStream to byte array (Transferred 0 bytes)");
        }
        return outputStream.toByteArray();
    }

    public static void appendToClassMap(Map<String, byte[]> classes, JarEntry entry, JarFile jar, PatchInfo info, boolean addResources) {
        if (!entry.isDirectory()) {
            if(entry.getRealName().contains("META-INF")){
                return;
            }
            if (entry.getName().endsWith(".class") && !entry.getName().contains("srg")) {
                String className = entry.getName().replace(".class", "");

                try (InputStream inputStream = jar.getInputStream(entry)) {
                    byte[] vanillaBytecode = readFully(inputStream);
                    byte[] bytecode = info.getClassBytes(className, vanillaBytecode).orElse(vanillaBytecode);
                    classes.put(entry.getName(), bytecode);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to open InputStream for entry " + entry.getName(), e);
                }
            } else {
                if(addResources && !entry.getName().contains("srg")) {
                    handleResource(classes, entry, jar);
                }
            }
        }
    }

    public static void lclassToClass(Map<String, byte[]> classes, JarEntry entry, JarFile jar, SolarClassLoader classLoader) {
        if (!entry.isDirectory()) {
            if (entry.getName().endsWith(".lclass")) {
                try (InputStream inputStream = jar.getInputStream(entry)) {
                    byte[] bytecode = readFully(inputStream);
                    classes.put(entry.getName().replace(".lclass", ".class"), bytecode);
                } catch (IOException e) {
                    throw new RuntimeException("Failed to open InputStream for entry " + entry.getName(), e);
                }
            }
        } else {
            handleResource(classes, entry, jar);
        }
    }

    public static void handleResource(Map<String, byte[]> classes, JarEntry entry, JarFile jar) {
        if (!entry.isDirectory()) {
            try (InputStream inputStream = jar.getInputStream(entry)) {
                byte[] fileSrc = readFully(inputStream);
                classes.put(entry.getName(), fileSrc);
            } catch (IOException e) {
                System.err.println("Failed to open InputStream for entry " + entry.getName() + " creating empty file...");
                classes.put(entry.getName(), new byte[1]);
            }
        }
    }

    public static void createJar(Map<String,byte[]> classMap, Path outFile) {
        ZipEntrySource[] entries = classMap.keySet()
                .stream()
                .map(name -> new ByteSource(name, classMap.get(name)))
                .toArray(ZipEntrySource[]::new);

        ZipUtil.createEmpty(outFile.toFile());
        ZipUtil.addOrReplaceEntries(outFile.toFile(), entries);
    }

    public static void appendToClassMap(Map<String,byte[]> lClassMap, JarEntry entry, JarFile lunarOfficialJar) {
        if(entry.isDirectory()) {
            return;
        }
        try {
            lClassMap.put(entry.getName(), IOUtils.readFully(lunarOfficialJar.getInputStream(entry)));
        } catch (IOException e) {
            throw new RuntimeException("Failed to append " + entry.getRealName() + " to class map");
        }
    }
}
