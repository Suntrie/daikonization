package testsGenerator;

import com.repoMiner.AetherTreeConstructor;
import daikon.inv.binary.sequenceScalar.SeqFloatLessEqual;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import utils.ProjectRunner;

import java.io.File;
import java.io.IOException;
import java.util.*;

import static utils.CommandExecutors.executeTerminal;

public class RandoopRunner extends ProjectRunner {

    public RandoopRunner(String baseDir)
            throws IOException, XmlPullParserException {
        super(baseDir);
    }

    public boolean generateTests() throws IOException {

        List<String> cmd = new ArrayList<>();

        cmd.add("java");
        cmd.add("-classpath");

        String classPath = getFullClassPathForProjectExploration();

        classPath=classPath.concat(File.pathSeparator).concat(randoopPath);

        cmd.add(classPath);

        cmd.add("randoop.main.Main");
        cmd.add("gentests");

        AetherTreeConstructor aetherTreeConstructor = new AetherTreeConstructor
                ("/home/suntrie/.m2/repository");

        Set<String> filters=new HashSet<>();
        filters.add("java.lang");

        Optional<Set<Class>> projectClasses=aetherTreeConstructor.getPackageClasses(
                getMavenCoords(), filters);

        if (!projectClasses.isPresent())
            return false;

        for (Class clazz: projectClasses.get()){
            cmd.add("--testclass=" + clazz.getName());
        }

        cmd.add("--junit-reflection-allowed=false");
        cmd.add("--no-error-revealing-tests=true");
        cmd.add("--check-compilable");
        cmd.add("--output-limit=100");
        cmd.add("--maxsize=100");
        cmd.add("--only-test-public-members");
        cmd.add("--stop-on-error-test=true");
        cmd.add("--junit-output-dir="+projectDirs.get("testSourceDirectory"));

        if (!executeTerminal(String.join(" ", cmd))) {
            return false;
        }

        return true;
    }

}
