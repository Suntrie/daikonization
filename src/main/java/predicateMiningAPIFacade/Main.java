package predicateMiningAPIFacade;

import daikon.FileIO;
import daikon.PptMap;
import daikon.PptTopLevel;
import daikon.inv.Invariant;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import org.kohsuke.args4j.CmdLineException;
import org.kohsuke.args4j.CmdLineParser;
import org.kohsuke.args4j.Option;
import predicatesGenerator.DaikonRunner;
import testsGenerator.RandoopRunner;
import utils.ProjectRunner;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Logger;

import static utils.CommandExecutors.executeTerminal;

public class Main extends ProjectRunner {

    private static Logger logger = Logger.getLogger(Main.class.getSimpleName());

    public Main(String baseDir) throws IOException, XmlPullParserException {
        super(baseDir);

    }

    //"C:\\Users\\Neverland\\.m2\\repository"
    // "org.apache.commons:commons-math3:3.6.1"
    //"/home/suntrie/IdeaProjects/jackson-example/jackson-example";

    public static void main(String[] args) throws IOException, XmlPullParserException {

//        new RandoopRunner("/home/suntrie/IdeaProjects/jackson-example/jackson-example").generateTests();

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



    public static boolean update_ppt_map(String libraryName, String invariantsFilePath, String projectPath){
        try {

            ProjectRunner projectRunner = new ProjectRunner(projectPath);

            if (!projectRunner.prepareProject())
                return false;

            RandoopRunner randoopRunner = new RandoopRunner(projectPath);
            randoopRunner.generateTests();

            if (!projectRunner.buildProject())
                return false;

            PptMap pptMap = new DaikonRunner(projectPath).generateInvariantsPptMap(libraryName);

            for (PptTopLevel ppt:pptMap.all_ppts()){
                for (Invariant inv: ppt.getInvariants())
                    System.out.println(inv);
            }

            File previousInvariantsFile = new File(invariantsFilePath);

            if (!previousInvariantsFile.exists()) {
                FileIO.write_serialized_pptmap(pptMap, new File(invariantsFilePath));
            }
            else{

                String newInvariantsFilePath = invariantsFilePath+"_"+projectRunner.getTimestamp();

                FileIO.write_serialized_pptmap(pptMap, new File(newInvariantsFilePath));
                List<String> cmd = new ArrayList<>();

                cmd.add("java");
                cmd.add("-classpath");

                cmd.add(daikonPath);

                cmd.add("daikonMergeInvariants");
                cmd.add("-o");
                cmd.add(invariantsFilePath);
                cmd.add(invariantsFilePath);
                cmd.add(newInvariantsFilePath);

                if (!executeTerminal(String.join(" ", cmd))) {
                    return false;
                }
            }

        } catch (IOException | XmlPullParserException e) {
            logger.severe(e.getMessage());
            return false;
        }

        return true;
    }

    private static class Params{

            @Option(name = "-projectPath", usage = "Path to the project that uses library-in-hand")
            String projectPath;

            @Option(name = "-libraryName", usage = "Name of library in-hand")
            String libraryName;

            @Option(name = "-pptMapPath", usage = "Path to the predicates file")
            String pptMapPath;

    }

}
