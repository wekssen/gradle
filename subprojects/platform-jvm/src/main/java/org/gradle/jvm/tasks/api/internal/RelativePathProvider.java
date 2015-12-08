/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.jvm.tasks.api.internal;

import org.gradle.api.UncheckedIOException;

import java.io.File;
import java.io.IOException;
import java.util.List;

import static org.gradle.util.GFileUtils.relativePath;

public abstract class RelativePathProvider {

    /**
     * Instantiates the most efficient provider
     * depending on how many directories are given.
     */
    public static RelativePathProvider from(List<File> dirs) {
        return dirs.size() == 1
            ? singleDirProvider(dirs.get(0))
            : multiDirProvider(dirs);
    }

    public abstract String relativePathOf(File file);

    private static RelativePathProvider singleDirProvider(final File dir) {
        return new RelativePathProvider() {
            @Override
            public String relativePathOf(File file) {
                return relativePath(dir, file);
            }
        };
    }

    private static RelativePathProvider multiDirProvider(final List<File> dirs) {
        // Cache canonical path representation for each of the directories
        final String[] canonicalDirs = canonicalDirs(dirs);
        return new RelativePathProvider() {
            @Override
            public String relativePathOf(File file) {
                String path = canonicalPathOf(file);
                for (String canonicalDir : canonicalDirs) {
                    if (path.startsWith(canonicalDir)) {
                        return path.substring(canonicalDir.length());
                    }
                }
                throw new IllegalStateException();
            }
        };
    }

    private static String[] canonicalDirs(List<File> dirs) {
        String[] paths = new String[dirs.size()];
        for (int i = 0; i < paths.length; i++) {
            File dir = dirs.get(i);
            paths[i] = ensureEndsWithSeparator(canonicalPathOf(dir));
        }
        return paths;
    }

    private static String ensureEndsWithSeparator(String path) {
        return path.endsWith(File.separator)
            ? path
            : path + File.separator;
    }

    private static String canonicalPathOf(File file) {
        try {
            return file.getCanonicalPath();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }
}
