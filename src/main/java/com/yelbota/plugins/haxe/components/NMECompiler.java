/**
 * Copyright (C) 2012 https://github.com/yelbota/haxe-maven-plugin
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.yelbota.plugins.haxe.components;

import com.yelbota.plugins.haxe.components.nativeProgram.NativeProgram;
import com.yelbota.plugins.haxe.utils.CompileTarget;
import com.yelbota.plugins.haxe.utils.HarMetadata;
import com.yelbota.plugins.haxe.utils.HaxeFileExtensions;
import org.apache.maven.artifact.Artifact;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.archiver.zip.ZipUnArchiver;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.io.BufferedReader;
import javax.xml.bind.JAXBContext;
import javax.xml.bind.JAXBException;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Component(role = NMECompiler.class)
public final class NMECompiler {
    private static final String TYPES_FILE = "types.xml";

    @Requirement(hint = "nme")
    private NativeProgram nme;

    @Requirement(hint = "chxdoc")
    private NativeProgram chxdoc;

    @Requirement
    private Logger logger;

    private boolean debug = false;
    private boolean verbose = false;
    private boolean generateDoc = false;

    private File outputDirectory;

    public void initialize(boolean debug, boolean verbose)
    {
        initialize(debug, verbose, false);
    }

    public void initialize(boolean debug, boolean verbose, boolean generateDoc)
    {
        this.debug = debug;
        this.verbose = verbose;
        this.generateDoc = generateDoc;
    }

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml) throws Exception
    {
        compile(project, targets, nmml, null, null, null);
    }

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, List<String> additionalArguments) throws Exception
    {
        compile(project, targets, nmml, additionalArguments, null, null);
    }

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, List<String> additionalArguments,
        String appMain, String appFile) throws Exception
    {
        File nmmlFile = assertNMML(nmml);
        String targetString = null;
        List<String> list;
        boolean chxdocIsValid = false;
        boolean xmlGenerated = false;
        String buildDir = this.outputDirectory.getAbsolutePath();

        if (generateDoc) {
            chxdocIsValid = chxdoc != null && chxdoc.getInitialized();
        }
        for (CompileTarget target : targets)
        {
            targetString = getTargetStringForTarget(target);

            if (targetString != null) {
                logger.info("Building using '" + nmmlFile.getName() + "' for target '"+targetString+"'.");

                list = getStandardArgumentsList(nmml, targetString, buildDir, appMain, appFile, additionalArguments);
                execute("update", list);

                list = getStandardArgumentsList(nmml, targetString, buildDir, appMain, appFile, additionalArguments);
                if (chxdocIsValid && !xmlGenerated) {
                    list.add("--haxeflag='-xml " + this.outputDirectory.getAbsolutePath() + "/" + TYPES_FILE + "'");
                    xmlGenerated = true;
                }
                execute("build", list);
            } else {
                throw new Exception("Encountered an unsupported target to pass to NME: " + target);
            }
        }

        if (chxdocIsValid && xmlGenerated) {
            list = new ArrayList<String>();
            list.add("--output=docs");
            list.add("--title=Documentation");
            list.add("--file=" + TYPES_FILE);
            chxdoc.execute(list, logger);
        }
    }

    public List<String> displayHxml(MavenProject project, Set<CompileTarget> targets, String nmml, List<String> additionalArguments,
        String appMain, String appFile) throws Exception
    {
        assertNMML(nmml);
        List<String> hxmlOutput = new ArrayList<String>();
        String buildDir = this.outputDirectory.getAbsolutePath();
        for (CompileTarget target : targets) {
            if (hxmlOutput.size() > 0) {
                hxmlOutput.add("");
                hxmlOutput.add("--next");
                hxmlOutput.add("");
            }
            hxmlOutput.addAll(displayHxml(project, target, nmml, additionalArguments, appMain, appFile));
        }
        return hxmlOutput;
    }

    public List<String> displayHxml(MavenProject project, CompileTarget target, String nmml, List<String> additionalArguments,
        String appMain, String appFile) throws Exception
    {
        assertNMML(nmml);
        List<String> hxmlOutput = new ArrayList<String>();
        String buildDir = this.outputDirectory.getAbsolutePath();
        String targetString = getTargetStringForTarget(target);
        if (targetString != null) {
            hxmlOutput.add("## " + targetString);

            List<String> list = new ArrayList<String>();
            list.add("display");
            list.addAll(
                getStandardArgumentsList(nmml, targetString, buildDir, appMain, appFile, additionalArguments)
            );
            BufferedReader br = nme.executeIntoBuffer(list);
            String line;
            while ((line = br.readLine()) != null) {
                hxmlOutput.add(line);
            }
        }
        return hxmlOutput;
    }

    private void execute(String command, List<String> arguments) throws Exception
    {
        List<String> list = new ArrayList<String>();
        list.add(command);
        list.addAll(arguments);
        int returnValue = nme.execute(list, logger);

        if (returnValue > 0) {
            throw new Exception("NME compiler encountered an error and cannot proceed.");
        }
    }

    private File assertNMML(String nmml) throws Exception
    {
        File nmmlFile = new File(nmml);
        if (!nmmlFile.exists()) {
            throw new Exception("Unable to build using NME. NMML file '" + nmml + "' does not exist.");
        }
        return nmmlFile;
    }

    private String getTargetStringForTarget(CompileTarget target)
    {
        String targetString = null;
        switch (target)
        {
            case flash:
                targetString = "flash";
                break;
            case html5:
                targetString = "html5";
                break;
            case ios:
                targetString = "ios";
                break;
            case android:
                targetString = "android";
                break;
            case cpp:
                targetString = "cpp";
                break;
        }
        return targetString;
    }

    private List<String> getStandardArgumentsList(String nmml, String targetString, String buildDir, String appMain, String appFile, List<String> additionalArguments)
    {
        List<String> list = new ArrayList<String>();
        list.add(nmml);
        list.add(targetString);
        list.add("--app-path=" + buildDir);
        if (appMain != null) {
            list.add("--app-main=" + appMain);
        }
        if (appFile != null) {
            list.add("--app-file=" + appFile);
        }
        if (debug) {
            list.add("-debug");
            list.add("--haxeflag='-D log'");
        }
        if (verbose) {
            list.add("-verbose");
        }

        if (additionalArguments != null) {
            List<String> compilerArgs = new ArrayList<String>();
            for (String arg : additionalArguments) {
                compilerArgs.add("--haxeflag='" + arg + "'");
            }
            list.addAll(compilerArgs);
        }
        return list;
    }

    public void setOutputDirectory(File outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }
}
