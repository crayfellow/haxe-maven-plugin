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

@Component(role = NativeProgram.class, hint = "munit")
public final class MUnitNativeProgram extends AbstractNativeProgram {

    @Requirement(hint = "haxe")
    private NativeProgram haxe;

    @Requirement(hint = "neko")
    private NativeProgram neko;

    @Requirement(hint = "haxelib")
    private HaxelibNativeProgram haxelib;

    private boolean needsSet = false;

    @Override
    public void initialize(Artifact artifact, File outputDirectory, File pluginHome, Set<String> path)
    {
		super.initialize(artifact, outputDirectory.getParentFile(), pluginHome, path);

        //path.add("/bin");
        //path.add("/usr/bin");

        if (needsSet) {
    		try
            {
            	haxelib.execute("set", artifact.getArtifactId(), artifact.getVersion());
            }
            catch (NativeProgramException e)
            {
                System.out.println("Unable to set version for haxelib '"+artifact.getArtifactId()+"'. " + e);
            }
        }
	}

    /*
	@Override
    protected File getUnpackDirectoryForArtifact(Artifact artifact) throws NativeProgramException
    {
    	File munitHaxelibHome = new File(haxelib.getLocalRepositoryPath(), artifact.getArtifactId());
    	if (!munitHaxelibHome.exists()) {
			munitHaxelibHome.mkdirs();
		}

		File currentFile = new File(munitHaxelibHome, ".current");
		if (!currentFile.exists()) {
			try {
                needsSet = true;
				currentFile.createNewFile();
            } catch (IOException e) {
           		throw new NativeProgramException("Unable to create pointer for MUnit haxelib.", e);
            }
		}
        return new File(munitHaxelibHome, artifact.getVersion().replace(".", ","));
    }
    */

    @Override
    protected List<String> updateArguments(List<String> arguments)
    {
        List<String> list = new ArrayList<String>();

        // run MUnit via haxelib
        File executable = new File(haxelib.getInstalledPath(), isWindows() ? "haxelib.exe" : "haxelib");
        list.add(executable.getAbsolutePath());
        list.add("run");
        list.add("munit");
        list.addAll(arguments);

        return list;
    }

    @Override
    protected String[] getEnvironment()
    {
    	String haxeHome = haxe.getInstalledPath();
    	String nekoHome = neko.getInstalledPath();
    	String nmeHome = getInstalledPath();
        String[] env = new String[]{
                "HAXEPATH=" + haxeHome,
                "NEKOPATH=" + nekoHome,
                "DYLD_LIBRARY_PATH=" + nekoHome + ":.",
                "HAXE_LIBRARY_PATH=" + haxeHome + "/std:.",
                "NMEPATH=" + nmeHome,
                "PATH=" + StringUtils.join(path.iterator(), ":"),
                "HOME=" + pluginHome.getAbsolutePath()
        };
        return env;
    }
}