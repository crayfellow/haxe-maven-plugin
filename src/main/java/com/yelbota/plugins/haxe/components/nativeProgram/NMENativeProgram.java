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

import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.util.StringUtils;

import org.apache.maven.artifact.Artifact;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import com.yelbota.plugins.haxe.components.nativeProgram.NativeProgramException;

@Component(role = NativeProgram.class, hint = "nme")
public final class NMENativeProgram extends AbstractNativeProgram {

    @Requirement(hint = "haxe")
    private NativeProgram haxe;

    @Requirement(hint = "neko")
    private NativeProgram neko;

    @Requirement(hint = "haxelib")
    private HaxelibNativeProgram haxelib;

    @Override
    public void initialize(Artifact artifact, File outputDirectory, File pluginHome, Set<String> path)
    {
		super.initialize(artifact, outputDirectory, pluginHome, path);

		try
        {
        	haxelib.execute("set", "nme", artifact.getVersion());
        }
        catch (NativeProgramException e)
        {
            System.out.println("bummer");
        }
	}

	@Override
    protected File getUnpackDirectoryForArtifact(Artifact artifact) throws NativeProgramException
    {
    	File nmeHaxelibHome = new File(haxelib.getLocalRepositoryPath(), artifact.getArtifactId());
    	if (!nmeHaxelibHome.exists()) {
			nmeHaxelibHome.mkdirs();
		}

		File currentFile = new File(nmeHaxelibHome, ".current");
		if (!currentFile.exists()) {
			try {
				currentFile.createNewFile();
            } catch (IOException e) {
           		throw new NativeProgramException("Unable to create pointer for NME haxelib.", e);
            }
		}
        File soPath = new File(nmeHaxelibHome, artifact.getVersion().replace(".", ","));
        return soPath;
    }

    @Override
    protected List<String> updateArguments(List<String> arguments)
    {
        List<String> list = new ArrayList<String>();

		File executable = new File(neko.getInstalledPath(), isWindows() ? "neko.exe" : "neko");
        list.add(executable.getAbsolutePath());
        list.add(getInstalledPath() + "/run.n");
        list.addAll(arguments);

        return list;
    }

    @Override
    protected String[] getEnvironment()
    {
    	String haxeHome = haxe.getInstalledPath();
    	String haxeLibraryPath = haxeHome + "/std:.";
    	String nekoHome = neko.getInstalledPath();
    	String nmeHome = getInstalledPath();
        path.add(haxeLibraryPath);

        String[] env = new String[]{
                "HAXEPATH=" + haxeHome,
                "NEKOPATH=" + nekoHome,
                "NMEPATH=" + nmeHome,
                "HAXE_LIBRARY_PATH=" + haxeLibraryPath,
                "LD_LIBRARY_PATH=" + nekoHome,
                "PATH=" + StringUtils.join(path.iterator(), ":"),
                "HOME=" + pluginHome.getAbsolutePath()
        };
        return env;
    }
}
