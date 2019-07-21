package testGenerator;

import com.github.javaparser.JavaParser;
import com.github.javaparser.ParseResult;
import com.github.javaparser.ast.*;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.VariableDeclarator;
import com.github.javaparser.ast.expr.*;
import com.github.javaparser.ast.stmt.BlockStmt;
import com.github.javaparser.ast.stmt.ExpressionStmt;
import com.github.javaparser.ast.stmt.Statement;
import com.github.javaparser.ast.type.ClassOrInterfaceType;
import com.github.javaparser.ast.visitor.VoidVisitorAdapter;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.Model;
import org.apache.maven.model.io.xpp3.MavenXpp3Reader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;
import utils.MavenProjectUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.*;
import java.util.logging.Logger;
import java.util.stream.Collectors;

import static java.nio.file.StandardOpenOption.CREATE;
import static java.nio.file.StandardOpenOption.TRUNCATE_EXISTING;
import static utils.CommandExecutors.fileSearch;

public class SimpleTestsDownGrader extends MavenProjectUtils {

    private static Logger logger = Logger.getLogger(SimpleTestsDownGrader.class.getSimpleName());

    private final JavaParser javaParser = new JavaParser();

    final private Set<Path> pathsToTestSourceFiles;
    private File generatedTestDriver;
    final private String testDriverClassName;
    final private String testDriverPackageName;
    final private CompilationUnit testDriverCompilationUnit = new CompilationUnit();

    private static final String RUNNER_CLASS_NAME_POSTFIX = "_scaffolding";

    private static Set<Class> annotationClasses = new HashSet<Class>() {{
        add(org.junit.Test.class);
        add(org.junit.BeforeClass.class);
        add(org.junit.AfterClass.class);
        add(org.junit.Before.class);
        add(org.junit.After.class);
        add(org.junit.Ignore.class);
    }};


    public SimpleTestsDownGrader(Path baseDir) throws IOException, XmlPullParserException {

        super(baseDir.toString());

        this.pathsToTestSourceFiles = fileSearch("(?i:(.*)test(.*)).java", Paths.get(getEvosuiteTestsFolder()));

        if (pathsToTestSourceFiles.size() == 0)
            throw new IllegalArgumentException("Tests folder is empty");

        Long timestamp = System.currentTimeMillis();

        this.testDriverClassName = "GeneratedTestDriver_" + timestamp;
        this.testDriverPackageName = "generatedTests_" + timestamp;

    }

    public boolean createExecutableTestSuite() {

        try {
            modifyDependencyVersion();
        } catch (IOException | XmlPullParserException e) {
            logger.severe(e.getMessage());
            return false;
        }

        MethodDeclaration testDriverMethodDeclaration = prepareTestDriverCompilationUnit(testDriverClassName, testDriverPackageName);

        for (Path path : this.pathsToTestSourceFiles) {

            if (path.toString().endsWith(RUNNER_CLASS_NAME_POSTFIX + ".java")) {
                try {
                    Files.delete(path);
                } catch (IOException e) {
                    logger.severe(e.getMessage());
                    return false;
                }
                continue;
            }

            String stringPath = String.valueOf(path);

            ParseResult result;
            try {
                result = javaParser.parse(new FileInputStream(stringPath));
            } catch (FileNotFoundException e) {
                logger.severe(e.getMessage());
                return false;
            }

            if (!result.isSuccessful()) {
                return false;
            } else {

                if (!result.getResult().isPresent())
                    return false;

                AnnotationVisitor annotationVisitor;
                try {
                    annotationVisitor = processTestSourceFile(path, (CompilationUnit) result.getResult().get());
                } catch (IOException e) {
                    logger.severe(e.getMessage());
                    return false;
                }

                addMethodCallsToDriverClass(annotationVisitor, testDriverMethodDeclaration);


            }
        }

        Path packagePath = Paths.get(getEvosuiteTestsFolder() + File.separator + testDriverPackageName);

        try {
            Files.createDirectories(packagePath);
        } catch (IOException e) {
            logger.severe(e.getMessage());
            return false;
        }

        this.generatedTestDriver = new File(packagePath + File.separator + testDriverClassName + ".java");

        try {
            Files.write(Paths.get(generatedTestDriver.getPath()),
                    Collections.singleton(testDriverCompilationUnit.toString()),
                    StandardCharsets.UTF_8, new StandardOpenOption[]{CREATE});
        } catch (IOException e) {
            logger.severe(e.getMessage());
            return false;
        }

        return true;
    }

    private AnnotationVisitor processTestSourceFile(Path path, CompilationUnit compilationUnit) throws IOException {

        AnnotationVisitor annotationVisitor = new AnnotationVisitor();

        compilationUnit.accept(annotationVisitor, null);

        for (Node node : annotationVisitor.nodesToRemove) {
            node.remove();
        }

        MethodDeclaration setUp = null, tearDown = null;

        if (annotationVisitor.beforeEachMethods.size() != 0) {
            setUp = mergeFrameMethods(annotationVisitor.beforeEachMethods, "setUp");
        }

        if (annotationVisitor.afterEachMethods.size() != 0) {
            tearDown = mergeFrameMethods(annotationVisitor.afterEachMethods, "tearDown");
        }

        annotationVisitor.setUp = setUp;
        annotationVisitor.tearDown = tearDown;

        correctInheritance(annotationVisitor);

        addImportStatement(annotationVisitor, false, compilationUnit);

        Files.write(path,
                Collections.singleton(compilationUnit.toString()),
                StandardCharsets.UTF_8, new StandardOpenOption[]{CREATE, TRUNCATE_EXISTING});
        return annotationVisitor;
    }

    private void correctInheritance(AnnotationVisitor annotationVisitor) {

        for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : annotationVisitor.classesToProcess) {

            if (!annotationVisitor.frameworkClasses.contains(classOrInterfaceDeclaration)) {


                ClassOrInterfaceType toRemove = null;

                for (ClassOrInterfaceType t : classOrInterfaceDeclaration.getExtendedTypes()) {
                    if (t.getNameAsString().endsWith(RUNNER_CLASS_NAME_POSTFIX)) {
                        toRemove = t;
                        break;
                    }
                }

                if (toRemove != null)
                    classOrInterfaceDeclaration.getExtendedTypes().remove(toRemove);

                classOrInterfaceDeclaration.addExtendedType("TestCase");
            }
        }
    }

    private void modifyDependencyVersion() throws IOException, XmlPullParserException {
        MavenXpp3Reader reader = new MavenXpp3Reader();

        File pomFile = new File(String.valueOf(this.projectDirs.get("baseDir")), "/pom.xml");
        Model model = reader.read(new FileInputStream(pomFile));

        boolean flag = false;

        for (Dependency dependency : model.getDependencies()) {
            if (dependency.getGroupId().contains("junit") &&
                    dependency.getArtifactId().contains("junit")) {
                dependency.setVersion("3.8.2");
                flag = true;
            }
        }

        if (!flag) {
            addDependency("junit", "junit", "3.8.2");
        }
    }

    private MethodDeclaration mergeFrameMethods(Set<MethodDeclaration> methods, String methodName) {

        if (methods.iterator().hasNext()) {

            ClassOrInterfaceDeclaration classOrInterfaceDeclaration = getClassOrInterfaceDeclaration(
                    methods.iterator().next());
            MethodDeclaration addedMethodDeclaration =
                    classOrInterfaceDeclaration.addMethod(methodName, Modifier.Keyword.PUBLIC, Modifier.Keyword.STATIC);

            for (MethodDeclaration methodDeclaration : methods) {

                NodeList<Statement> statements = methodDeclaration.getBody().get().getStatements();

                for (Statement statement : statements) {
                    addedMethodDeclaration.setBody(addedMethodDeclaration.getBody().get().
                            addStatement(statement));
                }
            }

            Iterator<MethodDeclaration> methodDeclarationIterator = methods.iterator();
            while (methodDeclarationIterator.hasNext()) {

                MethodDeclaration methodDeclaration = methodDeclarationIterator.next();

                methodDeclarationIterator.remove();
                methodDeclaration.remove();
            }

            return addedMethodDeclaration;
        }

        return null;
    }

    private MethodDeclaration prepareTestDriverCompilationUnit(String testDriverName, String packageName) {
        testDriverCompilationUnit.setPackageDeclaration(packageName);

        ClassOrInterfaceDeclaration generatedClass = testDriverCompilationUnit.addClass(testDriverName);
        MethodDeclaration methodDeclaration =
                generatedClass.addMethod("main",
                        Modifier.Keyword.PUBLIC,
                        Modifier.Keyword.STATIC);
        methodDeclaration.addAndGetParameter("String []", "args");
        methodDeclaration.addThrownException(Throwable.class);

        return methodDeclaration;
    }

    private void addMethodCallsToDriverClass(AnnotationVisitor annotationVisitor, MethodDeclaration testDriverMethodDeclaration) {

        addImportStatement(annotationVisitor, true, testDriverCompilationUnit);

        addMethodCallsToMethodDeclaration(annotationVisitor.beforeAllMethods, testDriverMethodDeclaration, null);
        addObjectMethodCallsToMethodDeclaration(annotationVisitor.testMethods, testDriverMethodDeclaration, annotationVisitor.setUp,
                annotationVisitor.tearDown);
        addMethodCallsToMethodDeclaration(annotationVisitor.afterAllMethods, testDriverMethodDeclaration, null);


    }

    private void addImportStatement(AnnotationVisitor annotationVisitor, boolean driverClass, CompilationUnit targetUnit) {


        for (ClassOrInterfaceDeclaration classOrInterfaceDeclaration : driverClass ? annotationVisitor.classesToProcess :
                annotationVisitor.frameworkClasses) {

            targetUnit.addImport(classOrInterfaceDeclaration.getFullyQualifiedName().orElse
                    (classOrInterfaceDeclaration.getNameAsString()));
        }

    }

    private void addMethodCallsToMethodDeclaration(Set<MethodDeclaration> methodDeclarations,
                                                   MethodDeclaration testDriverMethodDeclaration,
                                                   VariableDeclarationExpr variableDeclarationExpr) {
        for (MethodDeclaration methodDeclaration : methodDeclarations) {

            if (methodDeclaration == null) continue;

            MethodCallExpr call = variableDeclarationExpr == null ? new MethodCallExpr(
                    ((ClassOrInterfaceDeclaration) methodDeclaration.getParentNode().get())
                            .getFullyQualifiedName().get() + "." +
                            methodDeclaration.getNameAsString()) :
                    new MethodCallExpr(variableDeclarationExpr.getVariables().iterator().next().getNameAsExpression(),
                            methodDeclaration.getNameAsString());

            testDriverMethodDeclaration.setBody(
                    testDriverMethodDeclaration.getBody().get().
                            addStatement(new ExpressionStmt(call)));
        }
    }


    private void addObjectMethodCallsToMethodDeclaration(Set<MethodDeclaration> methodDeclarations, MethodDeclaration testDriverMethodDeclaration,
                                                         MethodDeclaration setUp, MethodDeclaration tearDown) {
        for (MethodDeclaration methodDeclaration : methodDeclarations) {

            ClassOrInterfaceType classOrInterfaceType = getClassOrInterfaceType(methodDeclaration);

            ObjectCreationExpr objectCreationExpr = new ObjectCreationExpr(null, //?
                    classOrInterfaceType, new NodeList<Expression>());

            VariableDeclarationExpr variableDeclarationExpr = new VariableDeclarationExpr
                    (new VariableDeclarator(classOrInterfaceType, "testObj_" + System.currentTimeMillis(),
                            objectCreationExpr));

            testDriverMethodDeclaration.setBody(
                    getTestDriverMethodBody(testDriverMethodDeclaration).
                            addStatement(new ExpressionStmt(variableDeclarationExpr)));

            addMethodCallsToMethodDeclaration(Collections.singleton(setUp), testDriverMethodDeclaration, variableDeclarationExpr);

            MethodCallExpr call = new MethodCallExpr(variableDeclarationExpr.getVariable(0).
                    getNameAsExpression(), methodDeclaration.getNameAsString());

            testDriverMethodDeclaration.setBody(
                    testDriverMethodDeclaration.getBody().get().
                            addStatement(new ExpressionStmt(call)));


            addMethodCallsToMethodDeclaration(Collections.singleton(tearDown), testDriverMethodDeclaration, variableDeclarationExpr);

        }
    }

    private BlockStmt getTestDriverMethodBody(MethodDeclaration testDriverMethodDeclaration) {
        return testDriverMethodDeclaration.getBody().get();
    }

    private ClassOrInterfaceType getClassOrInterfaceType(MethodDeclaration methodDeclaration) {
        ClassOrInterfaceDeclaration classOrInterfaceDeclaration = getClassOrInterfaceDeclaration(methodDeclaration);

        String classOrInterfaceDeclarationName = classOrInterfaceDeclaration.getFullyQualifiedName().get();

        return javaParser.parseClassOrInterfaceType(classOrInterfaceDeclarationName).getResult().orElse(null);
    }

    private ClassOrInterfaceDeclaration getClassOrInterfaceDeclaration(MethodDeclaration methodDeclaration) {
        return (ClassOrInterfaceDeclaration) (methodDeclaration
                .getParentNode().get());
    }

    // Just now there can't exist nested items
    private class AnnotationVisitor extends VoidVisitorAdapter<Void> {

        private Set<MethodDeclaration> testMethods = new HashSet<>();
        private Set<MethodDeclaration> beforeAllMethods = new HashSet<>();
        private Set<MethodDeclaration> afterAllMethods = new HashSet<>();
        private Set<MethodDeclaration> beforeEachMethods = new HashSet<>();
        private Set<MethodDeclaration> afterEachMethods = new HashSet<>();

        private MethodDeclaration setUp, tearDown;

        private List<Node> nodesToRemove = new ArrayList<>();


        private final Set<ClassOrInterfaceDeclaration> frameworkClasses =
                new HashSet<ClassOrInterfaceDeclaration>() {{
                    add(new ClassOrInterfaceDeclaration().setName("junit.framework.TestCase"));
                    add(new ClassOrInterfaceDeclaration().setName("junit.framework.*"));
                    add(new ClassOrInterfaceDeclaration().setName("junit.framework.Assert.*"));

                }};

        private List<ClassOrInterfaceDeclaration> classesToProcess = new ArrayList<ClassOrInterfaceDeclaration>() {{
            addAll(frameworkClasses);
        }};

        private void processAnnotation(AnnotationExpr n) {

            List<String> klassNames = annotationClasses.stream().map(klass ->
                    klass.getSimpleName()).collect(Collectors.toList());

            if (!klassNames.contains(n.getName().asString())) {
                nodesToRemove.add(n);
                return;
            }


            if (n.getParentNode().isPresent()) {
                Node it = n.getParentNode().get();

                String annotationName = n.getName().toString();

                MethodDeclaration methodDeclaration = ((MethodDeclaration) it);

                if (annotationName.matches("Test")) {
                    methodDeclaration.setName("test".concat(methodDeclaration.getNameAsString()));
                    testMethods.add(methodDeclaration);
                } else if (annotationName.matches("BeforeClass")) {
                    beforeAllMethods.add(methodDeclaration);
                } else if (annotationName.matches("AfterClass")) {
                    afterAllMethods.add(methodDeclaration);
                } else if (annotationName.matches("Before")) {
                    beforeEachMethods.add(methodDeclaration);
                } else if (annotationName.matches("After")) {
                    afterEachMethods.add(methodDeclaration);
                }

                nodesToRemove.add(n);
            }

        }

        @Override
        public void visit(MarkerAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            processAnnotation(n);
        }

        @Override
        public void visit(NormalAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            processAnnotation(n);
        }

        @Override
        public void visit(SingleMemberAnnotationExpr n, Void arg) {
            super.visit(n, arg);
            processAnnotation(n);
        }

        @Override
        public void visit(ClassOrInterfaceDeclaration n, Void arg) {
            super.visit(n, arg);

            if (!n.isInterface()) {

                if (!n.getFullyQualifiedName().get().endsWith(RUNNER_CLASS_NAME_POSTFIX)) {
                    classesToProcess.add(n);
                }
            }

        }

        @Override
        public void visit(ImportDeclaration n, Void arg) {
            super.visit(n, arg);
            nodesToRemove.add(n);
        }

    }
}