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

package org.gradle.platform.base.internal.registry;

import com.google.common.collect.ImmutableList;
import org.apache.commons.lang.StringUtils;
import org.gradle.api.Action;
import org.gradle.internal.Cast;
import org.gradle.language.base.LanguageSourceSet;
import org.gradle.language.base.internal.LanguageSourceSetFactory;
import org.gradle.language.base.plugins.ComponentModelBasePlugin;
import org.gradle.language.base.sources.BaseLanguageSourceSet;
import org.gradle.model.internal.core.*;
import org.gradle.model.internal.inspect.ExtractedModelAction;
import org.gradle.model.internal.inspect.ExtractedModelRule;
import org.gradle.model.internal.inspect.MethodRuleDefinition;
import org.gradle.model.internal.manage.schema.ModelSchema;
import org.gradle.model.internal.manage.schema.ModelSchemaStore;
import org.gradle.model.internal.type.ModelType;
import org.gradle.platform.base.InvalidModelException;
import org.gradle.platform.base.LanguageType;
import org.gradle.platform.base.LanguageTypeBuilder;
import org.gradle.platform.base.internal.builder.LanguageTypeBuilderInternal;
import org.gradle.platform.base.internal.builder.TypeBuilderFactory;
import org.gradle.platform.base.internal.builder.TypeBuilderInternal;

public class LanguageTypeModelRuleExtractor extends TypeModelRuleExtractor<LanguageType, LanguageSourceSet, BaseLanguageSourceSet> {
    public LanguageTypeModelRuleExtractor(ModelSchemaStore schemaStore) {
        super("language", LanguageSourceSet.class, BaseLanguageSourceSet.class, LanguageTypeBuilder.class, schemaStore, new TypeBuilderFactory<LanguageSourceSet>() {
            @Override
            public TypeBuilderInternal<LanguageSourceSet> create(ModelSchema<? extends LanguageSourceSet> schema) {
                return new DefaultLanguageTypeBuilder(schema);
            }
        });
    }

    @Override
    protected <P extends LanguageSourceSet, I extends BaseLanguageSourceSet> ExtractedModelRule createRegistration(
        final MethodRuleDefinition<?, ?> ruleDefinition,
        final ModelType<P> publicModelType, final ModelType<I> implModelType,
        final TypeBuilderInternal<LanguageSourceSet> builder
    ) {
        final String languageName = ((LanguageTypeBuilderInternal) builder).getLanguageName();
        if (!ModelType.of(LanguageSourceSet.class).equals(publicModelType) && StringUtils.isEmpty(languageName)) {
            throw new InvalidModelException(String.format("Language type '%s' cannot be registered without a language name.", publicModelType));
        }
        ModelAction<LanguageSourceSetFactory> regAction = NoInputsModelAction.of(ModelReference.of(LanguageSourceSetFactory.class), ruleDefinition.getDescriptor(), new Action<LanguageSourceSetFactory>() {
            @Override
            public void execute(LanguageSourceSetFactory sourceSetFactory) {
                ModelType<? extends P> castedImplModelType = Cast.uncheckedCast(implModelType);
                sourceSetFactory.register(languageName, publicModelType, builder.getInternalViews(), castedImplModelType, ruleDefinition.getDescriptor());
            }
        });
        return new ExtractedModelAction(ModelActionRole.Defaults, ImmutableList.<Class<?>>of(ComponentModelBasePlugin.class), regAction);
    }

    private static class DefaultLanguageTypeBuilder extends AbstractTypeBuilder<LanguageSourceSet> implements LanguageTypeBuilderInternal<LanguageSourceSet> {
        private String languageName;

        private DefaultLanguageTypeBuilder(ModelSchema<? extends LanguageSourceSet> schema) {
            super(LanguageType.class, schema);
        }

        @Override
        public void setLanguageName(String languageName) {
            this.languageName = languageName;
        }

        @Override
        public String getLanguageName() {
            return languageName;
        }
    }
}
