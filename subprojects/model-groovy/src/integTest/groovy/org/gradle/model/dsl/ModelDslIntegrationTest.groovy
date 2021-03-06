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

package org.gradle.model.dsl

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.EnableModelDsl
import org.gradle.model.dsl.internal.transform.RulesVisitor

import static org.hamcrest.Matchers.containsString

/**
 * Tests the fundamental usages of the model dsl.
 *
 * Boundary tests for the transform and specialised cases should go in other dedicated test classes.
 */
class ModelDslIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        EnableModelDsl.enable(executer)
    }

    def "rule inputs can be referenced in closures that are not executed during rule execution"() {
        when:
        buildScript """
            import org.gradle.model.*

            class MyPlugin {
              @RuleSource
              static class Rules {
                @Model
                String foo() {
                  "foo"
                }

                @Model
                List<String> strings() {
                  []
                }
              }
            }

            apply type: MyPlugin

            model {
              tasks {
                create("printStrings") {
                  doLast {
                    // Being in doLast is significant here.
                    // This is not going to execute until much later, so we are testing that we can still access the input
                    println "strings: " + \$("strings")
                  }
                }
              }
              strings {
                add \$("foo")
              }
            }
        """

        then:
        succeeds "printStrings"
        output.contains "strings: " + ["foo"]
    }

    def "inputs are fully configured when used in rules"() {
        when:
        buildScript """
            import org.gradle.model.*

            class MyPlugin {
              @RuleSource
              static class Rules {
                @Model
                List<String> strings() {
                  []
                }
              }
            }

            apply type: MyPlugin

            model {
              tasks {
                create("printStrings") {
                  doLast {
                    println "strings: " + \$("strings")
                  }
                }
              }
              strings {
                add "foo"
              }
              strings {
                add "bar"
              }
            }
        """

        then:
        succeeds "printStrings"
        output.contains "strings: " + ["foo", "bar"]
    }

    def "the same input can be referenced more than once, and refers to the same object"() {
        when:
        buildScript """
            import org.gradle.model.*

            class MyPlugin {
              @RuleSource
              static class Rules {
                @Model
                List<String> strings() {
                  []
                }
              }
            }

            apply type: MyPlugin

            model {
              tasks {
                create("assertDuplicateInputIsSameObject") {
                  doLast {
                    assert \$("strings").is(\$("strings"))
                  }
                }
              }
            }
        """

        then:
        succeeds "assertDuplicateInputIsSameObject"
    }

    def "can use model block in script plugin"() {
        given:
        settingsFile << "include 'a'; include 'b'"
        when:

        buildScript """
            import org.gradle.model.*

            class MyPlugin {
              @RuleSource
              static class Rules {
                @Model
                String foo() {
                  "foo"
                }

                @Model
                List<String> strings() {
                  []
                }
              }
            }

            subprojects {
                apply type: MyPlugin
                apply from: "\$rootDir/script.gradle"
            }
        """
        file("a/build.gradle") << """
            model {
              strings { add "a" }
            }
        """
        file("b/build.gradle") << """
            model {
              strings { add "b" }
            }
        """
        file("script.gradle") << """
            model {
              tasks {
                create("printStrings") {
                  doLast {
                    println project.name + ": " + \$("strings")
                  }
                }
              }
              strings {
                add \$("foo")
              }
            }
        """

        then:
        succeeds "printStrings"
        output.contains "a: " + ["foo", "a"]
        output.contains "b: " + ["foo", "b"]
    }

    def "only closure literals can be used as rules"() {
        when:
        buildScript """
            import org.gradle.model.*

            class MyPlugin {
              @RuleSource
              static class Rules {
                @Model
                String foo() {
                  "foo"
                }
              }
            }

            apply type: MyPlugin

            def c = {};
            model {
                foo(c)
            }
        """

        then:
        fails "tasks"
        failure.assertHasLineNumber 18
        failure.assertHasFileName("Build file '${buildFile}'")
        failure.assertThatCause(containsString(RulesVisitor.ARGUMENT_HAS_TO_BE_CLOSURE_LITERAL_MESSAGE))
    }

}
