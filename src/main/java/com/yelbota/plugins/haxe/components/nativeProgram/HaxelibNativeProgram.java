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
package com.yelbota.plugins.haxe.components.nativeProgram;

import org.codehaus.plexus.component.annotations.Component;

import org.apache.maven.artifact.Artifact;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

@Component(role = HaxelibNativeProgram.class, hint = "haxelib")
public final class HaxelibNativeProgram extends AbstractNativeProgram {
	private File localRepositoryPath;

	@Override
    public void initialize(Artifact artifact, File outputDirectory, File pluginHome, Set<String> path)
    {
    	super.initialize(artifact, outputDirectory, pluginHome, path);
    	setupHaxelib();
    }

    public File getLocalRepositoryPath()
    {
    	return this.localRepositoryPath;
    }

    private void setupHaxelib()
    {
        try
        {
           this.localRepositoryPath = new File(pluginHome, "_haxelib");

            if (!this.localRepositoryPath.exists())
            {
                // Setup local repository path
                execute("setup", this.localRepositoryPath.getAbsolutePath());
            }
        }
        catch (NativeProgramException e)
        {
            //throw new Exception("Cant setup haxelib", e);
        }
    }

    @Override
    protected List<String> updateArguments(List<String> arguments)
    {
        List<String> list = new ArrayList<String>();
        File executable = new File(directory, isWindows() ? "haxelib.exe" : "haxelib");
        list.add(executable.getAbsolutePath());
        list.addAll(arguments);

        return list;
    }
}

