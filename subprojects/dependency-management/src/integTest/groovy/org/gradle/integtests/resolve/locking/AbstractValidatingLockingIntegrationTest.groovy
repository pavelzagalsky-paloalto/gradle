/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.resolve.locking

import org.gradle.integtests.fixtures.FeaturePreviewsFixture
import org.gradle.integtests.fixtures.ToBeFixedForInstantExecution
import spock.lang.Unroll

abstract class AbstractValidatingLockingIntegrationTest extends AbstractLockingIntegrationTest {

    @Unroll
    @ToBeFixedForInstantExecution
    def 'fails when lock file conflicts with declared strict constraint (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf('org:foo') {
        version { strictly '1.1' }
    }
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0'], unique)

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':depLock:unspecified' --> 'org:foo:1.+'
   Dependency path ':depLock:unspecified' --> 'org:foo:{strictly 1.1}'
   Constraint path ':depLock:unspecified' --> 'org:foo:{strictly 1.0}' because of the following reason: dependency was locked to version '1.0'"""

        where:
        unique << [true, false]
    }

    @Unroll
    @ToBeFixedForInstantExecution
    def 'fails when lock file conflicts with declared version constraint (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf('org:foo:1.1')
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0'], unique)

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause """Cannot find a version of 'org:foo' that satisfies the version constraints:
   Dependency path ':depLock:unspecified' --> 'org:foo:1.+'
   Dependency path ':depLock:unspecified' --> 'org:foo:1.1'
   Constraint path ':depLock:unspecified' --> 'org:foo:{strictly 1.0}' because of the following reason: dependency was locked to version '1.0'"""

        where:
        unique << [true, false]
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = "broken file collection")
    def 'fails when lock file contains entry that is not in resolution result (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:bar:1.0', 'org:foo:1.0', 'org:baz:1.0'], unique)

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':lockedConf'.")
        failure.assertHasCause("Did not resolve 'org:bar:1.0' which is part of the dependency lock state")
        failure.assertHasCause("Did not resolve 'org:baz:1.0' which is part of the dependency lock state")

        where:
        unique << [true, false]
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = "broken file collection")
    def 'fails when lock file does not contain entry for module in resolution result (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()
        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    lockedConf 'org:foo:1.+'
    lockedConf 'org:bar:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause("Could not resolve all dependencies for configuration ':lockedConf'.")
        failure.assertHasCause("Resolved 'org:bar:1.0' which is not part of the dependency lock state")

        where:
        unique << [true, false]
    }

    @Unroll
    @ToBeFixedForInstantExecution(because = "broken file collection")
    def 'fails when resolution result is empty and lock file contains entries (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()

        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}
"""
        lockfileFixture.createLockfile('lockedConf', ['org:foo:1.0'], unique)

        when:
        fails 'checkDeps'

        then:
        failure.assertHasCause('Could not resolve all dependencies for configuration \':lockedConf\'.')
        failure.assertHasCause('Did not resolve \'org:foo:1.0\' which is part of the dependency lock state')

        where:
        unique << [true, false]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def 'dependency report passes with failed dependencies using out-of-date lock file (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()

        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    constraints {
        lockedConf('org:foo:1.1')
    }
    lockedConf 'org:foo:1.+'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:foo:1.0'], unique)

        when:
        run 'dependencies'

        then:
        outputContains """lockedConf
+--- org:foo:1.+ FAILED
+--- org:foo:1.1 FAILED
\\--- org:foo:{strictly 1.0} FAILED"""

        where:
        unique << [true, false]
    }

    @ToBeFixedForInstantExecution
    @Unroll
    def 'dependency report passes with FAILED dependencies for all out lock issues (unique: #unique)'() {
        mavenRepo.module('org', 'foo', '1.0').publish()
        mavenRepo.module('org', 'foo', '1.1').publish()
        mavenRepo.module('org', 'bar', '1.0').publish()

        if (unique) {
            FeaturePreviewsFixture.enableOneLockfilePerProject(settingsFile)
        }
        buildFile << """
dependencyLocking {
    lockAllConfigurations()
    lockMode = LockMode.${lockMode()}
}

repositories {
    maven {
        name 'repo'
        url '${mavenRepo.uri}'
    }
}
configurations {
    lockedConf
}

dependencies {
    constraints {
        lockedConf('org:foo:1.1')
    }
    lockedConf 'org:foo:[1.0, 1.1]'
}
"""

        lockfileFixture.createLockfile('lockedConf',['org:bar:1.0', 'org:foo:1.0'], unique)

        when:
        run 'dependencies'

        then:
        outputContains """lockedConf
+--- org:foo:[1.0, 1.1] FAILED
+--- org:foo:1.1 FAILED
+--- org:foo:{strictly 1.0} FAILED
\\--- org:bar:1.0 FAILED
"""

        where:
        unique << [true, false]
    }

}
