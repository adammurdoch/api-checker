package net.rubygrapefruit

import org.junit.Rule
import org.junit.rules.TemporaryFolder
import spock.lang.Specification

class ApiCheckerSpec extends Specification {
    @Rule TemporaryFolder temporaryFolder = new TemporaryFolder()

    def "inspects empty distributions"() {
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        def after = new DistroFixture(temporaryFolder.newFolder("after"))

        when:
        new ApiChecker().run([before.installDir.path, after.installDir] as String[])

        then:
        noExceptionThrown()
    }

    def "inspects distributions with no classes"() {
        def before = new DistroFixture(temporaryFolder.newFolder("before"))
        before.lib("gradle-core.jar") {}
        def after = new DistroFixture(temporaryFolder.newFolder("after"))
        after.lib("gradle-core.jar") {}
        after.lib("gradle-logging.jar") {}

        when:
        new ApiChecker().run([before.installDir.path, after.installDir] as String[])

        then:
        noExceptionThrown()
    }

    def "inspects distributions with unchanged classes"() {
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
        new ApiChecker().run([before.installDir.path, after.installDir] as String[])

        then:
        noExceptionThrown()
    }
}
