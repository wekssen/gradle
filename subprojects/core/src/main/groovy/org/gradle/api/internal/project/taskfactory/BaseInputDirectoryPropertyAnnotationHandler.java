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

package org.gradle.api.internal.project.taskfactory;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.tasks.SkipWhenEmpty;

import java.io.File;
import java.lang.annotation.Annotation;
import java.util.Collection;
import java.util.concurrent.Callable;

public abstract class BaseInputDirectoryPropertyAnnotationHandler implements PropertyAnnotationHandler {

    public abstract Class<? extends Annotation> getAnnotationType();

    protected abstract ValidationAction getValidationAction();

    protected static void validateInputDirectory(File dir, String propertyName, Collection<String> messages) {
        if (!dir.exists()) {
            messages.add(String.format("Directory '%s' specified for property '%s' does not exist.", dir, propertyName));
        } else if (!dir.isDirectory()) {
            messages.add(String.format("Directory '%s' specified for property '%s' is not a directory.", dir, propertyName));
        }
    }

    public void attachActions(PropertyActionContext context) {
        context.setValidationAction(getValidationAction());
        final boolean isSourceDir = context.getTarget().getAnnotation(SkipWhenEmpty.class) != null;
        context.setConfigureAction(new UpdateAction() {
            public void update(TaskInternal task, Callable<Object> futureValue) {
                if (isSourceDir) {
                    task.getInputs().sourceDir(futureValue);
                } else {
                    task.getInputs().dir(futureValue);
                }
            }
        });
    }
}
