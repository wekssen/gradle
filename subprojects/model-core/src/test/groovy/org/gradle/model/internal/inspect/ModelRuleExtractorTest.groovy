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

package org.gradle.model.internal.inspect

import org.gradle.model.*
import org.gradle.model.internal.core.*
import org.gradle.model.internal.core.rule.describe.MethodModelRuleDescriptor
import org.gradle.model.internal.fixture.ProjectRegistrySpec
import org.gradle.model.internal.manage.schema.extract.InvalidManagedModelElementTypeException
import org.gradle.model.internal.manage.schema.extract.ModelStoreTestUtils
import org.gradle.model.internal.registry.DefaultModelRegistry
import org.gradle.model.internal.registry.ModelRegistry
import org.gradle.model.internal.type.ModelType
import org.gradle.test.fixtures.ConcurrentTestUtil
import spock.lang.Unroll

import java.beans.Introspector

class ModelRuleExtractorTest extends ProjectRegistrySpec {
    def extractor = new ModelRuleExtractor(MethodModelRuleExtractors.coreExtractors(SCHEMA_STORE), MANAGED_PROXY_FACTORY, SCHEMA_STORE)
    ModelRegistry registry = new DefaultModelRegistry(extractor)

    static class ModelThing {
        final String name

        ModelThing(String name) {
            this.name = name
        }
    }

    static class EmptyClass extends RuleSource {}

    def "can inspect class with no rules"() {
        expect:
        extract(EmptyClass).empty
    }

    static class ClassWithNonRuleMethods extends RuleSource {
        static List thing() {
            []
        }

        static <T> List<T> genericThing() {
            []
        }

        private doStuff() {}

        private <T> T selectThing(List<T> list) { null }
    }

    def "can have non-rule methods that would be invalid rules"() {
        expect:
        extract(ClassWithNonRuleMethods).empty
    }

    static abstract class AbstractRules extends RuleSource {}

    def "rule class can be abstract"() {
        expect:
        extract(AbstractRules).empty
    }

    def "can create instance of abstract rule class"() {
        expect:
        def schema = extractor.extract(AbstractRules)
        schema.factory.create() instanceof AbstractRules
    }

    static abstract class AbstractPropertyRules extends RuleSource {
        @RuleInput
        abstract String getValue()
        abstract void setValue(String value)
        @RuleInput
        abstract int getNumber()
        abstract void setNumber(int value)
    }

    def "rule class can have abstract getter and setter"() {
        expect:
        extract(AbstractPropertyRules).empty
    }

    def "can create instance of rule class with abstract getter and setter"() {
        when:
        def schema = extractor.extract(AbstractPropertyRules)
        def instance = schema.factory.create()

        then:
        instance instanceof AbstractPropertyRules
        instance.value == null
        instance.number == 0

        when:
        instance.value = "12"
        instance.number = 12

        then:
        instance.value == "12"
        instance.number == 12
    }

    def "can create instance of rule class with abstract property"() {
        expect:
        def schema = extractor.extract(AbstractPropertyRules)
        schema.factory.create() instanceof AbstractPropertyRules
    }

    static abstract class AbstractMethodsRules extends RuleSource {
        @Mutate
        abstract void thing(String s)
    }

    static class NotRuleSource {
    }

    @Managed
    static abstract class ManagedThing {
    }

    def "rule class must extend RuleSource"() {
        when:
        extract(type)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type $type.name is not a valid rule source:
- Rule source classes must directly extend org.gradle.model.RuleSource"""

        where:
        type << [Long, RuleSource, NotRuleSource, ManagedThing]
    }

    def "rule class cannot have abstract rule methods"() {
        when:
        extract(AbstractMethodsRules)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type $AbstractMethodsRules.name is not a valid rule source:
- Method thing(java.lang.String) is not a valid rule method: A rule method cannot be abstract"""
    }

    def "rule class cannot have Groovy meta methods"() {
        when:
        extract(WithGroovyMeta).empty

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type $WithGroovyMeta.name is not a valid rule source:
- Method methodMissing(java.lang.String, java.lang.Object) is not a valid rule method: A method that is not annotated as a rule must be private
- Method propertyMissing(java.lang.String) is not a valid rule method: A method that is not annotated as a rule must be private
- Method propertyMissing(java.lang.String, java.lang.Object) is not a valid rule method: A method that is not annotated as a rule must be private"""
    }

    static class SimpleModelCreationRuleInferredName extends RuleSource {
        @Model
        static ModelThing modelPath() {
            new ModelThing("foo")
        }
    }

    List<ExtractedModelRule> extract(Class<?> source) {
        extractor.extract(source).rules
    }

    void registerRules(Class<?> clazz) {
        def rules = extract(clazz)
        rules.each {
            it.apply(registry, ModelPath.ROOT)
        }
    }

    def "can inspect class with simple model creation rule"() {
        def mockRegistry = Mock(ModelRegistry)

        when:
        def rule = extract(SimpleModelCreationRuleInferredName).first()

        then:
        rule instanceof ExtractedModelRegistration

        when:
        rule.apply(mockRegistry, ModelPath.ROOT)

        then:
        1 * mockRegistry.register(_) >> { ModelRegistration registration ->
            assert registration.path.toString() == "modelPath"
        }
        0 * _
    }

    def "can create instance of rule class"() {
        expect:
        def schema = extractor.extract(SimpleModelCreationRuleInferredName)
        schema.factory.create() instanceof SimpleModelCreationRuleInferredName
    }

    static class ParameterizedModel extends RuleSource {
        @Model
        List<String> strings() {
            Arrays.asList("foo")
        }

        @Model
        List<? super String> superStrings() {
            Arrays.asList("foo")
        }

        @Model
        List<? extends String> extendsStrings() {
            Arrays.asList("foo")
        }

        @Model
        List<?> wildcard() {
            Arrays.asList("foo")
        }
    }

    def "can inspect class with model creation rule for paramaterized type"() {
        when:
        registerRules(ParameterizedModel)

        then:
        registry.realizeNode(ModelPath.path("strings")).promise.canBeViewedAsImmutable(new ModelType<List<String>>() {})
        registry.realizeNode(ModelPath.path("superStrings")).promise.canBeViewedAsImmutable(new ModelType<List<? super String>>() {})
        registry.realizeNode(ModelPath.path("extendsStrings")).promise.canBeViewedAsImmutable(new ModelType<List<? extends String>>() {})
        registry.realizeNode(ModelPath.path("wildcard")).promise.canBeViewedAsImmutable(new ModelType<List<?>>() {})
    }

    static class HasGenericModelRule extends RuleSource {
        @Model
        static <T> List<T> thing() {
            []
        }
    }

    def "model creation rule cannot be generic"() {
        when:
        registerRules(HasGenericModelRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type $HasGenericModelRule.name is not a valid rule source:
- Method thing() is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)"""
    }

    static class HasMultipleRuleAnnotations extends RuleSource {
        @Model
        @Mutate
        static String thing() {
            ""
        }
    }

    def "model rule method cannot be annotated with multiple rule annotations"() {
        when:
        registerRules(HasMultipleRuleAnnotations)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${HasMultipleRuleAnnotations.name} is not a valid rule source:
- Method thing() is not a valid rule method: Can only be one of [annotated with @Model and returning a model element, annotated with @Model and taking a managed model element, annotated with @Defaults, annotated with @Mutate, annotated with @Finalize, annotated with @Validate, annotated with @Rules]"""
    }

    static class ConcreteGenericModelType extends RuleSource {
        @Model
        static List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured"() {
        when:
        registerRules(ConcreteGenericModelType)
        def node = registry.realizeNode(new ModelPath("strings"))
        def type = node.adapter.asImmutable(new ModelType<List<String>>() {}, node, null).type

        then:
        type.parameterized
        type.typeVariables[0] == ModelType.of(String)
    }

    static class ConcreteGenericModelTypeImplementingGenericInterface extends RuleSource implements HasStrings<String> {
        @Model
        List<String> strings() {
            []
        }
    }

    def "type variables of model type are captured when method is generic in interface"() {
        when:
        registerRules(ConcreteGenericModelTypeImplementingGenericInterface)
        def node = registry.realizeNode(new ModelPath("strings"))
        def type = node.adapter.asImmutable(new ModelType<List<String>>() {}, node, null).type

        then:
        type.parameterized
        type.typeVariables[0] == ModelType.of(String)
    }

    static class GenericMutationRule extends RuleSource {
        @Mutate
        <T> void mutate(T thing) {}
    }

    def "mutation rule cannot be generic"() {
        when:
        registerRules(GenericMutationRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${GenericMutationRule.name} is not a valid rule source:
- Method mutate(T) is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)"""
    }

    static class NonVoidMutationRule extends RuleSource {
        @Mutate
        String mutate(String thing) {}
    }

    def "only void is allowed as return type of a mutation rule"() {
        when:
        registerRules(NonVoidMutationRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type $NonVoidMutationRule.name is not a valid rule source:
- Method mutate(java.lang.String) is not a valid rule method: A method annotated with @Mutate must have void return type."""
    }

    static class NoSubjectMutationRule extends RuleSource {
        @Mutate
        void mutate() {}
    }

    def "mutation rule must have a subject"() {
        when:
        registerRules(NoSubjectMutationRule)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type $NoSubjectMutationRule.name is not a valid rule source:
- Method mutate() is not a valid rule method: A method annotated with @Mutate must have at least one parameter"""
    }

    static class RuleWithEmptyInputPath extends RuleSource {
        @Model
        String create(@Path("") String thing) {}
    }

    def "path of rule input cannot be empty"() {
        when:
        registerRules(RuleWithEmptyInputPath)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${RuleWithEmptyInputPath.name} is not a valid rule source:
- Method create(java.lang.String) is not a valid rule method: The declared model element path '' used for parameter 1 is not a valid path: Cannot use an empty string as a model path."""
    }

    static class RuleWithInvalidInputPath extends RuleSource {
        @Model
        String create(@Path("!!!!") String thing) {}
    }

    def "path of rule input has to be valid"() {
        when:
        registerRules(RuleWithInvalidInputPath)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${RuleWithInvalidInputPath.name} is not a valid rule source:
- Method create(java.lang.String) is not a valid rule method: The declared model element path '!!!!' used for parameter 1 is not a valid path: Model element name '!!!!' has illegal first character '!' (names must start with an ASCII letter or underscore)."""
    }

    static class MutationRules extends RuleSource {
        @Mutate
        static void mutate1(List<String> strings) {
            strings << "1"
        }

        @Mutate
        static void mutate2(List<String> strings) {
            strings << "2"
        }

        @Mutate
        static void mutate3(List<Integer> strings) {
            strings << 3
        }
    }

    // Not an exhaustive test of the mechanics of mutation rules, just testing the extraction and registration
    def "mutation rules are registered"() {
        given:
        def path = new ModelPath("strings")
        def type = new ModelType<List<String>>() {}

        // Have to make the inputs exist so the binding can be inferred by type
        // or, the inputs could be annotated with @Path
        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of(path, type), []).descriptor("strings").build())

        when:
        registerRules(MutationRules)

        then:
        def node = registry.realizeNode(path)
        node.adapter.asImmutable(type, node, null).instance.sort() == ["1", "2"]
    }

    static class MutationAndFinalizeRules extends RuleSource {
        @Mutate
        static void mutate3(List<Integer> strings) {
            strings << 3
        }

        @Finalize
        static void finalize1(List<String> strings) {
            strings << "2"
        }

        @Mutate
        static void mutate1(List<String> strings) {
            strings << "1"
        }
    }

    // Not an exhaustive test of the mechanics of finalize rules, just testing the extraction and registration
    def "finalize rules are registered"() {
        given:
        def path = new ModelPath("strings")
        def type = new ModelType<List<String>>() {}

        // Have to make the inputs exist so the binding can be inferred by type
        // or, the inputs could be annotated with @Path
        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of(path, type), []).descriptor("strings").build())

        when:
        registerRules(MutationAndFinalizeRules)

        then:
        def node = registry.realizeNode(path)
        node.adapter.asImmutable(type, node, null).instance == ["1", "2"]
    }

    def "methods are processed ordered by their to string representation"() {
        when:
        def stringListType = new ModelType<List<String>>() {}
        def integerListType = new ModelType<List<Integer>>() {}

        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of(ModelPath.path("strings"), stringListType), []).descriptor("strings").build())
        registry.register(ModelRegistrations.bridgedInstance(ModelReference.of(ModelPath.path("integers"), integerListType), []).descriptor("integers").build())

        then:
        extract(MutationAndFinalizeRules)*.action*.descriptor == [
                MethodModelRuleDescriptor.of(MutationAndFinalizeRules, "finalize1"),
                MethodModelRuleDescriptor.of(MutationAndFinalizeRules, "mutate1"),
                MethodModelRuleDescriptor.of(MutationAndFinalizeRules, "mutate3")
        ]

    }

    static class InvalidModelNameViaAnnotation extends RuleSource {
        @Model(" ")
        String foo() {
            "foo"
        }
    }

    def "invalid model name is not allowed"() {
        when:
        registerRules(InvalidModelNameViaAnnotation)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${InvalidModelNameViaAnnotation.name} is not a valid rule source:
- Method foo() is not a valid rule method: The declared model element path ' ' is not a valid path: Model element name ' ' has illegal first character ' ' (names must start with an ASCII letter or underscore)."""
    }

    static class RuleSourceCreatingAClassAnnotatedWithManaged extends RuleSource {
        @Model
        void bar(ManagedAnnotatedClass foo) {
        }
    }

    def "type of the first argument of void returning model definition has to be a valid managed type"() {
        when:
        registerRules(RuleSourceCreatingAClassAnnotatedWithManaged)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == 'Declaration of model rule ModelRuleExtractorTest.RuleSourceCreatingAClassAnnotatedWithManaged#bar is invalid.'
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == """Type $ManagedAnnotatedClass.name is not a valid model element type:
- Must be defined as an interface or an abstract class."""
    }

    static class RuleSourceWithAVoidReturningNoArgumentMethod extends RuleSource {
        @Model
        void bar() {
        }
    }

    def "void returning model definition has to take at least one argument"() {
        when:
        registerRules(RuleSourceWithAVoidReturningNoArgumentMethod)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == """Type ${RuleSourceWithAVoidReturningNoArgumentMethod.name} is not a valid rule source:
- Method bar() is not a valid rule method: A method annotated with @Model must either take at least one parameter or have a non-void return type"""
    }

    static class RuleSourceCreatingManagedWithNestedPropertyOfInvalidManagedType extends RuleSource {
        @Model
        void bar(ManagedWithNestedPropertyOfInvalidManagedType foo) {
        }
    }

    static class RuleSourceCreatingManagedWithNestedReferenceOfInvalidManagedType extends RuleSource {
        @Model
        void bar(ManagedWithNestedReferenceOfInvalidManagedType foo) {
        }
    }

    @Unroll
    def "void returning model definition with for a type with a nested property of invalid managed type - #inspected.simpleName"() {
        when:
        registerRules(inspected)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule ModelRuleExtractorTest.$inspected.simpleName#bar is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == """Type $invalidTypeName is not a valid model element type:
- Cannot be a parameterized type.

The type was analyzed due to the following dependencies:
${managedType.name}
  \\--- property 'managedWithNestedInvalidManagedType' (${nestedManagedType.name})
    \\--- property 'invalidManaged' ($invalidTypeName)"""

        where:
        inspected                                                        | managedType                                    | nestedManagedType
        RuleSourceCreatingManagedWithNestedPropertyOfInvalidManagedType  | ManagedWithNestedPropertyOfInvalidManagedType  | ManagedWithPropertyOfInvalidManagedType
        RuleSourceCreatingManagedWithNestedReferenceOfInvalidManagedType | ManagedWithNestedReferenceOfInvalidManagedType | ManagedWithReferenceOfInvalidManagedType

        invalidTypeName = "$ParametrizedManaged.name<$String.name>"
    }

    static class RuleSourceCreatingManagedWithNonManageableParent extends RuleSource {
        @Model
        void bar(ManagedWithNonManageableParents foo) {
        }
    }

    def "error message produced when super type is not a manageable type indicates the original (sub) type"() {
        when:
        registerRules(RuleSourceCreatingManagedWithNonManageableParent)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == "Declaration of model rule ModelRuleExtractorTest.RuleSourceCreatingManagedWithNonManageableParent#bar is invalid."
        e.cause instanceof InvalidManagedModelElementTypeException
        e.cause.message == """Type $invalidTypeName is not a valid model element type:
- Cannot be a parameterized type.

The type was analyzed due to the following dependencies:
${ManagedWithNonManageableParents.name}
  \\--- property 'invalidManaged' declared by ${AnotherManagedWithPropertyOfInvalidManagedType.name}, ${ManagedWithPropertyOfInvalidManagedType.name} ($invalidTypeName)"""

        where:
        invalidTypeName = "$ParametrizedManaged.name<$String.name>"
    }

    static class HasRuleWithUncheckedModelMap extends RuleSource {
        @Model
        static ModelThing modelPath(ModelMap foo) {
            new ModelThing("foo")
        }
    }

    def "error when trying to use model map without specifying type param"() {
        when:
        registerRules(HasRuleWithUncheckedModelMap)

        then:
        InvalidModelRuleDeclarationException e = thrown()
        e.message == """Type $HasRuleWithUncheckedModelMap.name is not a valid rule source:
- Method modelPath(org.gradle.model.ModelMap) is not a valid rule method: Raw type org.gradle.model.ModelMap used for parameter 1 (all type parameters must be specified of parameterized type)"""
    }

    static class NotEverythingAnnotated extends RuleSource {
        void mutate(String thing) {}

        private void ok() {}
    }

    def "all non-private methods must be annotated"() {
        when:
        registerRules(NotEverythingAnnotated)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${NotEverythingAnnotated.name} is not a valid rule source:
- Method mutate(java.lang.String) is not a valid rule method: A method that is not annotated as a rule must be private"""
    }

    static class PrivateAnnotated extends RuleSource {
        @Mutate
        private void notOk(String subject) {}
    }

    def "no private methods may be annotated"() {
        when:
        registerRules(PrivateAnnotated)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == """Type ${PrivateAnnotated.name} is not a valid rule source:
- Method notOk(java.lang.String) is not a valid rule method: A rule method cannot be private"""
    }

    static class SeveralProblems {
        private String field1
        private String field2

        @Mutate
        private <T> void notOk() {}

        public void notARule() {}

        @Mutate
        @Validate
        private <T> String multipleProblems(@Path('') List list, @Path(':)') T value) {
            "broken"
        }

        @Model(":)")
        void thing() {
        }
    }

    def "collects all validation problems"() {
        when:
        registerRules(SeveralProblems)

        then:
        def e = thrown(InvalidModelRuleDeclarationException)
        e.message == '''Type org.gradle.model.internal.inspect.ModelRuleExtractorTest$SeveralProblems is not a valid rule source:
- Rule source classes must directly extend org.gradle.model.RuleSource
- Field field1 is not valid: Fields must be static final.
- Field field2 is not valid: Fields must be static final.
- Method multipleProblems(java.util.List, T) is not a valid rule method: Can only be one of [annotated with @Model and returning a model element, annotated with @Model and taking a managed model element, annotated with @Defaults, annotated with @Mutate, annotated with @Finalize, annotated with @Validate, annotated with @Rules]
- Method multipleProblems(java.util.List, T) is not a valid rule method: A rule method cannot be private
- Method multipleProblems(java.util.List, T) is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)
- Method multipleProblems(java.util.List, T) is not a valid rule method: Raw type java.util.List used for parameter 1 (all type parameters must be specified of parameterized type)
- Method multipleProblems(java.util.List, T) is not a valid rule method: The declared model element path '' used for parameter 1 is not a valid path: Cannot use an empty string as a model path.
- Method multipleProblems(java.util.List, T) is not a valid rule method: The declared model element path ':)' used for parameter 2 is not a valid path: Model element name ':)' has illegal first character ':' (names must start with an ASCII letter or underscore).
- Method notOk() is not a valid rule method: A rule method cannot be private
- Method notOk() is not a valid rule method: Cannot have type variables (i.e. cannot be a generic method)
- Method notOk() is not a valid rule method: A method annotated with @Mutate must have at least one parameter
- Method notARule() is not a valid rule method: A method that is not annotated as a rule must be private
- Method thing() is not a valid rule method: The declared model element path ':)' is not a valid path: Model element name ':)' has illegal first character ':' (names must start with an ASCII letter or underscore).
- Method thing() is not a valid rule method: A method annotated with @Model must either take at least one parameter or have a non-void return type'''
    }

    def "extracted rules are cached"() {
        when:
        def fromFirstExtraction = extractor.extract(MutationRules)
        def fromSecondExtraction = extractor.extract(MutationRules)

        then:
        fromFirstExtraction.is(fromSecondExtraction)
    }

    def "cache does not hold strong references"() {
        given:
        def cl = new GroovyClassLoader(getClass().classLoader)
        def source = cl.parseClass('''
            import org.gradle.model.*

            class Rules extends RuleSource {
                @Mutate
                void mutate(String value) {
                }
            }
        ''')

        when:
        extractor.extract(source)

        then:
        extractor.cache.size() == 1

        when:
        cl.clearCache()
        forcefullyClearReferences(source)
        source = null

        then:
        ConcurrentTestUtil.poll(10) {
            System.gc()
            extractor.cache.cleanUp()
            extractor.cache.size() == 0
        }
    }

    private void forcefullyClearReferences(Class<?> clazz) {
        ModelStoreTestUtils.removeClassFromGlobalClassSet(clazz)

        // Remove soft references
        Introspector.flushFromCaches(clazz)
    }
}

