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

@Component(role = MUnitCompiler.class)
public final class MUnitCompiler {

    @Requirement(hint = "munit")
    private NativeProgram munit;

    @Requirement
    private Logger logger;

    private File outputDirectory;

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources) throws Exception
    {
        compile(project, targets, nmml, debug, includeTestSources, null);
    }

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources, List<String> additionalArguments) throws Exception
    {
        runWithArgument("-norun", additionalArguments);
    }

    public void run(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources) throws Exception
    {
        run(project, targets, nmml, debug, includeTestSources, null);
    }

    public void run(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources, List<String> additionalArguments) throws Exception
    {
        runWithArgument("-nogen", additionalArguments);
    }

    private void runWithArgument(String argument, List<String> additionalArguments) throws Exception
    {
        List<String> arguments = updateArguments(additionalArguments);
        if (argument != null) {
            arguments.add(argument);
        }
        int returnValue = munit.execute(arguments, logger);

        if (returnValue > 0) {
            throw new Exception("MassiveUnit test encountered an error and cannot proceed.");
        }
    }

    private List<String> updateArguments(List<String> additionalArguments)
    {     
        List<String> list = new ArrayList<String>();
        list.add("test");
        list.add("test.hxml");
        list.add("test_src");
        list.add("test_bin");
        list.add("test_report");
        list.add("-coverage");
        list.add("-result-exit-code");
        if (additionalArguments != null) {
            list.addAll(additionalArguments);
        }
        return list;
    }

    public boolean getHasRequirements()
    {
        return munit != null && munit.getInitialized();
    }

    public void setOutputDirectory(File outputDirectory)
    {
        this.outputDirectory = outputDirectory;
    }
}
