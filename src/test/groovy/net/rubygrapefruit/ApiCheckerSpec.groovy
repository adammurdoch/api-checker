package net.rubygrapefruit

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApiCheckerSpec extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "inspects empty distributions"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        def after = new DistroFixture(temporaryFolder.newFolder("after"))

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        0 * listener._
    }

    def "inspects distributions with no classes"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {}
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {}

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        0 * listener._
    }

    def "inspects distributions with unchanged classes"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {
            source("org.gradle.logging.Thing", "package org.gradle.logging; class Thing { }")
        }
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {
            source("org.gradle.logging.Thing", "package org.gradle.logging; class Thing { }")
        }

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        1 * listener.classUnchanged({it.name == "org/gradle/logging/Thing"})
        0 * listener._
    }

    def "inspects distributions with added and removed classes"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {
            source("org.gradle.logging.Thing1", "package org.gradle.logging; class Thing1 { }")
            source("org.gradle.logging.Thing2", "package org.gradle.logging; class Thing2 { }")
        }
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {
            source("org.gradle.logging.Thing1", "package org.gradle.logging; class Thing1 { }")
            source("org.gradle.logging.Thing3", "package org.gradle.logging; class Thing3 { }")
        }

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        1 * listener.classUnchanged({it.name == "org/gradle/logging/Thing1"})
        1 * listener.classAdded({it.name == "org/gradle/logging/Thing3"})
        1 * listener.classRemoved({it.name == "org/gradle/logging/Thing2"})
        0 * listener._
    }

    def "inspects distributions with changed and unchanged classes"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {
            source("org.gradle.logging.Thing1", """
                package org.gradle.logging;
                class Thing1 {
                    void doSomething() { }
                }
            """)
            source("org.gradle.logging.Thing2", "package org.gradle.logging; class Thing2 { }")
        }
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {
            source("org.gradle.logging.Thing1", "package org.gradle.logging; class Thing1 { }")
            source("org.gradle.logging.Thing2", "package org.gradle.logging; class Thing2 { }")
        }

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        1 * listener.methodRemoved({it.name == "org/gradle/logging/Thing1"}, {it.name == "org/gradle/logging/Thing1"}, _)
        1 * listener.classChanged({it.name == "org/gradle/logging/Thing1"}, {it.name == "org/gradle/logging/Thing1"})
        1 * listener.classUnchanged({it.name == "org/gradle/logging/Thing2"})
        0 * listener._
    }

    def "ignores changes to implementation classes"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {
            source("org.gradle.internal.Thing1", """
                package org.gradle.internal;
                class Thing1 {
                    void doSomething() { }
                }
            """)
            source("org.gradle.internal.Thing2", "package org.gradle.internal; class Thing2 { }")
            source("org.gradle.internal.Thing3", "package org.gradle.internal; class Thing3 { }")
        }
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {
            source("org.gradle.internal.Thing1", "package org.gradle.internal; class Thing1 { }")
            source("org.gradle.internal.Thing2", "package org.gradle.internal; class Thing2 { }")
            source("org.gradle.internal.Thing4", "package org.gradle.internal; class Thing4 { }")
        }

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        0 * listener._
    }

    def "reports on change to superclass"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {
            source("org.gradle.logging.Thing1", "package org.gradle.logging; class Thing1 { }")
            source("org.gradle.logging.Thing2", "package org.gradle.logging; class Thing2 extends Thing1 { }")
        }
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {
            source("org.gradle.logging.Thing1", "package org.gradle.logging; class Thing1 extends Thing2 { }")
            source("org.gradle.logging.Thing2", "package org.gradle.logging; class Thing2 { }")
        }

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        1 * listener.superClassChanged({it.name == "org/gradle/logging/Thing1"}, {it.name == "org/gradle/logging/Thing1"})
        1 * listener.superClassChanged({it.name == "org/gradle/logging/Thing2"}, {it.name == "org/gradle/logging/Thing2"})
        1 * listener.classChanged({it.name == "org/gradle/logging/Thing1"}, {it.name == "org/gradle/logging/Thing1"})
        1 * listener.classChanged({it.name == "org/gradle/logging/Thing2"}, {it.name == "org/gradle/logging/Thing2"})
        0 * listener._
    }

    def "reports on change to interfaces"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {
            source("org.gradle.logging.Thing1", "package org.gradle.logging; interface Thing1 { }")
            source("org.gradle.logging.Thing2", "package org.gradle.logging; interface Thing2 { }")
            source("org.gradle.logging.Thing3", "package org.gradle.logging; class Thing3 implements Thing1 { }")
            source("org.gradle.logging.Thing4", "package org.gradle.logging; interface Thing4 extends Thing1 { }")
        }
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {
            source("org.gradle.logging.Thing1", "package org.gradle.logging; interface Thing1 { }")
            source("org.gradle.logging.Thing2", "package org.gradle.logging; interface Thing2 { }")
            source("org.gradle.logging.Thing3", "package org.gradle.logging; class Thing3 implements Thing2 { }")
            source("org.gradle.logging.Thing4", "package org.gradle.logging; interface Thing4 extends Thing1, Thing2 { }")
        }

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        1 * listener.interfaceRemoved({it.name == "org/gradle/logging/Thing3"}, {it.name == "org/gradle/logging/Thing3"}, {it.name == "org/gradle/logging/Thing1"})
        1 * listener.interfaceAdded({it.name == "org/gradle/logging/Thing3"}, {it.name == "org/gradle/logging/Thing3"}, {it.name == "org/gradle/logging/Thing2"})
        1 * listener.interfaceAdded({it.name == "org/gradle/logging/Thing4"}, {it.name == "org/gradle/logging/Thing4"}, {it.name == "org/gradle/logging/Thing2"})
        1 * listener.classChanged({it.name == "org/gradle/logging/Thing3"}, {it.name == "org/gradle/logging/Thing3"})
        1 * listener.classChanged({it.name == "org/gradle/logging/Thing4"}, {it.name == "org/gradle/logging/Thing4"})
        1 * listener.classUnchanged({it.name == "org/gradle/logging/Thing1"})
        1 * listener.classUnchanged({it.name == "org/gradle/logging/Thing2"})
        0 * listener._
    }

    def "reports on change to methods"() {
        def listener = Mock(DiffListener)
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {
            source("org.gradle.logging.Thing1", """
                package org.gradle.logging;
                interface Thing1 {
                    String method1(java.util.List<String> p, boolean b);
                    void method2();
                }
            """)
        }
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {
            source("org.gradle.logging.Thing1", """
                package org.gradle.logging;
                interface Thing1 {
                    String method1(java.util.List<String> p, boolean b);
                    boolean method3(long l);
                }
            """)
        }

        when:
        new ApiChecker(before.installDir, after.installDir, listener).run()

        then:
        1 * listener.methodAdded({it.name == "org/gradle/logging/Thing1"}, {it.name == "org/gradle/logging/Thing1"}, {it.name == "method3"})
        1 * listener.methodRemoved({it.name == "org/gradle/logging/Thing1"}, {it.name == "org/gradle/logging/Thing1"}, {it.name == "method2"})
        1 * listener.classChanged({it.name == "org/gradle/logging/Thing1"}, {it.name == "org/gradle/logging/Thing1"})
        0 * listener._
    }
}
