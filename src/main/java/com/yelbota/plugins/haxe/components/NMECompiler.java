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

    @Requirement(hint = "nme")
    private NativeProgram nme;

    @Requirement
    private Logger logger;

    private File outputDirectory;

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources) throws Exception
    {
        compile(project, targets, nmml, debug, includeTestSources, null);
    }

    public void compile(MavenProject project, Set<CompileTarget> targets, String nmml, boolean debug, boolean includeTestSources, List<String> additionalArguments) throws Exception
    {
        File nmmlFile = new File(nmml);
        if (nmmlFile.exists()) {
            String targetString = null;
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
                    logger.info("Building using NME for target '"+targetString+"'.");
                    List<String> list = new ArrayList<String>();
                    list.add("test");
                    list.add(nmml);
                    list.add(targetString);
                    //list.add(nmmlFile.getParentFile().getAbsolutePath());
                    nme.execute(list, logger);
                } else {
                    throw new Exception("Encountered an unsupported target to pass to NME: " + target);
                }
            }
        } else {
            throw new Exception("Unable to build using NME. NMML file '" + nmml + "' does not exist.");
        }
    }
}
