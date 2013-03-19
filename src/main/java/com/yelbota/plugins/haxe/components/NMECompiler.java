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

    private File outputDirectory;

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources, boolean verbose) throws Exception
    {
        compile(project, targets, nmml, debug, includeTestSources, verbose, null, false);
    }

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources, boolean verbose, List<String> additionalArguments) throws Exception
    {
        compile(project, targets, nmml, debug, includeTestSources, verbose, additionalArguments, false);
    }

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources, boolean verbose, List<String> additionalArguments, boolean generateDoc) throws Exception
    {
        File nmmlFile = new File(nmml);
        if (nmmlFile.exists()) {
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

                if (targetString != null) {
                    logger.info("Building using '" + nmmlFile.getName() + "' for target '"+targetString+"'.");
                    int returnValue;

                    list = new ArrayList<String>();
                    list.add("update");
                    list.add(nmml);
                    list.add(targetString);
                    list.add("--app-path=" + buildDir);
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

                    returnValue = nme.execute(list, logger);

                    if (returnValue > 0) {
                        throw new Exception("NME update encountered an error and cannot proceed.");
                    }

                    list = new ArrayList<String>();
                    list.add("build");
                    list.add(nmml);
                    list.add(targetString);
                    list.add("--app-path=" + buildDir);
                    if (chxdocIsValid && !xmlGenerated) {
                        list.add("--haxeflag='-xml " + this.outputDirectory.getAbsolutePath() + "/" + TYPES_FILE + "'");
                        xmlGenerated = true;
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
                    returnValue = nme.execute(list, logger);

                    if (returnValue > 0) {
                        throw new Exception("NME build encountered an error and cannot proceed.");
                    }
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
        } else {
            throw new Exception("Unable to build using NME. NMML file '" + nmml + "' does not exist.");
        }
    }

    public void setOutputDirectory(File outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }
}
