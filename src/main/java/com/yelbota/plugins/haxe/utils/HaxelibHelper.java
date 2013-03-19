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
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;
import org.sonatype.aether.spi.connector.ArtifactDownload;
import org.sonatype.aether.artifact.Artifact;

import java.io.IOException;
import java.io.File;

public class HaxelibHelper {
    public static final String HAXELIB_URL = "lib.haxe.org";
    public static final String HAXELIB_GROUP_ID = "org.haxe.lib";

    private static HaxelibNativeProgram haxelib;

    public static final File getHaxelibDirectoryForArtifact(String artifactId, String version)
    {
        if (haxelib != null && haxelib.getInitialized()) {
            File haxelibHome = new File(haxelib.getLocalRepositoryPath(), artifactId);
            if (!haxelibHome.exists()) {
                haxelibHome.mkdirs();
            }
            return new File(haxelibHome, getCleanVersionForHaxelibArtifactAsDirectoryName(version));
        } else return null;
    }

    private static String getCleanVersionForHaxelibArtifactAsDirectoryName(String version)
    {
        return getCleanVersionForHaxelibArtifact(version).replace(".", ",");
    }

    public static String getCleanVersionForHaxelibArtifact(String version)
    {
        return version.replaceAll("-(.*)$", "");
    }

    public static File getHaxelibDirectoryForArtifactAndInitialize(String artifactId, String version, Logger logger)
    {
        File haxelibDirectory = HaxelibHelper.getHaxelibDirectoryForArtifact(artifactId, version);
        if (haxelibDirectory != null) {
            File currentFile = new File(haxelibDirectory.getParentFile(), ".current");
            if (!currentFile.exists()) {
                try {
                    currentFile.createNewFile();
                } catch (IOException e) {
                    logger.error("Unable to create pointer for '"+artifactId+"' haxelib: " + e);
                    // todo: throw exception!!
                }
            }
        }
        return haxelibDirectory;
    }

    public static int injectPomHaxelib(ArtifactDownload artifactDownload, Logger logger)
    {
        Artifact artifact = artifactDownload.getArtifact();
        return injectPomHaxelib(artifact.getArtifactId(), artifact.getVersion(), artifact.getExtension(), artifactDownload.getFile(), logger);
    }

    public static int injectPomHaxelib(String artifactId, String artifactVersion, String artifactType, File artifactFile, Logger logger)
    {
        File unpackDirectory = getHaxelibDirectoryForArtifactAndInitialize(artifactId, artifactVersion, logger);

        if (!unpackDirectory.exists()
            || artifactFile.lastModified() > unpackDirectory.lastModified())
        {
            if (unpackDirectory.exists()) {
                FileUtils.deleteQuietly(unpackDirectory);
            }

            File tmpDir = new File(haxelib.getLocalRepositoryPath().getParentFile(), artifactId + "-unpack");
            if (tmpDir.exists()) {
                FileUtils.deleteQuietly(tmpDir);
            }

            UnpackHelper unpackHelper = new UnpackHelper() {};
            DefaultUnpackMethods unpackMethods = new DefaultUnpackMethods(logger);
            try {
                unpackHelper.unpack(tmpDir, artifactType, artifactFile, unpackMethods, null);
            }
            catch (Exception e)
            {
                logger.error(String.format("Can't unpack %s: %s", artifactId, e));
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
                haxelib.execute("set", artifactId, getCleanVersionForHaxelibArtifact(artifactVersion));
            }
            catch (NativeProgramException e)
            {
                logger.error("Unable to set version for haxelib '"+artifactId+"'.", e);
                return 1;
            }
        }
        return 0;
    }

    public static void setHaxelib(HaxelibNativeProgram haxelib)
    {
        HaxelibHelper.haxelib = haxelib;
    }
}
