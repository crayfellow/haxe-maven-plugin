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
package com.yelbota.plugins.haxe.utils;

import com.yelbota.plugins.haxe.components.nativeProgram.HaxelibNativeProgram;
import com.yelbota.plugins.nd.UnpackHelper;
import com.yelbota.plugins.nd.utils.DefaultUnpackMethods;
import com.yelbota.plugins.haxe.components.nativeProgram.NativeProgramException;

import org.apache.commons.io.FileUtils;
import org.apache.maven.artifact.Artifact;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

import java.io.IOException;
import java.io.File;

public class HaxelibHelper {

    private static HaxelibNativeProgram haxelib;

    public static final File getHaxelibDirectoryForArtifact(String artifactId, String version)
    {
        if (haxelib != null && haxelib.getInitialized()) {
            File haxelibHome = new File(haxelib.getLocalRepositoryPath(), artifactId);
            if (!haxelibHome.exists()) {
                haxelibHome.mkdirs();
            }
            return new File(haxelibHome, getCleanVersionForHaxelibArtifact(version).replace(".", ","));
        } else return null;
    }

    private static String getCleanVersionForHaxelibArtifact(String version)
    {
        return version.replaceAll("-(.*)$", "");
    }

    private static File getHaxelibDirectoryForArtifactAndInitialize(String artifactId, String version)
    {
        File haxelibDirectory = HaxelibHelper.getHaxelibDirectoryForArtifact(artifactId, version);
        if (haxelibDirectory != null) {
            File currentFile = new File(haxelibDirectory.getParentFile(), ".current");
            if (!currentFile.exists()) {
                try {
                    currentFile.createNewFile();
                } catch (IOException e) {
                    System.out.println("Unable to create pointer for '"+artifactId+"' haxelib: " + e);
                    // todo: throw exception!!
                }
            }
        }
        return haxelibDirectory;
    }

    public static int injectPomHaxelib(Artifact artifact, File outputDirectory, Logger logger)
    {
        File unpackDirectory = getHaxelibDirectoryForArtifactAndInitialize(artifact.getArtifactId(), artifact.getVersion());
        if (unpackDirectory.exists()) {
            FileUtils.deleteQuietly(unpackDirectory);
        }

        File tmpDir = new File(outputDirectory, artifact.getArtifactId() + "-unpack");
        if (tmpDir.exists()) {
            FileUtils.deleteQuietly(tmpDir);
        }

        UnpackHelper unpackHelper = new UnpackHelper() {};
        DefaultUnpackMethods unpackMethods = new DefaultUnpackMethods(logger);
        try {
            unpackHelper.unpack(tmpDir, artifact, unpackMethods, null);
        }
        catch (Exception e)
        {
            logger.error(String.format("Can't unpack %s: %s", artifact.getArtifactId(), e));
        }

        for (String firstFileName : tmpDir.list())
        {
            File firstFile = new File(tmpDir, firstFileName);
            firstFile.renameTo(unpackDirectory);
            break;
        }

        if (tmpDir.exists()) {
            FileUtils.deleteQuietly(tmpDir);
        }

        try
        {
            haxelib.execute("set", artifact.getArtifactId(), getCleanVersionForHaxelibArtifact(artifact.getVersion()));
        }
        catch (NativeProgramException e)
        {
            logger.error("Unable to set version for haxelib '"+artifact.getArtifactId()+"'.", e);
            return 1;
        }
        return 0;
    }

    public static void setHaxelib(HaxelibNativeProgram haxelib)
    {
        HaxelibHelper.haxelib = haxelib;
    }
}
