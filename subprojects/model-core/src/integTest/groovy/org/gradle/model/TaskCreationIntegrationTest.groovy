/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.model

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class TaskCreationIntegrationTest extends AbstractIntegrationSpec {

    def "can create tasks from model"() {
        given:
        buildScript """
            import org.gradle.model.*
            import org.gradle.model.collection.*

            class MyModel {
                List<String> tasks = []
            }

            class MyPlugin implements Plugin<Project> {
                void apply(Project project) {}

                @RuleSource
                static class Rules {
                    @Model
                    MyModel myModel() {
                        new MyModel()
                    }

                    @Mutate
                    void addTasks(NamedItemCollectionBuilder<Task> tasks, MyModel myModel) {
                        myModel.tasks.each { n ->
                            tasks.create(n) {
                              it.description = "task \$n"
                            }
                        }
                    }
                }
            }

            apply plugin: MyPlugin

            model {
                myModel {
                    tasks << "a" << "b"
                }
            }
        """

        when:
        succeeds "tasks"

        then:
        output.contains "a - task a"
        output.contains "b - task b"
    }

}
