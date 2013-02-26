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
import org.sonatype.aether.artifact.Artifact;
import org.codehaus.plexus.component.annotations.Requirement;

import java.io.IOException;
import java.io.File;

public class HaxelibHelper {
    
    private static HaxelibNativeProgram haxelib;

    public static final File getHaxelibDirectoryForArtifact(Artifact artifact)
    {
        return getHaxelibDirectoryForArtifact(artifact.getArtifactId(), artifact.getVersion());
    }
    
    public static final File getHaxelibDirectoryForArtifact(String artifactId, String version)
    {
        if (haxelib != null && haxelib.getInitialized()) {
            File haxelibHome = new File(haxelib.getLocalRepositoryPath(), artifactId);
            if (!haxelibHome.exists()) {
                haxelibHome.mkdirs();
            }
            return new File(haxelibHome, version.replace(".", ","));
        } else return null;
    }

    public static void setHaxelib(HaxelibNativeProgram haxelib)
    {
        HaxelibHelper.haxelib = haxelib;
    }
}
