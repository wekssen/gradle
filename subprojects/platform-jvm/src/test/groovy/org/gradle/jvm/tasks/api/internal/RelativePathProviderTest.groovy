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

package org.gradle.jvm.tasks.api.internal

import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

class RelativePathProviderTest extends Specification {

    def "can resolve between similarly prefixed directories"() {
        given:
        def file1 = createFile("b/c/f1")
        def file2 = createFile("bbb/c/f2")
        def dir1 = file("b")
        def dir2 = file("bbb")
        def subject = RelativePathProvider.from([dir1, dir2])

        expect:
        subject.relativePathOf(file1) == path("c/f1")
        subject.relativePathOf(file2) == path("c/f2")
    }

    def path(String path) {
        path.replace('/', File.separator)
    }

    def createFile(String path) {
        tmpDir.createFile(path)
    }

    def file(String path) {
        tmpDir.file(path)
    }

    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider()
}
