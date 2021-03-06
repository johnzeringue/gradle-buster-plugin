package com.github.rundis.buster
import com.github.rundis.buster.internal.BusterJSParser
import com.github.rundis.buster.internal.BusterTestingService
import com.github.rundis.buster.internal.JUnitTestXml
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.InputFiles
import org.gradle.api.tasks.OutputDirectory
import org.gradle.api.tasks.TaskAction

class BusterTestTask extends DefaultTask {
    static NAME = "busterTest"

    @OutputDirectory
    File reportsDir = new File(project.buildDir, "busterTest-results")

    BusterTestingService service


    protected BusterTestTask setService(BusterTestingService service) {
        this.service = service
        this
    }

    @InputFiles
    public List<File> getInputFiles() {
        File configFile = project.buster.resolveConfigFile(project)

        if(!configFile) {
            project.logger.warn("No buster config file (found), unable to determine inputs for task")
            return []
        }

        def config = configFile.text
        def globPatterns = new BusterJSParser().extractGlobPatterns(config)


        def jsFiles = globPatterns.collect{
            project.fileTree (dir: "${configFile.parent}/${it.rootPath}", include: it.includes, excludes: it.excludes)
        }

        def inputFiles = project.files(configFile, jsFiles).files.collect{it}
        project.logger.debug("Incremental check inputfiles: $inputFiles")
        inputFiles
    }

    @TaskAction
    void test() {
        setupReportDir()

        service.prepareForTest()

        try {
            executeTests()
        } finally {
            service.tearDownAfterTest()
        }

    }

    private void setupReportDir() {
        if (!reportsDir.exists()) {
            reportsDir.mkdirs()
        }
    }

    private void executeTests() {
        def stdOut = new ByteArrayOutputStream()
        def busterArgs = busterArgs()
        def execResult = project.exec {
            executable project.buster.testExecutablePath
            args = busterArgs
            standardOutput = stdOut
            ignoreExitValue = true
        }

        new JUnitTestXml(stdOut.toString(), logger)
                .writeReports(reportsDir)
                .validateNoErrors()
                .logResults()

    }

    private List busterArgs() {
        def busterConfig = project.buster
        def busterArgs = ["--reporter", "xml", "--server", busterConfig.serverUrl]
        if (busterConfig.configFile) {
            busterArgs += ["--config", busterConfig.configFile.absolutePath]
        }
        busterArgs
    }

}
