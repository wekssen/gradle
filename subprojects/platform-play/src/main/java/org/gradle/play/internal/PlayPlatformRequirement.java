/*
 * Copyright 2014 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.play.internal;

import org.gradle.platform.base.internal.PlatformRequirement;

public class PlayPlatformRequirement implements PlatformRequirement {
    private final String platformName;
    private final String playVersion;
    private final String scalaVersion;
    private final String javaVersion;

    public PlayPlatformRequirement(String playVersion, String scalaVersion, String javaVersion) {
        this.playVersion = playVersion;
        this.scalaVersion = scalaVersion;
        this.javaVersion = javaVersion;
        platformName = createName(playVersion, scalaVersion, javaVersion);
    }

    @Override
    public String getPlatformName() {
        return platformName;
    }

    String getPlayVersion() {
        return playVersion;
    }

    String getScalaVersion() {
        return scalaVersion;
    }

    String getJavaVersion() {
        return javaVersion;
    }

    private String createName(String playVersion, String scalaVersion, String javaVersion) {
        StringBuilder builder = new StringBuilder("play-");
        builder.append(playVersion);
        if (scalaVersion != null) {
            builder.append("-");
            builder.append(scalaVersion);
        }
        if (javaVersion != null) {
            builder.append("_");
            builder.append(javaVersion);
        }
        return builder.toString();
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null || getClass() != obj.getClass()) {
            return false;
        }

        PlayPlatformRequirement that = (PlayPlatformRequirement) obj;

        if (playVersion != null ? !playVersion.equals(that.playVersion) : that.playVersion != null) {
            return false;
        } else if (scalaVersion != null ? !scalaVersion.equals(that.scalaVersion) : that.scalaVersion != null) {
            return false;
        }
        return javaVersion != null ? javaVersion.equals(that.javaVersion) : that.javaVersion == null;

    }

    @Override
    public int hashCode() {
        int result = playVersion != null ? playVersion.hashCode() : 0;
        result = 31 * result + (scalaVersion != null ? scalaVersion.hashCode() : 0);
        result = 31 * result + (javaVersion != null ? javaVersion.hashCode() : 0);
        return result;
    }
}
