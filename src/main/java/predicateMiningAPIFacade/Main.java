package predicateMiningAPIFacade;

import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import predicatesGenerator.DaikonRunner;
import testsGenerator.RandoopRunner;
import utils.MavenProjectRunner;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Logger;

import static utils.CommandExecutors.executeTerminal;

public class Main extends MavenProjectRunner {

    private static Logger logger = Logger.getLogger(Main.class.getSimpleName());

    public Main(String baseDir) throws IOException, XmlPullParserException {
        super(baseDir);

    }

    //"C:\\Users\\Neverland\\.m2\\repository"
    // "org.apache.commons:commons-math3:3.6.1"
    //"/home/suntrie/IdeaProjects/jackson-example/jackson-example";

    public static void main(String[] args) throws IOException, XmlPullParserException {

        Params params = new Params();

        CmdLineParser parser = new CmdLineParser(params);

        try {
            parser.parseArgument(args);

        } catch (CmdLineException e) {
            logger.severe(e.getMessage());
            return;
        }

        update_ppt_map(params.libraryName, params.pptMapPath, params.projectPath);
    }


    public static boolean update_ppt_map(String libraryName, String invariantsFilePath, String projectPath)
            throws IOException, XmlPullParserException {


        MavenProjectRunner projectRunner = new MavenProjectRunner(projectPath);

        if (!projectRunner.prepareAndInstallProject())
            return false;

        RandoopRunner randoopRunner = new RandoopRunner(projectRunner);
        randoopRunner.generateTests();

        if (!projectRunner.buildProject())
            return false;

        int firstPointPosition = invariantsFilePath.substring(0, invariantsFilePath.lastIndexOf('.')).lastIndexOf('.');

        String newInvariantsFilePath = invariantsFilePath.substring(0, firstPointPosition);
        String postfix = invariantsFilePath.substring(firstPointPosition);

        newInvariantsFilePath = newInvariantsFilePath + "_" + projectRunner.getTimestamp() + postfix;

        if ((new DaikonRunner(projectRunner).generateInvariantsPptMap(libraryName, Paths.get(newInvariantsFilePath))).isPresent()) {

            File previousInvariantsFile = new File(invariantsFilePath);

            if (previousInvariantsFile.exists()) {

                List<String> cmd = new ArrayList<>();

                cmd.add("java");
                cmd.add("-classpath");

                cmd.add(daikonPath);

                cmd.add("daikon.MergeInvariants");
                cmd.add("-o");
                cmd.add(invariantsFilePath);
                cmd.add(invariantsFilePath);
                cmd.add(newInvariantsFilePath);

                if (!executeTerminal(String.join(" ", cmd))) {
                    return false;
                }

                if (!new File(newInvariantsFilePath).delete()){
                    logger.severe("Invariants temporary file wasn't deleted.");
                }

            }else{
                Path source = Paths.get(newInvariantsFilePath);
                Files.move(source, source.resolveSibling(invariantsFilePath));
            }

        } else
            return false;

        return true;
    }

    private static class Params {

        @Option(name = "-projectPath", usage = "Path to the project that uses library-in-hand")
        String projectPath;

        @Option(name = "-libraryName", usage = "Name of library in-hand")
        String libraryName;

        @Option(name = "-pptMapPath", usage = "Path to the predicates file")
        String pptMapPath;

    }

}
