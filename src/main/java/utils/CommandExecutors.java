package utils;

import org.apache.maven.cli.MavenCli;

import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.logging.Logger;

public class CommandExecutors {

    private static Logger logger = Logger.getLogger(CommandExecutors.class.getSimpleName());
    private static SimpleDateFormat simpleDateFormat = new SimpleDateFormat("yyyy-MM-dd hh:mm:ss");

    private static void setTimestamp(TimsestampType timestampType) {
        logger.info(String.valueOf(timestampType));
        logger.info(simpleDateFormat.format(new Date()));
        logger.info("---------------------------------");
    }

    private enum TimsestampType {
        START,
        END
    }

    public static boolean executeTerminal(String cmd, Boolean redirect, String nameOfSrcFilesList) {

        logger.info("Exec: " + cmd);

        setTimestamp(TimsestampType.START);

        ProcessBuilder builder = new ProcessBuilder(cmd.split("\\s+"));

        File file = new File(nameOfSrcFilesList);

        if (redirect) {
            builder.redirectOutput(ProcessBuilder.Redirect.appendTo(file));
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        } else {
            builder.redirectOutput(ProcessBuilder.Redirect.INHERIT);
            builder.redirectError(ProcessBuilder.Redirect.INHERIT);
        }

        try {
            final Process process = builder.start();
            process.waitFor();
            setTimestamp(TimsestampType.END);

        } catch (Exception e) {
            logger.severe(e.getMessage());
            return false;
        }

        return true;
    }
    public static boolean executeTerminal(String cmd) {
        return executeTerminal(cmd, false, "");
    }

    public static boolean executeMaven(Path workingDir, String[] commands){
        MavenCli cli = new MavenCli();
        int exitCode = cli.doMain(
                commands,
                workingDir.toString(),
                System.out,
                System.err
        );

        return exitCode == 0;
    }

   public static Map<String, Path> getPathThroughProps(Path props) throws IOException {

       FileReader reader=new FileReader(String.valueOf(props));

       Properties properties=new Properties();
       properties.load(reader);

       Map <String, Path> propertiesMap = new HashMap<>();

       for (Map.Entry<Object, Object> entry: properties.entrySet()){
           propertiesMap.put((String)entry.getKey(), Paths.get((String)entry.getValue()));
       }

       return propertiesMap;
    }

    public static String stripExtension(String str) {
        if (str == null) return null;
        int pos = str.lastIndexOf(".");
        if (pos == -1) return str;
        return str.substring(0, pos);
    }

    public static Set<Path> fileSearch(String name, Path dir) {

        Set<Path> paths = new HashSet<>();

        File wantedDir = dir.toFile();
        File[] list = wantedDir.listFiles();
        if (list == null) return new HashSet<>();

        for (File f : list) {
            if (f.isFile() && f.getName().matches(name)) {
                paths.add(Paths.get(f.getAbsolutePath()));
            }
            if (f.isDirectory()) {
                Set<Path> newPaths = fileSearch(name, f.toPath());
                paths.addAll(newPaths);
            }
        }
        return paths;
    }
}
